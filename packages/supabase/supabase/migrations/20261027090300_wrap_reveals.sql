-- Migration: wrap_reveals, reveal_wrap, path_month_cover, home_wraps (#1254, spec 2026-10-26 §4.3, D5)
-- A month's wrap stays folded on the Home tab until the user unfolds it. The
-- state is per account (iOS and Android must agree), so it is a row here and
-- not a device preference. Writes go only through reveal_wrap.

-- 1. the table ------------------------------------------------------------------------
create table public.wrap_reveals (
  user_id     uuid not null references auth.users(id) on delete cascade,
  month       date not null check (month = date_trunc('month', month)::date),
  revealed_at timestamptz not null default now(),
  primary key (user_id, month)
);

comment on table public.wrap_reveals is
  'Which monthly wraps the user has unfolded on the Home tab (#1254). One row per account and month; written only by reveal_wrap().';

alter table public.wrap_reveals enable row level security;

create policy wrap_reveals_select_self on public.wrap_reveals
  for select to authenticated using (user_id = auth.uid());
-- No INSERT/UPDATE/DELETE policies: written exclusively by reveal_wrap().

-- 2. reveal_wrap --------------------------------------------------------------------------
-- Upserts for the caller. A month that is not finished in p_tz is ignored (the
-- row is not written) and the call still returns the current state, so a
-- client cannot unfold the live month early. invalid_tz / invalid_month as in
-- path_month_wrap.
create or replace function public.reveal_wrap(p_month date, p_tz text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  today date;
  v_at timestamptz;
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  begin
    today := (now() at time zone p_tz)::date;
  exception when others then
    raise exception 'invalid_tz' using errcode = '22023';
  end;
  if p_month is null or extract(day from p_month) <> 1 then
    raise exception 'invalid_month' using errcode = '22023';
  end if;
  if p_month < date_trunc('month', today)::date then
    insert into public.wrap_reveals (user_id, month) values (v_uid, p_month)
    on conflict (user_id, month) do nothing;
  end if;
  select revealed_at into v_at from public.wrap_reveals where user_id = v_uid and month = p_month;
  return jsonb_build_object('month', p_month, 'revealed_at',
    case when v_at is null then null else to_jsonb(to_char(v_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')) end);
end;
$$;

revoke all on function public.reveal_wrap(date, text) from public, anon;
grant execute on function public.reveal_wrap(date, text) to authenticated;

comment on function public.reveal_wrap(date, text) is
  'Unfolds a finished month''s wrap for the caller (#1254). Idempotent; the live month is ignored and the current state returned. Whole-second UTC revealed_at.';

-- 3. path_month_cover ---------------------------------------------------------------------
-- The cover CTE of path_month_wrap (20261023230000, the `mo`, `souls`, `fams`
-- and `doms` CTEs feeding j_cover), lifted into its own function so the Home
-- cards do not run the whole month page per month. The body is a transcription
-- of those CTEs and the jsonb_build_object for 'cover'; verify-home.ts asserts
-- per month that it equals path_month_wrap(...)->'cover', so drift fails CI.
-- path_month_wrap adopts this function at its next re-emission (not here: the
-- re-emission rule).
create or replace function public.path_month_cover(p_month date, p_tz text)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  wheel constant text[] := array['joy', 'pride', 'anger', 'fear', 'shame', 'sadness', 'peace'];
  uid uuid := (select auth.uid());
  from_ts timestamptz;
  to_ts timestamptz;
  result jsonb;
begin
  if p_month is null or extract(day from p_month) <> 1 then
    raise exception 'invalid_month' using errcode = '22023';
  end if;
  begin
    from_ts := p_month::timestamp at time zone p_tz;
  exception when others then
    raise exception 'invalid_tz' using errcode = '22023';
  end;
  to_ts := (p_month + interval '1 month')::timestamp at time zone p_tz;

  with mo as (
    select p.id as pebble_id, m.id as moment_id, m.happened_at,
           ((m.happened_at at time zone p_tz)::date - p_month) as dom,
           p.intensity, p.positiveness, p.emotion_id, ec.slug as family,
           p.intensity * case when p.positiveness = 0 then 0.5 else 1 end as worth
    from public.moments m
    join public.pebbles p on p.moment_id = m.id and p.user_id = uid
    left join public.emotions e on e.id = p.emotion_id
    left join public.emotion_categories ec on ec.id = e.category_id
    where m.user_id = uid and m.happened_at >= from_ts and m.happened_at < to_ts
  ),
  souls as (
    select ps.soul_id, count(*) as n, min(x.happened_at) as first_at
    from mo x join public.pebble_souls ps on ps.pebble_id = x.pebble_id
    group by ps.soul_id
  ),
  fams as (
    select family, count(*) as n, sum(worth) as worth
    from mo where family is not null
    group by family
  ),
  doms as (
    select pd.domain_id, count(*) as n, min(x.happened_at) as first_at
    from mo x join public.pebble_domains pd on pd.pebble_id = x.pebble_id
    group by pd.domain_id
  )
  select jsonb_build_object(
    'days', (select coalesce(jsonb_agg(jsonb_build_object('day', dom, 'pebbles', pebbles) order by dom), '[]'::jsonb)
               from (select dom, jsonb_agg(jsonb_build_object('moment_id', moment_id, 'emotion_id', emotion_id,
                                                              'intensity', intensity, 'polarity', positiveness)
                                           order by happened_at, moment_id) as pebbles
                     from mo group by dom) per_day),
    'souls_in_order', (select coalesce(jsonb_agg(soul_id order by first_at, soul_id), '[]'::jsonb) from souls),
    'inks', (select coalesce(jsonb_agg(family order by worth desc, n desc, array_position(wheel, family)), '[]'::jsonb) from fams),
    'papers', (select coalesce(jsonb_agg(domain_id order by n desc, first_at, domain_id), '[]'::jsonb)
                 from (select * from doms order by n desc, first_at, domain_id limit 3) top))
  into result;
  return result;
end;
$$;

revoke all on function public.path_month_cover(date, text) from public, anon;
grant execute on function public.path_month_cover(date, text) to authenticated;

comment on function public.path_month_cover(date, text) is
  'The month cover alone (#1254): the days with their pebbles, souls in order, inks and papers, equal to path_month_wrap''s cover for the same month.';

-- 4. home_wraps ---------------------------------------------------------------------------
-- The finished months (in p_tz) holding at least one pebble, newest first,
-- twelve at most, each with its pebble count, its revealed_at (null = folded)
-- and the cover from path_month_cover. security invoker like its siblings.
create or replace function public.home_wraps(p_tz text)
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_uid uuid := (select auth.uid());
  today date;
  result jsonb;
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  begin
    today := (now() at time zone p_tz)::date;
  exception when others then
    raise exception 'invalid_tz' using errcode = '22023';
  end;

  with months as (
    select date_trunc('month', (m.happened_at at time zone p_tz)::date)::date as month, count(*)::int as n
    from public.moments m
    join public.pebbles p on p.moment_id = m.id and p.user_id = v_uid
    where m.user_id = v_uid
    group by 1
    having date_trunc('month', (m.happened_at at time zone p_tz)::date)::date < date_trunc('month', today)::date
    order by 1 desc
    limit 12
  )
  select jsonb_build_object('months', coalesce(jsonb_agg(jsonb_build_object(
           'month', mo.month,
           'count', mo.n,
           'revealed_at', case when r.revealed_at is null then null
                               else to_jsonb(to_char(r.revealed_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')) end,
           'cover', public.path_month_cover(mo.month, p_tz))
         order by mo.month desc), '[]'::jsonb))
  into result
  from months mo
  left join public.wrap_reveals r on r.user_id = v_uid and r.month = mo.month;
  return result;
end;
$$;

revoke all on function public.home_wraps(text) from public, anon;
grant execute on function public.home_wraps(text) to authenticated;

comment on function public.home_wraps(text) is
  'The Home tab''s month cards (#1254): finished months with a pebble, newest first (12 at most), each with count, revealed_at (null = folded) and the cover (path_month_cover, equal to path_month_wrap''s).';

-- 5. purge_account: wrap_reveals ------------------------------------------------------------
-- Body verbatim from 20261012090300 with exactly ONE edit, checked by pairwise
-- diff: the wrap_reveals delete appended in section (4) right after the
-- achievement_unlocks block. The table cascades from auth.users, so the
-- explicit delete is belt-and-braces like its section-(4) siblings; it keeps
-- "all personal rows gone" true at RPC success and the counts honest.
-- Re-emitting this function is how appends get lost: `create or replace` has
-- no merge semantics and git reports no conflict, so anything not carried
-- forward is silently dropped. Grants are unchanged (service-role only, set
-- in 20260729201326); create or replace preserves them. The harness
-- (verify-account-purge.ts) gains the wrap_reveals seed and zero-row
-- assertion in the same part.
create or replace function public.purge_account(p_user_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_kept   uuid[];
  v_counts jsonb := '{}'::jsonb;
  v_n      bigint;
begin
  if p_user_id is null then
    raise exception 'missing_user_id' using errcode = '22004';
  end if;

  -- -------------------------------------------------------------------------
  -- (1) Marketplace rows the user OWNS. Own entitlements first:
  -- glyph_entitlements.karma_event_id references the buyer's own purchase
  -- karma_event with no cascade, so these must precede the karma_events
  -- delete in (3). Counterparties' entitlements reference their OWN events
  -- (buy_glyph links the entitlement to the buyer's spend) and are untouched.
  -- -------------------------------------------------------------------------
  delete from public.glyph_entitlements where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('glyph_entitlements', v_n);

  delete from public.glyph_favourites where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('glyph_favourites', v_n);

  -- -------------------------------------------------------------------------
  -- (2) Glyphs: anonymize the externally-referenced ones, delete the rest.
  -- Externally referenced = another user's entitlement (sold) ∪ another
  -- user's soul (RESTRICT FK) ∪ another user's collection (NO ACTION FK;
  -- a pebble's glyph is derived from its collections since 20261012090000)
  -- ∪ another user's profile glyph (SET NULL, kept for their UX) ∪ a domain
  -- default (NO ACTION FK).
  -- -------------------------------------------------------------------------
  select coalesce(array_agg(g.id), '{}') into v_kept
  from public.glyphs g
  where g.user_id = p_user_id
    and (
         exists (select 1 from public.glyph_entitlements e
                 where e.glyph_id = g.id and e.user_id <> p_user_id)
      or exists (select 1 from public.souls s
                 where s.glyph_id = g.id and s.user_id <> p_user_id)
      or exists (select 1 from public.collections c
                 where c.glyph_id = g.id and c.user_id <> p_user_id)
      or exists (select 1 from public.profiles pr
                 where pr.glyph_id = g.id and pr.user_id <> p_user_id)
      or exists (select 1 from public.domains d
                 where d.default_glyph_id = g.id)
    );

  update public.glyphs set user_id = null where id = any(v_kept);
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('glyphs_anonymized', v_n);

  -- Kept glyphs leave the market but keep the approved audit trail. Must run
  -- BEFORE the submitter detach below (this filters on submitter_id).
  update public.glyph_submissions
     set listed = false
   where glyph_id = any(v_kept) and submitter_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('glyph_submissions_delisted', v_n);

  -- Detach the user from every surviving submission: as submitter (kept
  -- glyphs above, plus glyphs admin_attribute_glyph moved to a new owner —
  -- those stay listed for that owner) and as reviewer (admin self-deletion).
  update public.glyph_submissions set submitter_id = null
   where submitter_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('glyph_submissions_detached', v_n);

  update public.glyph_submissions set reviewed_by = null
   where reviewed_by = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('glyph_submissions_unreviewed', v_n);

  -- -------------------------------------------------------------------------
  -- (3) Content, FK-ordered. Moments first: deleting a moment cascades its
  -- pebbles (pebbles.moment_id CASCADE) and snaps (snaps.moment_id CASCADE),
  -- and each pebble cascades pebble_cards / pebble_domains / pebble_souls /
  -- collection_pebbles. The pebbles delete then takes this user's pebbles on
  -- OTHER users' moments (participants, later); the snaps delete is
  -- belt-and-braces. So counts.moments is the user's moments, while
  -- counts.pebbles / counts.snaps count only rows NOT cascaded by that delete
  -- and read 0 until participants ship; the harness pins moments = <seeded>
  -- and pebbles = snaps = 0, and proves deletion with the zero-row table
  -- checks. Souls before glyphs (souls.glyph_id RESTRICT); collections before
  -- glyphs (collections.glyph_id NO ACTION).
  -- -------------------------------------------------------------------------
  delete from public.moments where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('moments', v_n);

  delete from public.pebbles where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('pebbles', v_n);

  delete from public.snaps where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('snaps', v_n);

  delete from public.souls where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('souls', v_n);

  delete from public.collections where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('collections', v_n);

  delete from public.profiles where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('profiles', v_n);

  -- Anonymized glyphs now have user_id = null, so delete-by-owner removes
  -- exactly the non-kept remainder. Cascades take their submissions and any
  -- other users' favourites on them. No entitlement can reference a deleted
  -- glyph: any glyph with a foreign entitlement is in v_kept by construction.
  delete from public.glyphs where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('glyphs_deleted', v_n);

  -- Ledger: nothing references these rows any more — own entitlements went in
  -- (1); other users' entitlements point at their own events. The wallet and
  -- bounce triggers are AFTER INSERT only (20260629193636, 20260501000004),
  -- so this fires nothing.
  delete from public.karma_events where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('karma_events', v_n);

  -- -------------------------------------------------------------------------
  -- (4) auth.users-CASCADE rows. deleteUser would reap these, but deleting
  -- them here makes "all personal rows gone" true at RPC success and keeps
  -- re-runs meaningful when deleteUser is the step that failed.
  -- >>> APPEND new per-user tables from later milestones HERE. <<<
  -- -------------------------------------------------------------------------

  -- Detach, don't delete (#831): the moderation trail outlives the reporter.
  -- reporter_id carries `on delete set null`, so deleteUser would eventually
  -- do this too — the explicit statement exists for this section's stated
  -- reason: it makes "all personal rows gone" true at RPC success, and keeps a
  -- re-run meaningful when deleteUser was the step that failed.
  update public.content_reports set reporter_id = null where reporter_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('content_reports_detached', v_n);

  -- Reports AGAINST the departing user go: their content is gone with the
  -- account, so an open report about it is unresolvable. Must follow the
  -- detach above.
  delete from public.content_reports where target_user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('content_reports', v_n);

  -- The Art. 9 consent ledger (#775). Erasure wins over the Art. 7(1) duty to
  -- demonstrate consent: once there is no data subject, there is nothing left
  -- to demonstrate about. Controller decision, 2026-09-11, recorded as Q5 in
  -- docs/compliance/dpia.md.
  --
  -- Unlike its neighbours in this section, user_consents has NO on-delete
  -- cascade (it matches profiles, so the purge stays explicit). That makes this
  -- delete load-bearing rather than belt-and-braces: without it,
  -- auth.admin.deleteUser fails with 23503 and the account is left half-purged.
  delete from public.user_consents where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('user_consents', v_n);
  -- connections and connection_blocks are TWO-SIDED — deliberately NOT the
  -- usual user_id = p_user_id shape. Do not "simplify" them to a single
  -- column: that would silently halve the purge (D10). Deleting A removes the
  -- shared connection row (B just stops seeing it) and blocks in BOTH
  -- directions involving A (a block against a nonexistent user is
  -- meaningless).
  delete from public.connections where p_user_id in (user_a, user_b);
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('connections', v_n);

  delete from public.connection_invites where inviter_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('connection_invites', v_n);

  delete from public.connection_blocks where p_user_id in (blocker_id, blocked_id);
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('connection_blocks', v_n);

  delete from public.achievement_unlocks where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('achievement_unlocks', v_n);

  -- Unfolded monthly wraps (#1254). Cascades from auth.users; explicit for the
  -- same reason as its neighbours.
  delete from public.wrap_reveals where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('wrap_reveals', v_n);

  delete from public.log_reactions where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('log_reactions', v_n);

  delete from public.wallet_balances where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('wallet_balances', v_n);

  delete from public.bounces where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('bounces', v_n);

  return jsonb_build_object(
    'user_id', p_user_id,
    'kept_glyphs', coalesce(array_length(v_kept, 1), 0),
    'counts', v_counts
  );
end;
$$;
