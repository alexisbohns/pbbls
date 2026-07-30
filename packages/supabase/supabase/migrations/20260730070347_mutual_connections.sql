-- =============================================================================
-- Mutual connections (#658, M49) — connections + invites + blocks + RPCs + purge extension
-- =============================================================================
-- The first user↔user primitive. A connection is ONE ordered row (user_a <
-- user_b) created only by accepting a multi-use invite — accepting IS the
-- mutual consent, so there is no status column: the invite table is the
-- pending state. Every write is multi-table validated logic (accept touches
-- invites, blocks and connections), so writes are definer-RPC-only and the
-- tables carry SELECT-only policies; cross-user reads return display
-- projections (display_name + glyph geometry), never a profiles row. Blocks
-- ship from day one for Apple UGC review. Design: docs/superpowers/specs/
-- 2026-07-29-mutual-connections-design.md (D1-D10).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. pgcrypto — the first `create extension` in any migration (grep-verified;
-- gen_random_uuid() everywhere is a PG13 builtin). Needed for
-- gen_random_bytes. Definer functions pin search_path = public, which does
-- not include `extensions`, so every call below is schema-qualified as
-- extensions.gen_random_bytes(32).
-- ---------------------------------------------------------------------------
create extension if not exists pgcrypto with schema extensions;

-- ---------------------------------------------------------------------------
-- 2. Tables. All user FKs cascade from auth.users (pebble_drafts precedent);
-- the explicit purge deletes in section (5) stay belt-and-braces on top.
-- ---------------------------------------------------------------------------
-- The symmetric pair is one ordered row: check (user_a < user_b) makes the
-- pair canonical, unique (user_a, user_b) makes accept idempotent (D1).
create table public.connections (
  id         uuid primary key default gen_random_uuid(),
  user_a     uuid not null references auth.users(id) on delete cascade,
  user_b     uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  check (user_a < user_b),
  unique (user_a, user_b)
);

-- unique (user_a, user_b) covers user_a lookups; this covers the other
-- membership direction.
create index connections_user_b_idx on public.connections (user_b);

-- token: 32 random bytes, base64url, stored in PLAINTEXT — it is a 7-day
-- revocable capability, not a credential, and re-display ("show the same QR
-- tomorrow") requires the server to return it on demand (D2). The unique
-- index doubles as the O(1) validation lookup.
create table public.connection_invites (
  id         uuid primary key default gen_random_uuid(),
  inviter_id uuid not null references auth.users(id) on delete cascade,
  token      text not null unique,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '7 days',
  revoked_at timestamptz
);

-- "One active invite per user" (D3). expires_at > now() cannot sit in a
-- partial-index predicate (now() is not immutable), so the index pins only
-- revoked_at is null and create_connection_invite revokes
-- expired-but-unrevoked rows first. revoked_at means "superseded or
-- withdrawn"; at most one null-revoked_at row per user ever exists.
create unique index connection_invites_one_active_idx
  on public.connection_invites (inviter_id)
  where revoked_at is null;

-- A block is one DIRECTED row (the remover blocks the removed, D6); accept
-- reads it in both directions (D5). Composite-pk join-table convention
-- (pebble_souls).
create table public.connection_blocks (
  blocker_id uuid not null references auth.users(id) on delete cascade,
  blocked_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  check (blocker_id <> blocked_id)
);

-- ---------------------------------------------------------------------------
-- 3. RLS: SELECT-only on all three (D8). Writes happen only inside the
-- definer RPCs below — pattern: wallet_balances ("No INSERT/UPDATE/DELETE
-- policies", 20260629193636), karma_events. Deliberately NOT the drafts-style
-- `for all` policy: that is for sanctioned direct-client single-table CRUD,
-- and no connections write is single-table.
-- ---------------------------------------------------------------------------
alter table public.connections        enable row level security;
alter table public.connection_invites enable row level security;
alter table public.connection_blocks  enable row level security;

create policy connections_select on public.connections for select
  to authenticated using (auth.uid() in (user_a, user_b));

-- The token is owner-visible only — this policy is what makes plaintext
-- storage safe (D2).
create policy connection_invites_select on public.connection_invites for select
  to authenticated using (inviter_id = auth.uid());

-- Blocker-only: the block row itself is never readable by the blocked user,
-- matching the masked invite_expired in accept below (D5).
create policy connection_blocks_select on public.connection_blocks for select
  to authenticated using (blocker_id = auth.uid());

-- No INSERT/UPDATE/DELETE policies on any of the three.

-- ---------------------------------------------------------------------------
-- 4. RPCs — the only write path. Error slugs are a wire contract (clients
-- substring-match them): not_authenticated (errcode 42501), invite_not_found,
-- invite_expired, cannot_accept_own_invite, not_found.
-- ---------------------------------------------------------------------------

-- Return-the-live-one, not revoke-and-reissue (D3): a link pasted into a chat
-- yesterday must not die because the inviter re-opened the invite screen.
-- p_rotate := true backs the explicit "new link" affordance and is the entire
-- revocation surface — there is no separate revoke RPC.
create or replace function public.create_connection_invite(p_rotate boolean default false)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_user uuid := auth.uid();
  v_row  public.connection_invites;
begin
  if v_user is null then raise exception 'not_authenticated' using errcode='42501'; end if;

  -- Expiry is evaluated at read time everywhere; fold any expired-but-
  -- unrevoked invite into the revoked state so the partial unique index frees.
  update public.connection_invites
     set revoked_at = now()
   where inviter_id = v_user and revoked_at is null and expires_at <= now();

  if p_rotate then
    update public.connection_invites
       set revoked_at = now()
     where inviter_id = v_user and revoked_at is null;
  end if;

  select * into v_row from public.connection_invites
   where inviter_id = v_user and revoked_at is null and expires_at > now();
  if found then
    return jsonb_build_object(
      'token',      v_row.token,
      'expires_at', v_row.expires_at,
      'created_at', v_row.created_at
    );
  end if;

  -- 32 random bytes → base64url: 43 URL-safe chars, 256 bits of entropy (D2).
  begin
    insert into public.connection_invites (inviter_id, token)
    values (v_user,
            translate(encode(extensions.gen_random_bytes(32), 'base64'), '+/=', '-_'))
    returning * into v_row;
  exception when unique_violation then
    -- Two concurrent creates: one insert wins the partial unique index, the
    -- loser re-selects the winner's row.
    select * into v_row from public.connection_invites
     where inviter_id = v_user and revoked_at is null and expires_at > now();
  end;

  return jsonb_build_object(
    'token',      v_row.token,
    'expires_at', v_row.expires_at,
    'created_at', v_row.created_at
  );
end;
$$;

-- NEVER raises — it renders a page (D4). No auth guard: anon-callable; token
-- possession is the authorization, and nothing is enumerable against 2^256
-- tokens. Reveals exactly what the accepter would learn seconds after
-- accepting: display name + glyph render geometry.
create or replace function public.preview_connection_invite(p_token text)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_invite  public.connection_invites;
  v_inviter jsonb;
begin
  select * into v_invite from public.connection_invites where token = p_token;
  if not found then
    return jsonb_build_object('status', 'not_found');
  end if;

  -- Revoked and expired share one outward shape with no inviter data: a
  -- withdrawn token goes fully dark.
  if v_invite.revoked_at is not null or v_invite.expires_at <= now() then
    return jsonb_build_object('status', 'expired');
  end if;

  select jsonb_build_object(
    'display_name', pr.display_name,
    'glyph', case when g.id is not null then
      jsonb_build_object('strokes', g.strokes, 'view_box', g.view_box)
    else null end
  ) into v_inviter
  from public.profiles pr
  left join public.glyphs g on g.id = pr.glyph_id
  where pr.user_id = v_invite.inviter_id;

  return jsonb_build_object('status', 'valid', 'inviter', v_inviter);
end;
$$;

-- Idempotent accept (D5): re-scans, double-taps and retries are the NORMAL
-- case for a multi-use QR, so already-connected succeeds with a flag — never
-- errors. The invite row is NOT consumed (multi-use until revoked/expired).
create or replace function public.accept_connection_invite(p_token text)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_user              uuid := auth.uid();
  v_invite            public.connection_invites;
  v_connection_id     uuid;
  v_connected_at      timestamptz;
  v_already_connected boolean := false;
  v_peer              jsonb;
begin
  if v_user is null then raise exception 'not_authenticated' using errcode='42501'; end if;

  -- invite_not_found (no such token) vs invite_expired (revoked/expired) —
  -- distinguished so clients can word the dead-link page honestly.
  select * into v_invite from public.connection_invites where token = p_token;
  if not found then raise exception 'invite_not_found'; end if;
  if v_invite.revoked_at is not null or v_invite.expires_at <= now() then
    raise exception 'invite_expired';
  end if;

  if v_invite.inviter_id = v_user then raise exception 'cannot_accept_own_invite'; end if;

  -- A block in EITHER direction surfaces as invite_expired — the same shape
  -- as real expiry, so a block is never CONFIRMED by the accept path. (Not
  -- fully hidden: preview is block-unaware by design (D4) and third parties
  -- keep succeeding on the same token, so a blocked peer can infer the block
  -- from preview=valid vs accept=expired — accepted residual.) This is also
  -- what stops a removed-with-block peer re-entering through the remover's
  -- still-live multi-use QR (D5, D6); the race with a concurrent
  -- remove_connection is closed by the post-insert re-check below.
  if exists (
    select 1 from public.connection_blocks b
    where (b.blocker_id = v_user and b.blocked_id = v_invite.inviter_id)
       or (b.blocker_id = v_invite.inviter_id and b.blocked_id = v_user)
  ) then
    raise exception 'invite_expired';
  end if;

  -- Ordered-pair normalization + on conflict do nothing = structural
  -- idempotency (D1).
  insert into public.connections (user_a, user_b)
  values (least(v_user, v_invite.inviter_id), greatest(v_user, v_invite.inviter_id))
  on conflict do nothing
  returning id, created_at into v_connection_id, v_connected_at;

  if v_connection_id is null then
    v_already_connected := true;
    select c.id, c.created_at into v_connection_id, v_connected_at
    from public.connections c
    where c.user_a = least(v_user, v_invite.inviter_id)
      and c.user_b = greatest(v_user, v_invite.inviter_id);
  end if;

  -- Post-insert block RE-check — an addition to D5's three-step validation
  -- order, closing its read-committed race with remove_connection(p_block =>
  -- true): a concurrent remover can commit its block between the pre-insert
  -- check's snapshot and our insert (the insert waits out the remover's
  -- uncommitted delete of the pair's old row, then proceeds against the dead
  -- tuple, resurrecting the connection AFTER the block landed). Blocks are
  -- written only by remove_connection, whose paired delete must commit
  -- before our insert can succeed, so any such block is always visible to
  -- this later statement's snapshot. Raising rolls back the insert above
  -- with the whole transaction (no explicit undo delete: it would be rolled
  -- back by the raise itself) and masks as invite_expired like the first
  -- check.
  if exists (
    select 1 from public.connection_blocks b
    where (b.blocker_id = v_user and b.blocked_id = v_invite.inviter_id)
       or (b.blocker_id = v_invite.inviter_id and b.blocked_id = v_user)
  ) then
    raise exception 'invite_expired';
  end if;

  -- Inviter display projection — never a profiles row (same shape as
  -- preview's inviter).
  select jsonb_build_object(
    'display_name', pr.display_name,
    'glyph', case when g.id is not null then
      jsonb_build_object('strokes', g.strokes, 'view_box', g.view_box)
    else null end
  ) into v_peer
  from public.profiles pr
  left join public.glyphs g on g.id = pr.glyph_id
  where pr.user_id = v_invite.inviter_id;

  return jsonb_build_object(
    'connection_id',     v_connection_id,
    'already_connected', v_already_connected,
    'connected_at',      v_connected_at,
    'peer',              v_peer
  );
end;
$$;

-- Remove severs for both sides; p_block additionally inserts the directed
-- block row (the remover blocks the removed, D6). not_found for a foreign
-- connection: never confirm to a non-member that the row exists.
create or replace function public.remove_connection(p_connection_id uuid, p_block boolean default false)
returns void
language plpgsql security definer set search_path = public as $$
declare
  v_user uuid := auth.uid();
  v_row  public.connections;
  v_peer uuid;
begin
  if v_user is null then raise exception 'not_authenticated' using errcode='42501'; end if;

  select * into v_row from public.connections where id = p_connection_id;
  if not found then raise exception 'not_found'; end if;
  if v_user not in (v_row.user_a, v_row.user_b) then raise exception 'not_found'; end if;

  v_peer := case when v_row.user_a = v_user then v_row.user_b else v_row.user_a end;

  delete from public.connections where id = p_connection_id;

  -- -------------------------------------------------------------------------
  -- >>> M52/M53: sever seams and pairs for this pair HERE (same transaction). <<<
  -- The roadmap promises "severs dependent seams and pairs in the same
  -- transaction"; those tables arrive in M52/M53. This is the purge_account
  -- append-marker pattern applied to a function body.
  -- -------------------------------------------------------------------------

  if p_block then
    insert into public.connection_blocks (blocker_id, blocked_id)
    values (v_user, v_peer)
    on conflict do nothing;
  end if;
end;
$$;

-- Definer jsonb projection, not a view (D7): a security_invoker view joining
-- profiles returns null peers (owner-only profiles_select), and a
-- definer-rights view is the #616 vulnerability class. One call feeds the
-- list on all three surfaces; refreshed on screen open, no realtime.
create or replace function public.get_connections()
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_user   uuid := auth.uid();
  v_result jsonb;
begin
  if v_user is null then raise exception 'not_authenticated' using errcode='42501'; end if;

  select coalesce(jsonb_agg(jsonb_build_object(
    'connection_id', c.id,
    'connected_at',  c.created_at,
    'peer', jsonb_build_object(
      'display_name', pr.display_name,
      'glyph', case when g.id is not null then
        jsonb_build_object('strokes', g.strokes, 'view_box', g.view_box)
      else null end
    )
  ) order by c.created_at desc), '[]'::jsonb)
  into v_result
  from public.connections c
  join public.profiles pr
    on pr.user_id = case when c.user_a = v_user then c.user_b else c.user_a end
  left join public.glyphs g on g.id = pr.glyph_id
  where v_user in (c.user_a, c.user_b);

  return v_result;
end;
$$;

-- ---------------------------------------------------------------------------
-- 5. purge_account: second customer of the M46 standing rule
-- (20260729201326:26-28). Body otherwise verbatim from
-- 20260729213348_pebble_drafts.sql — the only change is the three connections
-- deletes at the section-(4) append marker. Section (4) is the right home:
-- all FKs point only at auth.users (cascade) and nothing references these
-- tables at M49 — M52/M53 will insert their own severing above these lines.
-- ---------------------------------------------------------------------------
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
  -- user's soul (RESTRICT FK) ∪ another user's pebble (NO ACTION FK) ∪
  -- another user's profile glyph (SET NULL, kept for their UX) ∪ a domain
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
      or exists (select 1 from public.pebbles pb
                 where pb.glyph_id = g.id and pb.user_id <> p_user_id)
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
  -- (3) Content, FK-ordered: pebbles before glyphs (pebbles.glyph_id NO
  -- ACTION), souls before glyphs (souls.glyph_id RESTRICT). Deleting pebbles
  -- cascades pebble_cards / pebble_domains / pebble_souls / snaps /
  -- collection_pebbles; the explicit snaps delete is belt-and-braces.
  -- -------------------------------------------------------------------------
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

  delete from public.pebble_drafts where user_id = p_user_id;
  get diagnostics v_n = row_count;
  v_counts := v_counts || jsonb_build_object('pebble_drafts', v_n);

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

-- purge_account grants are unchanged (service-role only, set in
-- 20260729201326); create or replace preserves them.

-- ---------------------------------------------------------------------------
-- 6. Grants (hygiene per 20260729201326:186-190). preview_connection_invite
-- is additionally granted to anon (D4): token possession = capability, and
-- the sign-up-first /invite/[token] page must render for signed-out visitors.
-- anon-grant precedents: is_admin (20260421000000:29), v_domains_with_glyph
-- (20260703000000:30).
-- ---------------------------------------------------------------------------
revoke all on function public.create_connection_invite(boolean)    from public, anon;
revoke all on function public.preview_connection_invite(text)      from public, anon;
revoke all on function public.accept_connection_invite(text)       from public, anon;
revoke all on function public.remove_connection(uuid, boolean)     from public, anon;
revoke all on function public.get_connections()                    from public, anon;
grant execute on function public.create_connection_invite(boolean) to authenticated;
grant execute on function public.accept_connection_invite(text)    to authenticated;
grant execute on function public.remove_connection(uuid, boolean)  to authenticated;
grant execute on function public.get_connections()                 to authenticated;
grant execute on function public.preview_connection_invite(text)   to anon, authenticated;
