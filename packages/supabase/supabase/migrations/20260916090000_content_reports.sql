-- =============================================================================
-- content_reports (#831, M56) — the UGC report primitive: one table, an
-- authenticated insert RPC, and an is_admin-gated queue whose resolve takes
-- the takedown action in the same transaction as the verdict.
-- =============================================================================
-- Apple 1.2 and Play's UGC policy require an in-product report mechanism wired
-- to an operator queue. The database is the contract for all four clients, so
-- no client can ship a report button until this exists (Kritik
-- F-2026-08-PLT-supabase-01). purge_account has named `reports` in its
-- future-tables list since 20260729201326; the extension lands in
-- 20260916090100, its own file, per the re-emission rule.
--
-- Design: docs/superpowers/specs/2026-09-16-content-reports-design.md
--
-- STANDING-RULE CHECK: no emotion or domain inserts here, so
-- sync_achievement_catalog() is deliberately NOT re-run.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. The table. `target_id` is polymorphic — a pebble id, a profile user_id,
-- or a glyph id depending on target_kind — so it carries NO foreign key: three
-- kinds cannot share one. The cost is accepted and handled: nothing cascades
-- when the target row is deleted, and admin_list_content_reports flags those
-- orphans as target_missing for an admin to dismiss.
--
-- target_user_id is denormalised rather than derived, resolved server-side in
-- report_content (a definer function, so it reads past RLS). It is what lets
-- purge_account delete reports against a departing user in one statement, and
-- what lets the queue group by offender without joining three tables.
-- ---------------------------------------------------------------------------
create table public.content_reports (
  id              uuid primary key default gen_random_uuid(),
  reporter_id     uuid references auth.users(id) on delete set null,
  target_kind     text not null check (target_kind in ('pebble', 'profile', 'glyph')),
  target_id       uuid not null,
  target_user_id  uuid not null references auth.users(id) on delete cascade,
  -- A closed slug set, localised by the clients. A reference table would need
  -- seeding, an admin CRUD surface and a drift rule for nine values that change
  -- roughly never; the CHECK buys the same unrepresentability for nothing.
  reason          text not null check (reason in (
                    'sexual', 'violence', 'hate', 'harassment',
                    'self_harm', 'illegal', 'spam', 'impersonation', 'other')),
  -- Free text from a reporter, shown to an operator. Uncapped, it is a place
  -- to paste a novel.
  detail          text check (detail is null or length(detail) <= 1000),
  -- The reported content as it read AT FILE TIME. Without it, an owner who
  -- edits their pebble after being reported presents innocent content to the
  -- reviewer — a one-line evasion of the whole pillar. It also keeps an
  -- orphaned report meaningful after the target is gone. Only ever holds the
  -- TARGET's text, never the reporter's, so purge-by-target_user_id erases it.
  target_snapshot jsonb,
  status          text not null default 'open'
                    check (status in ('open', 'actioned', 'dismissed')),
  resolution_note text,
  resolved_at     timestamptz,
  resolved_by     uuid references auth.users(id) on delete set null,
  created_at      timestamptz not null default now()
);

-- One OPEN report per (reporter, target): report_content infers this index to
-- return the standing report instead of erroring. Partial, so a resolved
-- report stops blocking — a reoffending target can be reported again.
create unique index content_reports_open_unique
  on public.content_reports (reporter_id, target_kind, target_id)
  where status = 'open';

-- The queue read is always oldest-first over open rows (FIFO review).
create index content_reports_open_queue
  on public.content_reports (created_at)
  where status = 'open';

-- Covers both purge_account statements and the queue's per-target count.
create index content_reports_target_user on public.content_reports (target_user_id);
create index content_reports_target on public.content_reports (target_kind, target_id);

-- ---------------------------------------------------------------------------
-- 2. RLS: enabled, with NO policies. Not even a select.
--
-- Pattern: wallet_balances (20260629193636) and connection_blocks
-- (20260730070347) — "no INSERT/UPDATE/DELETE policies", the definer RPCs are
-- the only write path. Deliberately NOT the pebble_drafts `for all` shape,
-- which is for sanctioned direct-client single-table CRUD.
--
-- The select is absent too, and that is the load-bearing part: a reporter
-- never reads their report back. The report is fire-and-forget — the client
-- shows a confirmation and moves on. A readable content_reports is a map of
-- who reported whom, and there is no product reason to expose it.
--
-- Defense in depth (pattern: pebbles, v_pebbles_full): anon holds the default
-- public-schema grant but has no legitimate read or write here.
-- ---------------------------------------------------------------------------
alter table public.content_reports enable row level security;

revoke all on public.content_reports from anon;

-- ---------------------------------------------------------------------------
-- 3. report_content — the insert path. `authenticated` only.
--
-- Anonymous reporting is deliberately unsupported: an anon insert is an
-- unauthenticated write to a table any bot can find, and the schema has no
-- rate-limit primitive to lean on. Apple 1.2 asks for a report mechanism for
-- the app's users; the anon share page routes its affordance to sign-in or to
-- the published moderation contact. Design §4 records this as a knowing gap.
--
-- Error slugs are a wire contract — clients substring-match them (pattern:
-- 20260730070347): not_authenticated (42501), invalid_kind, invalid_reason,
-- not_found, cannot_report_own.
-- ---------------------------------------------------------------------------
create function public.report_content(
  p_target_kind text,
  p_target_id   uuid,
  p_reason      text,
  p_detail      text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user     uuid := auth.uid();
  v_owner    uuid;
  v_snapshot jsonb;
  v_row      public.content_reports;
begin
  if v_user is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;

  if p_target_kind not in ('pebble', 'profile', 'glyph') then
    raise exception 'invalid_kind';
  end if;

  if p_reason not in ('sexual', 'violence', 'hate', 'harassment',
                      'self_harm', 'illegal', 'spam', 'impersonation', 'other') then
    raise exception 'invalid_reason';
  end if;

  -- The visibility gate, mirroring each kind's live read rule. A miss leaves
  -- v_owner null and falls through to the single not_found below.
  if p_target_kind = 'pebble' then
    -- Mirrors pebbles_select (20260817130000 §3) minus the owner arm: owners
    -- are caught by cannot_report_own, not by the gate. The connections pair
    -- is canonical (user_a < user_b), so least/greatest lands on the unique
    -- (user_a, user_b) index.
    select p.user_id,
           jsonb_build_object('name', p.name, 'description', p.description)
      into v_owner, v_snapshot
      from public.pebbles p
     where p.id = p_target_id
       and (
            p.visibility = 'public'
         or (p.visibility = 'private' and exists (
               select 1
                 from public.connections c
                where c.user_a = least(v_user, p.user_id)
                  and c.user_b = greatest(v_user, p.user_id)))
       );

  elsif p_target_kind = 'profile' then
    -- target_id is the profile's user_id: the public profile IS the user.
    select pr.user_id,
           jsonb_build_object('handle', pr.handle, 'display_name', pr.display_name)
      into v_owner, v_snapshot
      from public.profiles pr
     where pr.user_id = p_target_id
       and pr.public_profile = true;

  else
    select g.user_id,
           jsonb_build_object('name', g.name)
      into v_owner, v_snapshot
      from public.glyphs g
     where g.id = p_target_id
       and exists (
             select 1
               from public.glyph_submissions s
              where s.glyph_id = g.id
                and s.status = 'approved'
                and s.listed = true);
  end if;

  -- One slug for three cases: no such row, a row the reporter cannot see, and
  -- an anonymised glyph (purge_account nulls user_id but delists in the same
  -- pass, so it fails the listed gate above before the not-null column is
  -- needed). Distinguishing them would make this an existence oracle — feed it
  -- uuids, learn which secret pebbles exist. Same choice get_shared_pebble
  -- makes by returning null for both (20260817130000 §4).
  if v_owner is null then
    raise exception 'not_found';
  end if;

  if v_owner = v_user then
    raise exception 'cannot_report_own';
  end if;

  -- Idempotent on content_reports_open_unique. A second file is a success from
  -- the user's point of view — they reported it — so it returns the standing
  -- report rather than an error slug every client would have to special-case.
  insert into public.content_reports
    (reporter_id, target_kind, target_id, target_user_id, reason, detail, target_snapshot)
  values
    (v_user, p_target_kind, p_target_id, v_owner, p_reason,
     nullif(btrim(p_detail), ''), v_snapshot)
  on conflict (reporter_id, target_kind, target_id) where status = 'open'
  do nothing
  returning * into v_row;

  if v_row.id is null then
    select * into v_row
      from public.content_reports
     where reporter_id = v_user
       and target_kind = p_target_kind
       and target_id   = p_target_id
       and status      = 'open';
  end if;

  -- Projected keys only. target_user_id is deliberately excluded (standing
  -- rule: no cross-user identifier ever leaves an RPC), and so is the
  -- snapshot. created_at is whole-second UTC (standing timestamp rule).
  return jsonb_build_object(
    'id', v_row.id,
    'status', v_row.status,
    'created_at', to_char(v_row.created_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- 4. admin_list_content_reports — the queue read. Pattern:
-- admin_list_glyph_submissions (20260630084718), oldest-first.
--
-- The reporter's email is deliberately NOT joined. admin_list_glyph_submissions
-- joins auth.users for the submitter email and #766 is open against exactly
-- that — materialising third-party emails into an admin list. This does not
-- repeat it; the reporter id is enough to act on.
-- ---------------------------------------------------------------------------
create function public.admin_list_content_reports(p_status text default null)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_result jsonb;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;

  -- created_at is emitted as fixed-width ISO-8601 UTC, so ordering on the text
  -- is chronological.
  select coalesce(jsonb_agg(to_jsonb(t) order by t.created_at), '[]'::jsonb)
  into v_result
  from (
    select
      r.id              as id,
      r.reporter_id     as reporter_id,
      r.target_kind     as target_kind,
      r.target_id       as target_id,
      r.target_user_id  as target_user_id,
      r.reason          as reason,
      r.detail          as detail,
      r.target_snapshot as target_snapshot,
      r.status          as status,
      r.resolution_note as resolution_note,
      r.resolved_at     as resolved_at,
      r.resolved_by     as resolved_by,
      to_char(r.created_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') as created_at,
      -- The orphan flag: the owner deleted the reported content themselves,
      -- which is self-remediation. The snapshot still says what was reported.
      (case r.target_kind
         when 'pebble'  then not exists (select 1 from public.pebbles  p  where p.id      = r.target_id)
         when 'profile' then not exists (select 1 from public.profiles pr where pr.user_id = r.target_id)
         else                not exists (select 1 from public.glyphs   g  where g.id      = r.target_id)
       end) as target_missing,
      -- The signal that separates one bad pebble from a bad account.
      (select count(*)
         from public.content_reports o
        where o.target_kind = r.target_kind
          and o.target_id   = r.target_id
          and o.status      = 'open') as open_reports_against_target,
      -- Live content beside the snapshot: the two disagreeing IS the evidence
      -- that the owner edited after being reported.
      (case r.target_kind
         when 'pebble' then (select jsonb_build_object(
                               'name', p.name, 'description', p.description,
                               'visibility', p.visibility)
                               from public.pebbles p where p.id = r.target_id)
         when 'profile' then (select jsonb_build_object(
                               'handle', pr.handle, 'display_name', pr.display_name,
                               'public_profile', pr.public_profile)
                               from public.profiles pr where pr.user_id = r.target_id)
         else (select jsonb_build_object(
                 'name', g.name,
                 'listed', exists (select 1 from public.glyph_submissions s
                                    where s.glyph_id = g.id and s.listed))
                 from public.glyphs g where g.id = r.target_id)
       end) as target_live
    from public.content_reports r
    where p_status is null or r.status = p_status
  ) t;

  return v_result;
end;
$$;

-- ---------------------------------------------------------------------------
-- 5. resolve_content_report — verdict AND takedown, one transaction.
--
-- Enforcement lives here rather than in a separate RPC because there is no
-- admin takedown path in the schema at all today: pebblestore only approves and
-- rejects PENDING glyph submissions (20260630084718), so an approved-and-live
-- glyph has no removal route. A triage-only queue would let a reviewer mark a
-- report `actioned` with nothing actually happening — precisely the failure
-- store review looks for. One RPC makes "the status says actioned" and "the
-- content is down" the same fact.
--
-- Not reversible here: restoring wrongly-removed content is a service-role
-- operation until an appeal flow exists.
-- ---------------------------------------------------------------------------
create function public.resolve_content_report(
  p_report_id uuid,
  p_outcome   text,
  p_note      text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_admin uuid := auth.uid();
  v_row   public.content_reports;
begin
  if not public.is_admin(v_admin) then
    raise exception 'not_admin' using errcode = '42501';
  end if;

  if p_outcome not in ('actioned', 'dismissed') then
    raise exception 'invalid_outcome';
  end if;

  -- for update: two admins resolving the same row race otherwise, and the
  -- second takedown would run against already-removed content.
  select * into v_row from public.content_reports where id = p_report_id for update;
  if not found then raise exception 'not_found'; end if;
  if v_row.status <> 'open' then raise exception 'invalid_state'; end if;

  if p_outcome = 'actioned' then
    if v_row.target_kind = 'pebble' then
      update public.pebbles set visibility = 'secret' where id = v_row.target_id;

    elsif v_row.target_kind = 'glyph' then
      -- Every submission for the glyph: a re-attributed glyph can carry more
      -- than one, and leaving any listed leaves it in the Market.
      update public.glyph_submissions set listed = false where glyph_id = v_row.target_id;

    else
      -- Both columns in ONE statement: profiles_public_requires_handle
      -- (20260730120000) is checked at statement end, so depublishing and
      -- releasing the handle cannot be split. Releasing the handle is the
      -- actual remedy for impersonation (the decisions log defers it to "M56
      -- adds reporting"). display_name is left alone — it is only reachable
      -- through the public page that just went away.
      update public.profiles
         set public_profile = false, handle = null
       where user_id = v_row.target_id;
    end if;
  end if;

  update public.content_reports
     set status          = p_outcome,
         resolution_note = nullif(btrim(p_note), ''),
         resolved_at     = now(),
         resolved_by     = v_admin
   where id = p_report_id
  returning * into v_row;

  return to_jsonb(v_row);
end;
$$;

-- ---------------------------------------------------------------------------
-- 6. Grants: authenticated only on all three. The is_admin guard does the real
-- gating on the two admin functions; anon is granted nothing anywhere.
-- ---------------------------------------------------------------------------
revoke all on function public.report_content(text, uuid, text, text)      from public, anon;
revoke all on function public.admin_list_content_reports(text)            from public, anon;
revoke all on function public.resolve_content_report(uuid, text, text)    from public, anon;

grant execute on function public.report_content(text, uuid, text, text)   to authenticated;
grant execute on function public.admin_list_content_reports(text)         to authenticated;
grant execute on function public.resolve_content_report(uuid, text, text) to authenticated;
