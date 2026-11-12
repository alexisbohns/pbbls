# Report a person and a glyph on iOS: implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give iOS the fourth App Store 1.2 pillar: a user can report a connected person, an inviter, and a market glyph. On the server, a new `report_person` RPC files a report against a person without the client ever holding that person's user id.

**Architecture:** Part 1 adds `report_person`, a `security definer` RPC that resolves the person from a connection id or an invite token and files a `profile` report. With the remove-and-block flag set, it also calls `remove_connection(id, true)` in the same transaction. Part 2 adds a self-contained `Features/Report/` module: a reason enum, a target enum, a receipt, error mapping, a stateless service, an `@Observable` model and the sheet. Part 3 wires three entry points: the Connections context menu, the glyph drawer and the invite sheet.

**Tech Stack:** Postgres/plpgsql (Supabase), Deno harness (`supabase-js`), SwiftUI iOS 17, `@Observable`, Swift Testing, `supabase-swift`.

**Spec:** `docs/superpowers/specs/2026-11-12-report-person-design.md`. Read it first.

---

## Working environment (every task)

- Worktree: `/Users/alexis/code/pbbls-report-ios`. Never work in `/Users/alexis/code/pbbls` (that is `main`).
- The stack, bottom to top:

  | Part | Branch |
  |---|---|
  | 1 | `fix/1008-report-person-ios` |
  | 2 | `feat/838-report-sheet-ios` |
  | 3 | `feat/838-report-entries-ios` |

  Every branch carries `-ios` so Vercel skips it. The orchestrator creates the branches. A task names its branch: check it out with `git checkout <branch>`, and if it is not current, stop and report.
- iOS build and test commands. Run them from `apps/ios`, always with this private derived data path:

  ```bash
  DD=/private/tmp/claude-503/-Users-alexis-code-pbbls/8baa7e89-6ad2-4dee-9aa7-1a38f40714ff/scratchpad/dd-report
  xcodegen generate
  xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' \
    -derivedDataPath "$DD" -only-testing:PebblesTests/<SuiteStructName> 2>&1 | tail -30
  xcodebuild build -scheme Pebbles -destination 'generic/platform=iOS Simulator' -derivedDataPath "$DD" 2>&1 | tail -5
  swiftlint lint --quiet Pebbles/Features/Report PebblesTests/Features/Report
  ```

  About 27 reference-image tests fail on untouched `main` on this Mac. Never run the whole suite as a gate. Run your own suites with `-only-testing`, plus a full build.
- Commits: conventional, lowercase, no period, one logical change each. End every message with the line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Never edit `docs/arkaik/bundle.json` or `journal.jsonl`.
- `Localizable.xcstrings` is formatting-sensitive. Insert entries as text at an anchor with python `str.replace` (assert the anchor occurs exactly once), validate with `json.load`, and check `git diff --stat` shows only added lines. Never rewrite the file with `json.dump`.

---

## File map

**Part 1 (server)**
- Create `packages/supabase/supabase/migrations/20261112090000_report_person.sql`: the RPC and its grants.
- Modify `packages/supabase/scripts/rls-expectations.json`: register `public.report_person` (the RLS denial harness fails on an unregistered definer function).
- Modify `packages/supabase/scripts/verify-content-reports.ts`: section 7, the `report_person` cases.
- Modify `packages/supabase/types/database.ts`: regenerated (orchestrator).
- Create `packages/supabase/fixtures/report-person-receipt.json`: the receipt captured verbatim from the harness run (orchestrator).
- Modify `docs/decisions/log.md`: one entry (orchestrator).

**Part 2 (iOS module)**, all new, under `apps/ios/Pebbles/Features/Report/`:
- `ReportReason.swift`: the nine reasons, raw values = server slugs, localized labels.
- `ReportTarget.swift`: glyph / connection / invite, plus the title and toggle copy.
- `ReportReceipt.swift`: decodes `{id, status, created_at}` tolerantly.
- `ReportError.swift`: slug → error → message.
- `ReportService.swift`: `ReportRequest` (pure: target → RPC name + params) and `ReportService` (calls it).
- `ReportSheetModel.swift`: the `@Observable` state and the submit logic.
- `ReportSheet.swift`: the view.
- Tests under `apps/ios/PebblesTests/Features/Report/`: `ReportModelsTests.swift`, `ReportRequestTests.swift`, `ReportSheetModelTests.swift`.
- `apps/ios/PebblesTests/Fixtures/report-person-receipt.json`: a verbatim copy of the part 1 fixture.
- Modify `apps/ios/Pebbles/Resources/Localizable.xcstrings`: new EN and FR entries.

**Part 3 (iOS entry points)**
- Modify `apps/ios/Pebbles/Features/Connections/ConnectionsListView.swift`
- Modify `apps/ios/Pebbles/Features/Glyph/Views/GlyphDetailDrawer.swift`
- Modify `apps/ios/Pebbles/Features/Connections/InviteAcceptSheet.swift`
- Modify `apps/ios/Pebbles/Resources/Localizable.xcstrings` (one entry: "Report")

---

# Part 1: `report_person` (branch `fix/1008-report-person-ios`)

### Task 1: The migration, its registry entry, and a local smoke test

**Files:**
- Create: `packages/supabase/supabase/migrations/20261112090000_report_person.sql`
- Modify: `packages/supabase/scripts/rls-expectations.json` (insert after the `"public.report_content"` entry, around line 1663)
- Scratch only (never committed): `$SCRATCH/report-person-smoke/`, where `SCRATCH=/private/tmp/claude-503/-Users-alexis-code-pbbls/8baa7e89-6ad2-4dee-9aa7-1a38f40714ff/scratchpad`

Background: `report_content` (newest body in `20261012090300_moments_split_moderation_purge.sql`) is the template. `preview_connection_invite` (newest body in `20261007140000_block_severs_public_reads.sql`) defines the invite gates. `remove_connection` (`20260730070347_mutual_connections.sql`) has in-body append markers: **call it, never re-emit it** (the CI guard `.github/scripts/check-migration-reemissions.sh` enforces this).

- [ ] **Step 1: Write the migration**

```sql
-- report_person (#1008, #838): report a connected peer or an inviter.
--
-- report_content's 'profile' branch takes the person's user_id and requires a
-- public profile. The connection projections (get_connections,
-- preview_connection_invite) never hand a client a user_id (standing rule:
-- cross-user reads never return one), so a connected peer or an inviter with
-- a private profile could not be reported at all. This RPC takes the ROUTE the
-- reporter met the person by (their connection id, or the invite token they
-- hold) and resolves the person here.
--
-- It files an ordinary 'profile' report (target_id = target_user_id = the
-- person), so the admin queue, admin_set_content_hidden('profile', …) and
-- content_reports_open_unique work unchanged: a person reported from their
-- invite and again after connecting is ONE open report. The snapshot records
-- the route in `via`.
--
-- report_content is deliberately not re-emitted. remove_connection is CALLED,
-- never re-emitted, so its in-body append markers stay intact.
create or replace function public.report_person(
  p_reason           text,
  p_detail           text    default null,
  p_connection_id    uuid    default null,
  p_invite_token     text    default null,
  p_remove_and_block boolean default false
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user     uuid := auth.uid();
  v_person   uuid;
  v_via      text;
  v_conn     public.connections;
  v_invite   public.connection_invites;
  v_snapshot jsonb;
  v_row      public.content_reports;
begin
  if v_user is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;

  -- Exactly one route. Both or neither is a malformed call, reported with the
  -- same slug report_content uses for an unknown target kind.
  if (p_connection_id is null) = (p_invite_token is null) then
    raise exception 'invalid_kind';
  end if;

  if p_reason is null
     or p_reason not in ('sexual', 'violence', 'hate', 'harassment',
                         'self_harm', 'illegal', 'spam', 'impersonation', 'other') then
    raise exception 'invalid_reason';
  end if;

  if p_connection_id is not null then
    -- A foreign connection and a missing one answer the same not_found, as
    -- remove_connection does: never confirm to a non-member that the row exists.
    select * into v_conn from public.connections where id = p_connection_id;
    if not found or v_user not in (v_conn.user_a, v_conn.user_b) then
      raise exception 'not_found';
    end if;
    v_person := case when v_conn.user_a = v_user then v_conn.user_b else v_conn.user_a end;
    v_via := 'connection';
  else
    -- preview_connection_invite's gates (20261007140000): an unknown, revoked
    -- or expired token, an inviter who has blocked the caller, and a hidden
    -- inviter all answer one not_found. A token the caller could not preview
    -- must not be a way to file against its inviter.
    select * into v_invite from public.connection_invites where token = p_invite_token;
    if not found
       or v_invite.revoked_at is not null
       or v_invite.expires_at <= now()
       or exists (
            select 1 from public.connection_blocks b
             where b.blocker_id = v_invite.inviter_id
               and b.blocked_id = v_user)
       or not exists (
            select 1 from public.profiles pr
             where pr.user_id = v_invite.inviter_id
               and pr.hidden_at is null)
    then
      raise exception 'not_found';
    end if;
    if v_invite.inviter_id = v_user then
      raise exception 'cannot_report_own';
    end if;
    v_person := v_invite.inviter_id;
    v_via := 'invite';
  end if;

  -- The person as they read at file time, plus the route. Only the TARGET's
  -- text, so purge-by-target_user_id erases it (same rule as report_content).
  select jsonb_build_object('handle', pr.handle, 'display_name', pr.display_name, 'via', v_via)
    into v_snapshot
    from public.profiles pr
   where pr.user_id = v_person;
  v_snapshot := coalesce(v_snapshot, jsonb_build_object('via', v_via));

  -- Idempotent on content_reports_open_unique, exactly as report_content: a
  -- second file is a success and returns the standing report.
  insert into public.content_reports
    (reporter_id, target_kind, target_id, target_user_id, reason, detail, target_snapshot)
  values
    (v_user, 'profile', v_person, v_person, p_reason,
     nullif(btrim(p_detail), ''), v_snapshot)
  on conflict (reporter_id, target_kind, target_id) where status = 'open'
  do nothing
  returning * into v_row;

  if v_row.id is null then
    select * into v_row
      from public.content_reports
     where reporter_id = v_user
       and target_kind = 'profile'
       and target_id   = v_person
       and status      = 'open';
  end if;

  -- Report and block in one transaction. Only the connection route can block:
  -- an invite has no connection yet.
  if v_via = 'connection' and coalesce(p_remove_and_block, false) then
    perform public.remove_connection(p_connection_id, true);
  end if;

  -- report_content's projection: never a user id, never the snapshot;
  -- created_at whole-second UTC (standing timestamp rule).
  return jsonb_build_object(
    'id', v_row.id,
    'status', v_row.status,
    'created_at', to_char(v_row.created_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
  );
end;
$$;

revoke all on function public.report_person(text, text, uuid, text, boolean) from public, anon;
grant execute on function public.report_person(text, text, uuid, text, boolean) to authenticated;
```

- [ ] **Step 2: Register the function for the RLS denial harness**

In `packages/supabase/scripts/rls-expectations.json`, insert this entry right after the closing `},` of `"public.report_content"`. Copy that entry's exact indentation (6 spaces before the key). Insert it as text, not with `json.dump`:

```json
    "public.report_person": {
      "kind": "owner-only",
      "args": {
        "p_reason": "spam",
        "p_connection_id": "$connection",
        "p_remove_and_block": true
      },
      "intact": [
        "connections"
      ],
      "note": "Takes a connection id; a stranger passing the owner's connection is refused not_found, nothing is filed and the connection is untouched."
    },
```

Validate with `python3 -c "import json;json.load(open('packages/supabase/scripts/rls-expectations.json'))"` and `git diff --stat`, which should show about 13 added lines and no deletions.

- [ ] **Step 3: Build the local stub schema**

Start the native Postgres (Homebrew `postgresql@16`; no Docker):

```bash
SCRATCH=/private/tmp/claude-503/-Users-alexis-code-pbbls/8baa7e89-6ad2-4dee-9aa7-1a38f40714ff/scratchpad
W=$SCRATCH/report-person-smoke; rm -rf $W; mkdir -p $W
PATH=/opt/homebrew/opt/postgresql@16/bin:$PATH
initdb -D $W/pgdata -U postgres --auth=trust >/dev/null
pg_ctl -D $W/pgdata -o "-p 55432 -k /tmp -c listen_addresses=127.0.0.1" -l $W/pg.log start
```

Write `$W/stub.sql`:

```sql
create schema auth;
create table auth.users (id uuid primary key);
create function auth.uid() returns uuid language sql stable as
  $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
create role anon; create role authenticated;

create table public.profiles (
  user_id uuid primary key references auth.users(id),
  handle text, display_name text,
  public_profile boolean not null default false,
  hidden_at timestamptz);
create table public.connections (
  id uuid primary key default gen_random_uuid(),
  user_a uuid not null, user_b uuid not null,
  created_at timestamptz not null default now(),
  unique (user_a, user_b));
create table public.connection_invites (
  id uuid primary key default gen_random_uuid(),
  inviter_id uuid not null, token text not null unique,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '30 days',
  revoked_at timestamptz);
create table public.connection_blocks (
  blocker_id uuid not null, blocked_id uuid not null,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id));
create table public.content_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid references auth.users(id),
  target_kind text not null check (target_kind in ('moment', 'profile', 'glyph')),
  target_id uuid not null,
  target_user_id uuid not null references auth.users(id),
  reason text not null check (reason in ('sexual','violence','hate','harassment',
    'self_harm','illegal','spam','impersonation','other')),
  detail text check (detail is null or length(detail) <= 1000),
  target_snapshot jsonb,
  status text not null default 'open' check (status in ('open','actioned','dismissed')),
  created_at timestamptz not null default now());
create unique index content_reports_open_unique
  on public.content_reports (reporter_id, target_kind, target_id) where status = 'open';

-- Users: R reporter, P peer (private profile), S stranger, H hidden inviter.
insert into auth.users values
  ('00000000-0000-0000-0000-00000000000a'), ('00000000-0000-0000-0000-00000000000b'),
  ('00000000-0000-0000-0000-00000000000c'), ('00000000-0000-0000-0000-00000000000d');
insert into public.profiles (user_id, handle, display_name, hidden_at) values
  ('00000000-0000-0000-0000-00000000000a', 'r', 'Reporter', null),
  ('00000000-0000-0000-0000-00000000000b', null, 'Peer', null),
  ('00000000-0000-0000-0000-00000000000c', 's', 'Stranger', null),
  ('00000000-0000-0000-0000-00000000000d', 'h', 'Hidden', now());
insert into public.connections (id, user_a, user_b) values
  ('11111111-1111-1111-1111-111111111111',
   '00000000-0000-0000-0000-00000000000a', '00000000-0000-0000-0000-00000000000b');
insert into public.connection_invites (inviter_id, token, revoked_at, expires_at) values
  ('00000000-0000-0000-0000-00000000000b', 'live',    null,  now() + interval '1 day'),
  ('00000000-0000-0000-0000-00000000000b', 'revoked', now(), now() + interval '1 day'),
  ('00000000-0000-0000-0000-00000000000b', 'expired', null,  now() - interval '1 day'),
  ('00000000-0000-0000-0000-00000000000d', 'hidden',  null,  now() + interval '1 day');
```

Extract the real `remove_connection` body and apply everything:

```bash
awk '/^create or replace function public.remove_connection/{p=1} p{print} p&&/^\$\$;/{exit}' \
  packages/supabase/supabase/migrations/20260730070347_mutual_connections.sql > $W/remove_connection.sql
PSQL="psql -h 127.0.0.1 -p 55432 -U postgres -v ON_ERROR_STOP=1 -q"
$PSQL -f $W/stub.sql && $PSQL -f $W/remove_connection.sql \
  && $PSQL -f packages/supabase/supabase/migrations/20261112090000_report_person.sql && echo APPLIED
```

Expected: `APPLIED`, with no errors.

- [ ] **Step 4: Run the scenarios**

Write `$W/scenarios.sql`. Each `do` block prints `ok <case>` or raises:

```sql
create function pg_temp.expect_error(p_sql text, p_slug text, p_case text) returns void
language plpgsql as $$
begin
  execute p_sql;
  raise exception 'FAIL % — no error', p_case;
exception when others then
  if sqlerrm like 'FAIL%' then raise; end if;
  if position(p_slug in sqlerrm) = 0 then raise exception 'FAIL % — got %', p_case, sqlerrm; end if;
  raise notice 'ok %', p_case;
end $$;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', false);
-- invite route, private-profile inviter
select public.report_person('spam', null, null, 'live') as via_invite \gset
select :'via_invite'::jsonb ? 'id' and not (:'via_invite'::jsonb ? 'target_user_id') as invite_ok \gset
\echo invite route returns id only: :invite_ok
-- connection route lands on the same open report
select (public.report_person('harassment', '  ', '11111111-1111-1111-1111-111111111111', null)->>'id')
     = (:'via_invite'::jsonb->>'id') as same_report \gset
\echo same standing report: :same_report
select pg_temp.expect_error($q$select public.report_person('spam', null, null, 'revoked')$q$, 'not_found', 'revoked invite');
select pg_temp.expect_error($q$select public.report_person('spam', null, null, 'expired')$q$, 'not_found', 'expired invite');
select pg_temp.expect_error($q$select public.report_person('spam', null, null, 'nope')$q$, 'not_found', 'unknown token');
select pg_temp.expect_error($q$select public.report_person('spam', null, null, 'hidden')$q$, 'not_found', 'hidden inviter');
select pg_temp.expect_error($q$select public.report_person('spam')$q$, 'invalid_kind', 'no route');
select pg_temp.expect_error($q$select public.report_person('spam', null, '11111111-1111-1111-1111-111111111111', 'live')$q$, 'invalid_kind', 'two routes');
select pg_temp.expect_error($q$select public.report_person('vibes', null, null, 'live')$q$, 'invalid_reason', 'bad reason');
select pg_temp.expect_error($q$select public.report_person(null, null, null, 'live')$q$, 'invalid_reason', 'null reason');

-- the inviter reporting their own invite
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000b', false);
select pg_temp.expect_error($q$select public.report_person('spam', null, null, 'live')$q$, 'cannot_report_own', 'own invite');

-- a stranger through someone else's connection
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000c', false);
select pg_temp.expect_error($q$select public.report_person('spam', null, '11111111-1111-1111-1111-111111111111', null)$q$, 'not_found', 'non-party');

-- report + remove + block, as R
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000a', false);
select public.report_person('harassment', null, '11111111-1111-1111-1111-111111111111', null, true);
select count(*) = 0 as connection_gone from public.connections where id = '11111111-1111-1111-1111-111111111111' \gset
select count(*) = 1 as block_row from public.connection_blocks
 where blocker_id = '00000000-0000-0000-0000-00000000000a' and blocked_id = '00000000-0000-0000-0000-00000000000b' \gset
\echo connection gone: :connection_gone, block row: :block_row
select count(*) as reports, max(target_snapshot->>'via') as via, bool_and(detail is null) as blank_detail_null
  from public.content_reports;

-- inviter has blocked the caller: P blocks S, S tries P's live invite
insert into public.connection_blocks values ('00000000-0000-0000-0000-00000000000b', '00000000-0000-0000-0000-00000000000c');
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-00000000000c', false);
select pg_temp.expect_error($q$select public.report_person('spam', null, null, 'live')$q$, 'not_found', 'inviter blocked caller');

-- signed out
select set_config('request.jwt.claim.sub', '', false);
select pg_temp.expect_error($q$select public.report_person('spam', null, null, 'live')$q$, 'not_authenticated', 'anon');
```

Run: `$PSQL -f $W/scenarios.sql 2>&1`

Expected: `invite route returns id only: t`, `same standing report: t`, `connection gone: t, block row: t`, the reports row reads `1 | invite | t` (one open report; `via` is from the first file because the second hit the conflict), and an `ok …` notice for each of the 12 `expect_error` cases. No `FAIL` lines.

- [ ] **Step 5: Stop Postgres and clean up**

```bash
pg_ctl -D $W/pgdata stop && rm -rf $W
```

- [ ] **Step 6: Run the re-emission guard**

Run: `bash .github/scripts/check-migration-reemissions.sh origin/main`
Expected: exit 0 (`report_person` is new, `remove_connection` is only called).

- [ ] **Step 7: Commit**

```bash
git add packages/supabase/supabase/migrations/20261112090000_report_person.sql packages/supabase/scripts/rls-expectations.json
git commit -m "fix(db): report a connected person or an inviter through report_person"
```

### Task 2: Harness cases in `verify-content-reports.ts`

**Files:**
- Modify: `packages/supabase/scripts/verify-content-reports.ts`

The harness signs up throwaway users and deletes them through the `delete-account` edge function in `finally`. It runs against the remote project, and **the migration is not on the remote yet**: in this task you only write and typecheck. The orchestrator runs it after `db:push`.

- [ ] **Step 1: Declare two more users**

Next to `let owner: TestUser | null = null;` and `let reporter …`, add:

```ts
let peer: TestUser | null = null;
let stranger: TestUser | null = null;
```

And change the cleanup loop in `finally` to `for (const u of [reporter, owner, peer, stranger]) {`.

- [ ] **Step 2: Add section 7 before the `} catch (err) {` that closes the main `try`**

Place it right after section 6 (the admin-surface checks):

```ts
  // ---------------------------------------------------------------------------
  // 7. report_person (#1008): a person reached through a connection or an
  //    invite, filed by ROUTE, never by user id. The peer's profile stays
  //    private: that is exactly the case report_content's 'profile' branch
  //    cannot reach. The reporter here is `reporter`, the person is `peer`.
  // ---------------------------------------------------------------------------
  peer = await signUp("peer");
  stranger = await signUp("stranger");
  const p = peer.client;
  const s = stranger.client;

  const { data: invite, error: inviteErr } = await p.rpc("create_connection_invite", { p_rotate: false });
  const token = (invite as { token?: string } | null)?.token;
  if (inviteErr || !token) throw new Error(`create_connection_invite: ${inviteErr?.message ?? "no token"}`);

  const { data: viaInvite, error: viaInviteErr } = await r.rpc("report_person", {
    p_reason: "spam", p_invite_token: token,
  });
  const viaInviteRow = viaInvite as { id?: string; status?: string; created_at?: string } | null;
  check("an inviter is reportable through a live invite",
    !viaInviteErr && !!viaInviteRow?.id && viaInviteRow?.status === "open", viaInviteErr?.message);
  check("report_person emits whole-second UTC created_at",
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/.test(viaInviteRow?.created_at ?? ""), viaInviteRow?.created_at);
  check("report_person returns id, status and created_at only",
    JSON.stringify(Object.keys(viaInviteRow ?? {}).sort()) === JSON.stringify(["created_at", "id", "status"]),
    JSON.stringify(viaInviteRow));
  // Verbatim payload for the iOS decoding test (packages/supabase/fixtures/report-person-receipt.json).
  console.log(`report_person receipt: ${JSON.stringify(viaInvite)}`);

  const { error: ownInviteErr } = await p.rpc("report_person", { p_reason: "spam", p_invite_token: token });
  check("an inviter cannot report through their own invite",
    !!ownInviteErr && ownInviteErr.message.includes("cannot_report_own"), ownInviteErr?.message);

  const { data: accepted, error: acceptErr } = await r.rpc("accept_connection_invite", { p_token: token });
  const connectionId = (accepted as { connection_id?: string } | null)?.connection_id;
  if (acceptErr || !connectionId) throw new Error(`accept_connection_invite: ${acceptErr?.message ?? "no id"}`);

  const { data: viaConn, error: viaConnErr } = await r.rpc("report_person", {
    p_reason: "harassment", p_detail: "verify run", p_connection_id: connectionId,
  });
  check("a connected peer with a private profile is reportable",
    !viaConnErr && !!(viaConn as { id?: string } | null)?.id, viaConnErr?.message);
  check("the invite report and the connection report are one standing report",
    (viaConn as { id?: string } | null)?.id === viaInviteRow?.id, JSON.stringify(viaConn));

  const { error: nonPartyErr } = await s.rpc("report_person", { p_reason: "spam", p_connection_id: connectionId });
  check("a non-party cannot report through someone else's connection",
    !!nonPartyErr && nonPartyErr.message.includes("not_found"), nonPartyErr?.message);
  const { error: ghostConnErr } = await r.rpc("report_person", {
    p_reason: "spam", p_connection_id: crypto.randomUUID(),
  });
  check("a foreign connection and a nonexistent one fail identically",
    !!ghostConnErr && ghostConnErr.message === nonPartyErr?.message,
    `foreign="${nonPartyErr?.message}" ghost="${ghostConnErr?.message}"`);

  const { error: bothErr } = await r.rpc("report_person", {
    p_reason: "spam", p_connection_id: connectionId, p_invite_token: token,
  });
  check("two routes at once are refused", !!bothErr && bothErr.message.includes("invalid_kind"), bothErr?.message);
  const { error: neitherErr } = await r.rpc("report_person", { p_reason: "spam" });
  check("no route is refused", !!neitherErr && neitherErr.message.includes("invalid_kind"), neitherErr?.message);
  const { error: badReasonErr } = await r.rpc("report_person", { p_reason: "vibes", p_connection_id: connectionId });
  check("an unknown reason is refused by report_person",
    !!badReasonErr && badReasonErr.message.includes("invalid_reason"), badReasonErr?.message);

  // Rotating revokes the first token: it goes dark exactly like an unknown one.
  const { data: rotated, error: rotateErr } = await p.rpc("create_connection_invite", { p_rotate: true });
  const token2 = (rotated as { token?: string } | null)?.token;
  if (rotateErr || !token2) throw new Error(`rotate invite: ${rotateErr?.message ?? "no token"}`);
  const { error: revokedErr } = await s.rpc("report_person", { p_reason: "spam", p_invite_token: token });
  check("a revoked invite is not reportable",
    !!revokedErr && revokedErr.message.includes("not_found"), revokedErr?.message);
  const { error: ghostTokenErr } = await s.rpc("report_person", {
    p_reason: "spam", p_invite_token: `no-such-${runId}`,
  });
  check("a revoked token and an unknown one fail identically",
    !!ghostTokenErr && ghostTokenErr.message === revokedErr?.message,
    `revoked="${revokedErr?.message}" ghost="${ghostTokenErr?.message}"`);

  // Report and block in one call: the connection goes, the reporter's block
  // row appears (connection_blocks is readable by the blocker only).
  const { data: blockedReport, error: blockErr } = await r.rpc("report_person", {
    p_reason: "harassment", p_connection_id: connectionId, p_remove_and_block: true,
  });
  check("report with remove-and-block returns the standing report",
    !blockErr && (blockedReport as { id?: string } | null)?.id === viaInviteRow?.id,
    blockErr ? blockErr.message : JSON.stringify(blockedReport));
  const { data: leftover } = await r.from("connections").select("id").eq("id", connectionId);
  check("the connection is gone after remove-and-block", (leftover?.length ?? -1) === 0, `rows=${leftover?.length}`);
  const { data: blocks } = await r.from("connection_blocks").select("blocked_id").eq("blocked_id", peer.id);
  check("the reporter now blocks the peer", (blocks?.length ?? 0) === 1, `rows=${blocks?.length}`);

  // An inviter who has blocked the caller: their invite is dark to that caller.
  const { data: accepted2, error: accept2Err } = await s.rpc("accept_connection_invite", { p_token: token2 });
  const connection2 = (accepted2 as { connection_id?: string } | null)?.connection_id;
  if (accept2Err || !connection2) throw new Error(`stranger accept: ${accept2Err?.message ?? "no id"}`);
  const { error: removeErr } = await p.rpc("remove_connection", { p_connection_id: connection2, p_block: true });
  if (removeErr) throw new Error(`peer blocks stranger: ${removeErr.message}`);
  const { error: blockedCallerErr } = await s.rpc("report_person", { p_reason: "spam", p_invite_token: token2 });
  check("an inviter who blocked the caller is not reportable through their invite",
    !!blockedCallerErr && blockedCallerErr.message.includes("not_found"), blockedCallerErr?.message);
```

Also add these two lines to the "WHAT THIS HARNESS STRUCTURALLY CANNOT PROVE" block in the header comment:

```ts
 *   - report_person's hidden-inviter gate: hiding a profile needs an admin
 *     (proven on a local Postgres when the migration was written)
```

- [ ] **Step 3: Typecheck**

Run: `cd packages/supabase && deno check scripts/verify-content-reports.ts`
Expected: no errors. If `deno` is missing, report it and do not install anything.

- [ ] **Step 4: Commit**

```bash
git add packages/supabase/scripts/verify-content-reports.ts
git commit -m "test(db): prove report_person in the content reports harness"
```

### Task 3 (orchestrator, not a subagent): push, prove, regenerate, capture

This writes to the production database. It adds one function and changes nothing else.

- [ ] `npm run db:push --workspace=packages/supabase` (confirm the CLI lists only `20261112090000_report_person.sql`)
- [ ] `npm run db:verify:reports --workspace=packages/supabase` with `SUPABASE_URL` and `SUPABASE_ANON_KEY` set; every check passes. Copy the `report_person receipt: {…}` JSON verbatim into `packages/supabase/fixtures/report-person-receipt.json` (one line plus a trailing newline).
- [ ] `npm run db:types:remote --workspace=packages/supabase`, then confirm `git diff packages/supabase/types/database.ts` adds `report_person` only.
- [ ] Commit: `chore(db): regenerate types and capture the report_person receipt`.

### Task 4 (orchestrator): decision-log entry and the plan

- [ ] Append to `docs/decisions/log.md`:

```markdown
## 2026-11-12 — A person is reported through report_person, never by user id

- **Status:** taken
- **Scope:** all
- **Context:** `report_content`'s `profile` branch takes the person's user id and requires a public profile. Connections and invite previews never hand a client a user id (standing rule), so a connected person or an inviter with a private profile could not be reported (#1008), and iOS needs reporting for App Store guideline 1.2 (#838).
- **Decision:** `report_person(p_reason, p_detail, p_connection_id | p_invite_token, p_remove_and_block)` resolves the person on the server from the route the reporter met them by and files an ordinary `profile` report, with `via` in the snapshot. `report_content` is unchanged and still files glyph and moment reports. No new target kind.
- **Why:** The admin queue, `admin_set_content_hidden('profile', …)` and the one-open-report index work as they are, and `report_content` is not re-emitted.
- **Consequences:** Android (#839) and web (#840) report people through `report_person`. A person reported from their invite and again after connecting is one open report. Remove-and-block calls `remove_connection(id, true)` in the same transaction; it is called, never re-emitted.
- **Supersedes / Superseded-by:** none.
- **Refs:** #838, #1008, `docs/superpowers/specs/2026-11-12-report-person-design.md`.
```

- [ ] Commit the log entry: `docs(core): person reports go through report_person`.

---

# Part 2: the report sheet (branch `feat/838-report-sheet-ios`)

### Task 5: Reasons, targets, receipt, errors

**Files:**
- Create: `apps/ios/Pebbles/Features/Report/ReportReason.swift`
- Create: `apps/ios/Pebbles/Features/Report/ReportTarget.swift`
- Create: `apps/ios/Pebbles/Features/Report/ReportReceipt.swift`
- Create: `apps/ios/Pebbles/Features/Report/ReportError.swift`
- Create: `apps/ios/PebblesTests/Fixtures/report-person-receipt.json` (copy of `packages/supabase/fixtures/report-person-receipt.json`, byte for byte: `cp`)
- Test: `apps/ios/PebblesTests/Features/Report/ReportModelsTests.swift`

- [ ] **Step 1: Copy the fixture**

```bash
cp packages/supabase/fixtures/report-person-receipt.json apps/ios/PebblesTests/Fixtures/report-person-receipt.json
```

- [ ] **Step 2: Write the failing tests**

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("Report models")
struct ReportModelsTests {
    /// Read through `#filePath`, like the other suites' fixtures, never from the bundle.
    private static let fixtures = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent()   // Report
        .deletingLastPathComponent()   // Features
        .deletingLastPathComponent()   // PebblesTests
        .appendingPathComponent("Fixtures")

    @Test func reasonRawValuesAreTheServerSlugs() {
        #expect(ReportReason.allCases.map(\.rawValue) == [
            "harassment", "hate", "impersonation", "sexual", "violence",
            "self_harm", "illegal", "spam", "other",
        ])
    }

    @Test func receiptDecodesTheHarnessPayload() throws {
        let data = try Data(contentsOf: Self.fixtures.appendingPathComponent("report-person-receipt.json"))
        let receipt = try JSONDecoder().decode(ReportReceipt.self, from: data)
        #expect(receipt.status == "open")
        #expect(receipt.createdAt.timeIntervalSince1970 > 1_700_000_000)
    }

    @Test func receiptAcceptsFractionalSeconds() throws {
        let json = #"{"id":"6f1c2a4e-8a39-4b52-9a0e-3f1d2c4b5a69","status":"open","created_at":"2026-11-12T09:00:00.123456+00:00"}"#
        let receipt = try JSONDecoder().decode(ReportReceipt.self, from: Data(json.utf8))
        #expect(receipt.createdAt == ISO8601Flexible.parse("2026-11-12T09:00:00.123456+00:00"))
    }

    @Test func receiptRejectsANonDate() {
        let json = #"{"id":"6f1c2a4e-8a39-4b52-9a0e-3f1d2c4b5a69","status":"open","created_at":"yesterday"}"#
        #expect(throws: DecodingError.self) {
            try JSONDecoder().decode(ReportReceipt.self, from: Data(json.utf8))
        }
    }

    @Test(arguments: [
        ("not_found", ReportError.notFound),
        ("P0001: cannot_report_own", ReportError.ownContent),
    ])
    func slugsMapToErrors(message: String, expected: ReportError) {
        let error = NSError(domain: "test", code: 0, userInfo: [NSLocalizedDescriptionKey: message])
        #expect(ReportError.from(error) == expected)
    }

    @Test func anythingElseIsOther() {
        let error = NSError(domain: "test", code: 0, userInfo: [NSLocalizedDescriptionKey: "timeout"])
        #expect(ReportError.from(error) == .other("timeout"))
    }

    @Test func onlyAConnectionOffersTheBlock() {
        #expect(ReportTarget.connection(id: UUID(), name: "Ana").offersBlock)
        #expect(!ReportTarget.invite(token: "t", name: "Ana").offersBlock)
        #expect(!ReportTarget.glyph(UUID()).offersBlock)
    }

    @Test func titlesNameThePersonWhenKnown() {
        #expect(ReportTarget.connection(id: UUID(), name: "Ana").title == "Report Ana")
        #expect(ReportTarget.invite(token: "t", name: nil).title == "Report this person")
        #expect(ReportTarget.connection(id: UUID(), name: "").title == "Report this person")
        #expect(ReportTarget.glyph(UUID()).title == "Report this glyph")
    }
}
```

Note: the title tests run in the test host's locale, English, which is the development language. If `title` comes back localized in another language on this Mac, check `defaults read -g AppleLanguages` and report it rather than weakening the test.

- [ ] **Step 3: Run the tests and watch them fail**

Run (from `apps/ios`): `xcodegen generate && xcodebuild test … -only-testing:PebblesTests/ReportModelsTests`
Expected: the build fails with `cannot find 'ReportReason' in scope`.

- [ ] **Step 4: Implement**

`ReportReason.swift`:

```swift
import Foundation

/// Why something is reported. Raw values are the server's slugs (the
/// `content_reports.reason` CHECK), so they never change; the order is the
/// order the sheet lists them in.
enum ReportReason: String, CaseIterable, Identifiable, Sendable {
    case harassment
    case hate
    case impersonation
    case sexual
    case violence
    case selfHarm = "self_harm"
    case illegal
    case spam
    case other

    var id: String { rawValue }

    var label: LocalizedStringResource {
        switch self {
        case .harassment: return "Harassment"
        case .hate: return "Hate"
        case .impersonation: return "Impersonation"
        case .sexual: return "Sexual content"
        case .violence: return "Violence"
        case .selfHarm: return "Self-harm"
        case .illegal: return "Illegal"
        case .spam: return "Spam"
        case .other: return "Other"
        }
    }
}
```

`ReportTarget.swift`:

```swift
import Foundation

/// What a report is about. A person is addressed by the route the reporter met
/// them by (their connection, or the invite they hold), never by user id: the
/// app never has one (standing rule), and `report_person` resolves it.
enum ReportTarget: Hashable, Sendable {
    case glyph(UUID)
    case connection(id: UUID, name: String?)
    case invite(token: String, name: String?)

    /// Only a connection can be removed and blocked in the same step.
    var offersBlock: Bool {
        if case .connection = self { return true }
        return false
    }

    var personName: String? {
        switch self {
        case .glyph: return nil
        case .connection(_, let name), .invite(_, let name):
            guard let name, !name.isEmpty else { return nil }
            return name
        }
    }

    var title: String {
        switch self {
        case .glyph:
            return String(localized: "Report this glyph")
        case .connection, .invite:
            if let name = personName { return String(localized: "Report \(name)") }
            return String(localized: "Report this person")
        }
    }

    var blockLabel: String {
        if let name = personName { return String(localized: "Also remove and block \(name)") }
        return String(localized: "Also remove and block this person")
    }
}
```

`ReportReceipt.swift`:

```swift
import Foundation

/// The projection both report RPCs return: `{id, status, created_at}`.
/// `created_at` is parsed tolerantly (standing timestamp rule), never left to
/// the ambient decoder's date strategy.
struct ReportReceipt: Decodable, Equatable, Sendable {
    let id: UUID
    let status: String
    let createdAt: Date

    enum CodingKeys: String, CodingKey {
        case id
        case status
        case createdAt = "created_at"
    }

    init(id: UUID, status: String, createdAt: Date) {
        self.id = id
        self.status = status
        self.createdAt = createdAt
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(UUID.self, forKey: .id)
        status = try container.decode(String.self, forKey: .status)
        let raw = try container.decode(String.self, forKey: .createdAt)
        guard let date = ISO8601Flexible.parse(raw) else {
            throw DecodingError.dataCorruptedError(
                forKey: .createdAt, in: container, debugDescription: "not ISO-8601: \(raw)")
        }
        createdAt = date
    }
}
```

`ReportError.swift`:

```swift
import Foundation

/// Postgres raises short slugs and PostgREST delivers them inside the message,
/// so the mapping is a substring match: the contract `ConnectionsError` uses.
enum ReportError: Error, Equatable {
    case notFound
    case ownContent
    case other(String)

    static func from(_ error: Error) -> ReportError {
        if let error = error as? ReportError { return error }
        let message = error.localizedDescription
        let lowered = message.lowercased()
        if lowered.contains("cannot_report_own") { return .ownContent }
        if lowered.contains("not_found") { return .notFound }
        return .other(message)
    }

    var message: String {
        switch self {
        case .notFound: return String(localized: "This is no longer available.")
        case .ownContent: return String(localized: "You can't report your own.")
        case .other: return String(localized: "Couldn't send the report. Try again.")
        }
    }
}
```

- [ ] **Step 5: Run the tests and watch them pass**

Run the same command. Expected: `ReportModelsTests` passes (all cases).

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Report apps/ios/PebblesTests/Features/Report apps/ios/PebblesTests/Fixtures/report-person-receipt.json
git commit -m "feat(ios): report reasons, targets, receipt and errors"
```

### Task 6: `ReportRequest` and `ReportService`

**Files:**
- Create: `apps/ios/Pebbles/Features/Report/ReportService.swift`
- Test: `apps/ios/PebblesTests/Features/Report/ReportRequestTests.swift`

The pattern to follow is `GlyphMarketService`: a struct that a view builds from `SupabaseService`. The params are `Encodable` structs whose `nil` optionals are omitted, so the server applies its defaults. `ReportRequest` is the pure, testable half.

- [ ] **Step 1: Write the failing tests**

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("Report request")
struct ReportRequestTests {
    private func json(_ value: some Encodable) throws -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        return String(decoding: try encoder.encode(value), as: UTF8.self)
    }

    private let id = UUID(uuidString: "6F1C2A4E-8A39-4B52-9A0E-3F1D2C4B5A69")!

    @Test func aGlyphGoesThroughReportContent() throws {
        let request = ReportRequest.make(target: .glyph(id), reason: .hate, detail: nil, removeAndBlock: true)
        guard case .content(let params) = request else { Issue.record("expected report_content"); return }
        #expect(request.function == "report_content")
        #expect(try json(params) ==
            #"{"p_reason":"hate","p_target_id":"6F1C2A4E-8A39-4B52-9A0E-3F1D2C4B5A69","p_target_kind":"glyph"}"#)
    }

    @Test func aConnectionCarriesTheBlockFlag() throws {
        let request = ReportRequest.make(
            target: .connection(id: id, name: "Ana"), reason: .harassment, detail: "rude", removeAndBlock: true)
        guard case .person(let params) = request else { Issue.record("expected report_person"); return }
        #expect(request.function == "report_person")
        #expect(try json(params) ==
            #"{"p_connection_id":"6F1C2A4E-8A39-4B52-9A0E-3F1D2C4B5A69","p_detail":"rude","p_reason":"harassment","p_remove_and_block":true}"#)
    }

    @Test func anInviteNeverBlocks() throws {
        let request = ReportRequest.make(
            target: .invite(token: "tok", name: nil), reason: .spam, detail: nil, removeAndBlock: true)
        guard case .person(let params) = request else { Issue.record("expected report_person"); return }
        #expect(try json(params) ==
            #"{"p_invite_token":"tok","p_reason":"spam","p_remove_and_block":false}"#)
    }
}
```

- [ ] **Step 2: Run the tests and watch them fail**

`-only-testing:PebblesTests/ReportRequestTests`. Expected: the build fails with `cannot find 'ReportRequest'`.

- [ ] **Step 3: Implement `ReportService.swift`**

```swift
import Foundation

/// Which RPC a report goes through, with its wire params. Pure, so the routing
/// (glyph → `report_content`, person → `report_person`, invites never block)
/// is tested without a network.
enum ReportRequest {
    case content(ReportContentParams)
    case person(ReportPersonParams)

    var function: String {
        switch self {
        case .content: return "report_content"
        case .person: return "report_person"
        }
    }

    static func make(
        target: ReportTarget,
        reason: ReportReason,
        detail: String?,
        removeAndBlock: Bool
    ) -> ReportRequest {
        switch target {
        case .glyph(let id):
            return .content(ReportContentParams(
                pTargetKind: "glyph", pTargetId: id.uuidString, pReason: reason.rawValue, pDetail: detail))
        case .connection(let id, _):
            return .person(ReportPersonParams(
                pReason: reason.rawValue, pDetail: detail, pConnectionId: id.uuidString,
                pInviteToken: nil, pRemoveAndBlock: removeAndBlock))
        case .invite(let token, _):
            return .person(ReportPersonParams(
                pReason: reason.rawValue, pDetail: detail, pConnectionId: nil,
                pInviteToken: token, pRemoveAndBlock: false))
        }
    }
}

/// `nil` optionals are omitted by the synthesized encoder, so the RPC's
/// defaults apply.
struct ReportContentParams: Encodable {
    let pTargetKind: String
    let pTargetId: String
    let pReason: String
    let pDetail: String?

    enum CodingKeys: String, CodingKey {
        case pTargetKind = "p_target_kind"
        case pTargetId = "p_target_id"
        case pReason = "p_reason"
        case pDetail = "p_detail"
    }
}

struct ReportPersonParams: Encodable {
    let pReason: String
    let pDetail: String?
    let pConnectionId: String?
    let pInviteToken: String?
    let pRemoveAndBlock: Bool

    enum CodingKeys: String, CodingKey {
        case pReason = "p_reason"
        case pDetail = "p_detail"
        case pConnectionId = "p_connection_id"
        case pInviteToken = "p_invite_token"
        case pRemoveAndBlock = "p_remove_and_block"
    }
}

/// Files a report. Both RPCs return scalar `jsonb`, so the receipt decodes
/// straight from the body (no `.single()`).
struct ReportService {
    let supabase: SupabaseService

    func send(
        _ target: ReportTarget,
        reason: ReportReason,
        detail: String?,
        removeAndBlock: Bool
    ) async throws -> ReportReceipt {
        let request = ReportRequest.make(
            target: target, reason: reason, detail: detail, removeAndBlock: removeAndBlock)
        switch request {
        case .content(let params):
            return try await supabase.client.rpc(request.function, params: params).execute().value
        case .person(let params):
            return try await supabase.client.rpc(request.function, params: params).execute().value
        }
    }
}
```

- [ ] **Step 4: Run the tests and watch them pass**

Same command. Expected: `ReportRequestTests` passes. If the JSON string differs only in how `/` or unicode is escaped, fix the expectation to the encoder's real output and say so in your report.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Report/ReportService.swift apps/ios/PebblesTests/Features/Report/ReportRequestTests.swift
git commit -m "feat(ios): route reports to report_content or report_person"
```

### Task 7: `ReportSheetModel`

**Files:**
- Create: `apps/ios/Pebbles/Features/Report/ReportSheetModel.swift`
- Test: `apps/ios/PebblesTests/Features/Report/ReportSheetModelTests.swift`

The model holds no service: `submit(using:)` takes the send function, so tests pass a fake and the view passes `ReportService(supabase:).send`.

- [ ] **Step 1: Write the failing tests**

```swift
import Foundation
import Testing
@testable import Pebbles

@MainActor
@Suite("Report sheet model")
struct ReportSheetModelTests {
    private let receipt = ReportReceipt(id: UUID(), status: "open", createdAt: Date(timeIntervalSince1970: 1_800_000_000))

    @Test func cannotSendWithoutAReason() {
        let model = ReportSheetModel(target: .glyph(UUID()))
        #expect(!model.canSend)
        model.reason = .spam
        #expect(model.canSend)
    }

    @Test func aSuccessfulSendLandsInSent() async {
        let model = ReportSheetModel(target: .glyph(UUID()))
        model.reason = .hate
        await model.submit { _, _, _, _ in receipt }
        #expect(model.phase == .sent)
        #expect(!model.canSend)
        #expect(!model.didBlock)
    }

    @Test func aDuplicateIsStillASuccess() async {
        let model = ReportSheetModel(target: .invite(token: "t", name: nil))
        model.reason = .spam
        await model.submit { _, _, _, _ in receipt }
        let again = ReportSheetModel(target: .invite(token: "t", name: nil))
        again.reason = .spam
        await again.submit { _, _, _, _ in receipt }   // the server returns the standing report
        #expect(again.phase == .sent)
    }

    @Test func passesTrimmedDetailOrNil() async {
        let model = ReportSheetModel(target: .glyph(UUID()))
        model.reason = .other
        model.detail = "   "
        var sent: String?? = .none
        await model.submit { _, _, detail, _ in sent = .some(detail); return receipt }
        #expect(sent == .some(nil))

        let second = ReportSheetModel(target: .glyph(UUID()))
        second.reason = .other
        second.detail = "  look at the name  "
        await second.submit { _, _, detail, _ in sent = .some(detail); return receipt }
        #expect(sent == .some("look at the name"))
    }

    @Test func theBlockFlagIsSentOnlyForAConnection() async {
        let connection = ReportSheetModel(target: .connection(id: UUID(), name: "Ana"))
        connection.reason = .harassment
        #expect(connection.removeAndBlock)   // on by default
        var flag: Bool?
        await connection.submit { _, _, _, block in flag = block; return receipt }
        #expect(flag == true)
        #expect(connection.didBlock)

        let invite = ReportSheetModel(target: .invite(token: "t", name: "Ana"))
        invite.reason = .harassment
        await invite.submit { _, _, _, block in flag = block; return receipt }
        #expect(flag == false)
        #expect(!invite.didBlock)
    }

    @Test func turningTheToggleOffDoesNotBlock() async {
        let model = ReportSheetModel(target: .connection(id: UUID(), name: nil))
        model.reason = .spam
        model.removeAndBlock = false
        var flag: Bool?
        await model.submit { _, _, _, block in flag = block; return receipt }
        #expect(flag == false)
        #expect(!model.didBlock)
    }

    @Test func aFailureShowsItsMessageAndAllowsRetry() async {
        let model = ReportSheetModel(target: .glyph(UUID()))
        model.reason = .spam
        await model.submit { _, _, _, _ in
            throw NSError(domain: "t", code: 0, userInfo: [NSLocalizedDescriptionKey: "not_found"])
        }
        #expect(model.phase == .failed(ReportError.notFound.message))
        #expect(model.canSend)
    }

    @Test func detailIsCappedAtTheServerLimit() {
        let model = ReportSheetModel(target: .glyph(UUID()))
        model.detail = String(repeating: "x", count: 1_200)
        model.clampDetail()
        #expect(model.detail.count == ReportSheetModel.detailLimit)
    }
}
```

- [ ] **Step 2: Run the tests and watch them fail**

`-only-testing:PebblesTests/ReportSheetModelTests`. Expected: the build fails with `cannot find 'ReportSheetModel'`.

- [ ] **Step 3: Implement**

```swift
import Foundation
import Observation
import os

/// The report sheet's state. All logic lives here; `ReportSheet` only draws it.
@Observable
@MainActor
final class ReportSheetModel {
    enum Phase: Equatable {
        case editing
        case sending
        case sent
        case failed(String)
    }

    typealias Send = (ReportTarget, ReportReason, String?, Bool) async throws -> ReportReceipt

    /// `content_reports.detail`'s CHECK.
    static let detailLimit = 1_000

    let target: ReportTarget
    var reason: ReportReason?
    var detail = ""
    /// Only read for a connection; on by default (design decision 3).
    var removeAndBlock = true
    private(set) var phase: Phase = .editing
    /// True once a report with remove-and-block succeeded.
    private(set) var didBlock = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "report")

    init(target: ReportTarget) {
        self.target = target
    }

    var canSend: Bool {
        reason != nil && phase != .sending && phase != .sent
    }

    func clampDetail() {
        if detail.count > Self.detailLimit {
            detail = String(detail.prefix(Self.detailLimit))
        }
    }

    func submit(using send: Send) async {
        guard canSend, let reason else { return }
        phase = .sending
        let trimmed = detail.trimmingCharacters(in: .whitespacesAndNewlines)
        let block = target.offersBlock && removeAndBlock
        do {
            _ = try await send(target, reason, trimmed.isEmpty ? nil : trimmed, block)
            didBlock = block
            phase = .sent
        } catch {
            logger.error("report failed: \(error.localizedDescription, privacy: .private)")
            phase = .failed(ReportError.from(error).message)
        }
    }
}
```

- [ ] **Step 4: Run the tests and watch them pass**

Same command. Expected: `ReportSheetModelTests` passes.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Report/ReportSheetModel.swift apps/ios/PebblesTests/Features/Report/ReportSheetModelTests.swift
git commit -m "feat(ios): report sheet model"
```

### Task 8: `ReportSheet` and its strings

**Files:**
- Create: `apps/ios/Pebbles/Features/Report/ReportSheet.swift`
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`

Reuse the app's own pieces: `pebblesList()`, `pebblesListRow(position:)`, `pebblesRowPosition(index:count:)`, `.pebblesSectionHeader()`, `PebbleToolbarButton`, `pebblesToolbarTitle(_: String)`, `pebblesScreen()`, `PbblsGlyphView`, `Color.pebbles.*`. Look at `Features/Profile/Sheets/SettingsSheet.swift` for a sheet built from these.

- [ ] **Step 1: Write the view**

```swift
import SwiftUI

/// Report a person or a glyph (App Store guideline 1.2, #838). A native grouped
/// list built from the app's own list chrome: one reason, optional details,
/// and, for a connection, remove-and-block in the same step.
struct ReportSheet: View {
    let target: ReportTarget
    /// Called once the report is filed; `true` when the connection was also
    /// removed and blocked, so the caller can drop it from its list.
    var onSent: (_ blocked: Bool) -> Void = { _ in }

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss
    @State private var model: ReportSheetModel

    init(target: ReportTarget, onSent: @escaping (_ blocked: Bool) -> Void = { _ in }) {
        self.target = target
        self.onSent = onSent
        _model = State(initialValue: ReportSheetModel(target: target))
    }

    var body: some View {
        NavigationStack {
            content
                .pebblesToolbarTitle(target.title)
                .toolbar { toolbar }
                .pebblesScreen()
        }
        .interactiveDismissDisabled(model.phase == .sending)
        .onChange(of: model.phase) { _, phase in
            if phase == .sent { onSent(model.didBlock) }
        }
    }

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        if model.phase == .sent {
            ToolbarItem(placement: .confirmationAction) {
                PebbleToolbarButton("Done") { dismiss() }
            }
        } else {
            ToolbarItem(placement: .cancellationAction) {
                PebbleToolbarButton("Cancel") { dismiss() }
                    .disabled(model.phase == .sending)
            }
            ToolbarItem(placement: .confirmationAction) {
                PebbleToolbarButton(action: send) {
                    if model.phase == .sending {
                        ProgressView()
                    } else {
                        Text("Send report")
                    }
                }
                .disabled(!model.canSend)
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        if model.phase == .sent {
            ContentUnavailableView {
                Label {
                    Text("Thanks. We'll look at it.")
                } icon: {
                    PbblsGlyphView(.checkCircleDot, .hero)
                }
            }
        } else {
            form
        }
    }

    private var form: some View {
        @Bindable var model = model
        return List {
            Section {
                ForEach(Array(ReportReason.allCases.enumerated()), id: \.element) { index, reason in
                    Button {
                        model.reason = reason
                    } label: {
                        HStack {
                            Text(reason.label)
                                .foregroundStyle(Color.pebbles.onSurface)
                            Spacer(minLength: 0)
                            if model.reason == reason {
                                Image(systemName: "checkmark")
                                    .foregroundStyle(Color.pebbles.primary)
                            }
                        }
                        .contentShape(Rectangle())
                    }
                    .accessibilityAddTraits(model.reason == reason ? .isSelected : [])
                    .pebblesListRow(position: pebblesRowPosition(index: index, count: ReportReason.allCases.count))
                }
            } header: {
                Text("Why are you reporting?").pebblesSectionHeader()
            }

            Section {
                TextField("What happened?", text: $model.detail, axis: .vertical)
                    .lineLimit(3...8)
                    .onChange(of: model.detail) { _, _ in model.clampDetail() }
                    .pebblesListRow(position: .only)
            } header: {
                Text("Add details (optional)").pebblesSectionHeader()
            }

            if target.offersBlock {
                Section {
                    Toggle(isOn: $model.removeAndBlock) {
                        Text(target.blockLabel)
                    }
                    .tint(Color.pebbles.primary)
                    .pebblesListRow(position: .only)
                } footer: {
                    Text("Blocking also stops them joining again through your invite link.")
                }
            }

            if case .failed(let message) = model.phase {
                Section {
                    Text(message)
                        .foregroundStyle(Color.pebbles.error)
                        .pebblesListRow(position: .only)
                }
            }
        }
        .pebblesList()
        .disabled(model.phase == .sending)
    }

    private func send() {
        let service = ReportService(supabase: supabase)
        Task { await model.submit(using: service.send) }
    }
}

#Preview("Connection") {
    ReportSheet(target: .connection(id: UUID(), name: "Ana"))
        .environment(SupabaseService())
}
```

If `#Preview` cannot construct `SupabaseService()` without config, delete the preview block rather than adding scaffolding.

- [ ] **Step 2: Build**

Run the full `xcodebuild build` command. Expected: `** BUILD SUCCEEDED **`. Fix compile errors in this file only. If `@Bindable var model = model` inside a computed property is rejected, move `form` into a private subview `ReportForm` that takes `@Bindable var model: ReportSheetModel`.

- [ ] **Step 3: Add the strings, EN and FR**

Add each key below only if it is not already in the catalog (check with python first; `Done`, `Cancel`, `What happened?` and the "Blocking also stops…" footer already exist). Each entry has the shape of the existing `"Remove and block"` entry: `"extractionState" : "manual"`, with an `en` and an `fr` `stringUnit`, both `"state" : "translated"`. Insert each at its sorted position by key, as text. Interpolated keys use `%@`.

| Key (EN value identical) | FR |
|---|---|
| `Add details (optional)` | `Ajouter des détails (facultatif)` |
| `Also remove and block %@` | `Retirer et bloquer aussi %@` |
| `Also remove and block this person` | `Retirer et bloquer aussi cette personne` |
| `Couldn't send the report. Try again.` | `Impossible d'envoyer le signalement. Réessaie.` |
| `Harassment` | `Harcèlement` |
| `Hate` | `Haine` |
| `Illegal` | `Illégal` |
| `Impersonation` | `Usurpation d'identité` |
| `Other` | `Autre` |
| `Report %@` | `Signaler %@` |
| `Report this glyph` | `Signaler ce glyphe` |
| `Report this person` | `Signaler cette personne` |
| `Self-harm` | `Automutilation` |
| `Send report` | `Envoyer le signalement` |
| `Sexual content` | `Contenu sexuel` |
| `Spam` | `Spam` |
| `Thanks. We'll look at it.` | `Merci. On va regarder.` |
| `This is no longer available.` | `Ce n'est plus disponible.` |
| `Violence` | `Violence` |
| `Why are you reporting?` | `Pourquoi tu signales ?` |
| `You can't report your own.` | `Tu ne peux pas signaler ce qui est à toi.` |

The FR register is "tu", matching the catalog. Typographic apostrophes: copy whatever the neighbouring FR entries use (`’` or `'`). Check one first and stay consistent.

Validate: `python3 -c "import json;d=json.load(open('apps/ios/Pebbles/Resources/Localizable.xcstrings'))['strings'];print(all(k in d for k in ['Report %@','Why are you reporting?','Send report']))"` prints `True`, and `git diff --stat` shows insertions only for that file.

- [ ] **Step 4: Build, run the three Report suites, lint**

```bash
xcodebuild build … | tail -5        # BUILD SUCCEEDED
xcodebuild test … -only-testing:PebblesTests/ReportModelsTests -only-testing:PebblesTests/ReportRequestTests -only-testing:PebblesTests/ReportSheetModelTests
swiftlint lint --quiet Pebbles/Features/Report PebblesTests/Features/Report
```

Expected: build succeeds, all three suites pass, swiftlint prints nothing.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Report/ReportSheet.swift apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ios): report sheet"
```

---

# Part 3: entry points (branch `feat/838-report-entries-ios`)

### Task 9: Report from the Connections list

**Files:**
- Modify: `apps/ios/Pebbles/Features/Connections/ConnectionsListView.swift`
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings` (`Report` → FR `Signaler`, if absent)

- [ ] **Step 1: Add the state** next to `pendingRemoval`:

```swift
    @State private var reporting: Connection?
```

- [ ] **Step 2: Add the menu item above Remove.** Replace the `.pebblesContextMenu([...])` argument with:

```swift
                        .pebblesContextMenu([
                            PebblesMenuItem(title: "Report", icon: .circleDotExclamation) {
                                reporting = row
                            },
                            PebblesMenuItem(title: "Remove", icon: .remove, isDestructive: true) {
                                pendingRemoval = row
                            }
                        ])
```

- [ ] **Step 3: Present the sheet.** After `.sheet(isPresented: $isPresentingInvite) { InviteSheet() }`, add:

```swift
            .sheet(item: $reporting) { row in
                ReportSheet(target: .connection(id: row.connectionId, name: row.peer.displayName)) { blocked in
                    // The server already removed it in the same transaction.
                    if blocked { rows.removeAll { $0.id == row.id } }
                }
            }
```

- [ ] **Step 4: Add the `Report` string** (EN `Report`, FR `Signaler`) the same way as Task 8 Step 3, if absent.

- [ ] **Step 5: Build and lint**

`xcodebuild build …` → `BUILD SUCCEEDED`; `swiftlint lint --quiet Pebbles/Features/Connections` → no output.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Connections/ConnectionsListView.swift apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ios): report a connection from its context menu"
```

### Task 10: Report from the glyph drawer

**Files:**
- Modify: `apps/ios/Pebbles/Features/Glyph/Views/GlyphDetailDrawer.swift`

A glyph is reportable when it is listed on the market (`item.price > 0`; `price` is 0 when there is no approved and listed submission) and the user does not own it (`!isOwned`). The server's `cannot_report_own` covers the rest.

- [ ] **Step 1: Add the state** next to `errorText`:

```swift
    @State private var isReporting = false
```

- [ ] **Step 2: Add the quiet button** as the last child of the drawer's `VStack(spacing: Spacing.lg)`, after the `if let errorText { … }` block:

```swift
                    if item.price > 0 && !isOwned {
                        Button("Report this glyph") { isReporting = true }
                            .font(.footnote)
                            .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    }
```

- [ ] **Step 3: Present the sheet.** After `.pebblesScreen()` inside the `NavigationStack`, add:

```swift
            .sheet(isPresented: $isReporting) {
                ReportSheet(target: .glyph(item.glyph.id))
            }
```

- [ ] **Step 4: Build and lint**: as in Task 9.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Glyph/Views/GlyphDetailDrawer.swift
git commit -m "feat(ios): report a market glyph from its drawer"
```

### Task 11: Report from the invite sheet

**Files:**
- Modify: `apps/ios/Pebbles/Features/Connections/InviteAcceptSheet.swift`

After a send, the sheet closes without accepting. `RootView.openWhatWasWaiting` already clears `connections.pendingInviteToken` when it presents this sheet. Clearing it again here is deliberate: a report must never leave an invite parked.

- [ ] **Step 1: Add the state** next to `accepted`:

```swift
    @State private var isReporting = false
    @State private var didReport = false
```

- [ ] **Step 2: Add the quiet button** in the `else if let preview, preview.status == .valid` branch, right after the Connect button's `.disabled(isAccepting)`:

```swift
                Button("Report") { isReporting = true }
                    .font(.footnote)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .disabled(isAccepting)
```

- [ ] **Step 3: Present the sheet and close after a report.** On the `NavigationStack`, after `.task { await load() }`, add:

```swift
        .sheet(isPresented: $isReporting, onDismiss: {
            guard didReport else { return }
            connections.pendingInviteToken = nil
            dismiss()
        }) {
            ReportSheet(target: .invite(token: token, name: preview?.inviter?.displayName)) { _ in
                didReport = true
            }
        }
```

- [ ] **Step 4: Build and lint**: as in Task 9.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Connections/InviteAcceptSheet.swift
git commit -m "feat(ios): report an inviter from the invite sheet"
```

### Task 12 (orchestrator): simulator pass, Arkaik, PRs

- [ ] Simulator pass with a throwaway account the maintainer signs in to: report a connection with the toggle on (row leaves the list), report one with the toggle off, report a market glyph, report from an invite link (the sheet closes, no connection made). Screenshot each sheet and each entry point.
- [ ] Arkaik: Connections, glyph detail and invite accept views → `development` when part 2 starts. Each gets a report acceptance, and part 3 records the screenshots. Never set `live`.
- [ ] `gh stack submit --auto`, then edit each PR: title, `Resolves #1008` (part 1) / `Part of #838` (part 2) / `Resolves #838` (part 3), key files, `Data safety: no change`, labels and milestone inherited from the issues (confirm with the maintainer), `no-lab-note`. Never write a Kritik finding id in a PR body except in part 3, which resolves the iOS report finding; look its id up in Kritik at PR time.
- [ ] Build-journal pebble once the stack is up.
