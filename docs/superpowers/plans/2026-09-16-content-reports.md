# content_reports Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the `content_reports` database primitive — table, insert RPC, admin queue with takedown, purge extension, two harnesses — so every client can ship a report button (Kritik `F-2026-08-PLT-supabase-01`, issue #831).

**Architecture:** One table with a polymorphic target and no RLS policies at all; three `security definer` RPCs are the only access path. `report_content` is `authenticated`-only and gates on whether the reporter can see the target. `resolve_content_report` performs the takedown in the same transaction as the verdict. A second migration re-emits `purge_account` whole per the standing rule.

**Tech Stack:** Postgres (Supabase), plpgsql, Deno harnesses against the **linked remote project** (this repo does not use local Docker — see `docs/decisions/log.md`).

**Spec:** `docs/superpowers/specs/2026-09-16-content-reports-design.md`

---

## Read this before Task 1

**Migrations are pushed to the REMOTE linked project**, not a local stack. `npm run db:push --workspace=packages/supabase` applies them. A pushed migration file must never be edited afterwards — a correction is a new migration. That is why Tasks 2 and 3 each write a *complete* migration file before pushing once.

**Type regeneration uses the `:remote` variant.** `npm run db:types --workspace=packages/supabase` targets `--local` (Docker), and on failure it truncates `types/database.ts` to an empty file. Always use `db:types:remote`.

**Harnesses need env.** All of them read `SUPABASE_URL` / `SUPABASE_ANON_KEY` (and the purge one also `SUPABASE_SERVICE_ROLE_KEY`) from the environment. Load them with `set -a; . ./.env; set +a` from the repo root.

## File structure

| File | Responsibility |
|---|---|
| `packages/supabase/supabase/migrations/20260916090000_content_reports.sql` | Create: table, indexes, RLS posture, the three RPCs, grants |
| `packages/supabase/supabase/migrations/20260916090100_purge_account_content_reports.sql` | Create: whole re-emission of `purge_account` with the two new statements |
| `packages/supabase/scripts/verify-content-reports.ts` | Create: anon-only harness for the report path — the CI-gated contract |
| `packages/supabase/scripts/verify-account-purge.ts` | Modify: purge assertions + the service-role-only admin/listed-glyph assertions |
| `packages/supabase/package.json` | Modify: `db:verify:reports` script, add to the `db:verify` chain |
| `.github/workflows/supabase.yml` | Modify: a sixth harness step |
| `packages/supabase/types/database.ts` | Regenerate via `db:types:remote` |

---

### Task 1: Write the failing harness for the report path

This is the test, and it is written first. Every assertion in it is behavioural — an anon-only client cannot read `content_reports` back (that is the point of the RLS posture), so correctness of `target_user_id` and `target_snapshot` is proven in Task 5 under the service role instead.

**Files:**
- Create: `packages/supabase/scripts/verify-content-reports.ts`

> **Correction applied 2026-09-16.** The first draft of this task seeded the unlisted glyph with a direct `.from("glyph_submissions").insert(...)`. That fails: `glyph_submissions` has a SELECT policy and nothing else (`20260630003348_glyph_marketplace.sql:56`), so every client write goes through the `submit_glyph` definer RPC — the root `AGENTS.md` rule about preferring RPCs, enforced by RLS. The seed below uses the RPC. A harness that aborts in its own setup is not a red test; it fails identically before and after the migration lands.

- [ ] **Step 1: Write the harness**

```typescript
#!/usr/bin/env -S deno run --allow-env --allow-net
/**
 * Acceptance test for the UGC report primitive (#831, M56) — runs against the
 * REMOTE project.
 *
 * `report_content` is the schema's report path: an `authenticated`-only
 * `security definer` insert that resolves the target's owner server-side and
 * refuses targets the reporter cannot see. Three things need proving end to
 * end, and none can be proven from inside a single client's test suite:
 *
 *   1. The visibility gate holds — a secret pebble, an unlisted glyph and a
 *      nonexistent uuid are all equally unreportable.
 *   2. Invisible and nonexistent are INDISTINGUISHABLE. Without that, the RPC
 *      is an existence oracle: feed it uuids, learn which secret pebbles
 *      exist. This is the same choice get_shared_pebble makes.
 *   3. content_reports is opaque. RLS is enabled with NO policies, so the
 *      reporter who just filed cannot read their own report back — a readable
 *      table is a map of who reported whom.
 *
 * Run:
 *   SUPABASE_URL=... SUPABASE_ANON_KEY=... \
 *     deno run --allow-env --allow-net packages/supabase/scripts/verify-content-reports.ts
 *
 * Deliberately needs NO service-role key: it signs up throwaway users and
 * deletes them through the real `delete-account` edge function, so it is
 * runnable by anyone who can run the app, and it can sit in the CI gate.
 * Cleanup runs even on failure. Exits non-zero if any assertion fails.
 *
 * WHAT THIS HARNESS STRUCTURALLY CANNOT PROVE (covered in
 * verify-account-purge.ts, which holds the service role):
 *   - that target_user_id and target_snapshot are resolved correctly — both
 *     are unreadable from here by construction
 *   - reporting a LISTED glyph — a submission is `pending` until an admin
 *     approves it, and this harness cannot mint an admin past
 *     profiles_privileged_guard (20260902090000)
 *   - the positive admin path (queue read, takedown dispatch)
 */

const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY");

if (!SUPABASE_URL || !ANON_KEY) {
  console.error("SUPABASE_URL and SUPABASE_ANON_KEY must be set");
  Deno.exit(2);
}

import { createClient, type SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

let passed = 0;
let failed = 0;
function check(name: string, ok: boolean, detail?: string) {
  if (ok) {
    passed += 1;
    console.log(`✓ ${name}`);
  } else {
    failed += 1;
    console.log(`✗ ${name}${detail ? ` — ${detail}` : ""}`);
  }
}

const runId = crypto.randomUUID().slice(0, 8);
const password = `Reports-${crypto.randomUUID()}`;

type TestUser = { client: SupabaseClient; id: string; token: string };

async function signUp(label: string): Promise<TestUser> {
  const client = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
  const email = `reports-verify-${label}-${runId}@example.test`;
  const { data, error } = await client.auth.signUp({ email, password });
  if (error || !data.session || !data.user) {
    throw new Error(`signUp ${label}: ${error?.message ?? "no session (email confirmations on?)"}`);
  }
  return { client, id: data.user.id, token: data.session.access_token };
}

/** The real client teardown path — no service role needed. */
async function deleteAccount(token: string): Promise<Response> {
  return await fetch(`${SUPABASE_URL}/functions/v1/delete-account`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      apikey: ANON_KEY!,
      "Content-Type": "application/json",
    },
  });
}

let owner: TestUser | null = null;
let reporter: TestUser | null = null;

try {
  owner = await signUp("owner");
  reporter = await signUp("reporter");
  console.log(`owner=${owner.id} reporter=${reporter.id}\n`);
  const o = owner.client;
  const r = reporter.client;

  const { data: emotion } = await o.from("emotions").select("id").limit(1).single();
  if (!emotion) throw new Error("reference data missing (emotion)");

  const base = {
    happened_at: "2026-09-01T12:00:00Z",
    intensity: 2,
    positiveness: 1,
    emotion_id: emotion.id,
  };

  // ---------------------------------------------------------------------------
  // 0. Seed the owner's reportable and unreportable surfaces.
  // ---------------------------------------------------------------------------
  const { data: publicPebbleId, error: pubErr } = await o.rpc("create_pebble", {
    payload: { ...base, name: `public ${runId}`, description: "reportable", visibility: "public" },
  });
  if (pubErr || !publicPebbleId) throw new Error(`create public pebble: ${pubErr?.message}`);

  const { data: secretPebbleId, error: secErr } = await o.rpc("create_pebble", {
    payload: { ...base, name: `secret ${runId}`, visibility: "secret" },
  });
  if (secErr || !secretPebbleId) throw new Error(`create secret pebble: ${secErr?.message}`);

  const handle = `rep${runId}`;
  const { error: handleErr } = await o.rpc("set_handle", { p_handle: handle });
  if (handleErr) throw new Error(`set_handle: ${handleErr.message}`);
  const { error: pubProfErr } = await o.from("profiles")
    .update({ public_profile: true }).eq("user_id", owner.id);
  if (pubProfErr) throw new Error(`publish profile: ${pubProfErr.message}`);

  // A glyph submitted but NOT approved — the unlisted-target negative case.
  const { data: glyph, error: glyphErr } = await o.from("glyphs")
    .insert({ user_id: owner.id, name: `g ${runId}`, strokes: [], view_box: "0 0 100 100" })
    .select("id").single();
  if (glyphErr || !glyph) throw new Error(`insert glyph: ${glyphErr?.message}`);
  // Via the RPC, not a direct insert: glyph_submissions carries a SELECT
  // policy only (20260630003348 §3), so every client write goes through
  // submit_glyph. It inserts with status defaulting to 'pending' — which is
  // exactly the unlisted state this fixture needs.
  const { error: subErr } = await o.rpc("submit_glyph", { p_glyph_id: glyph.id });
  if (subErr) throw new Error(`submit_glyph: ${subErr.message}`);

  // ---------------------------------------------------------------------------
  // 1. The happy path: a stranger reports a public pebble and a public profile.
  // ---------------------------------------------------------------------------
  const { data: filed, error: fileErr } = await r.rpc("report_content", {
    p_target_kind: "pebble",
    p_target_id: publicPebbleId,
    p_reason: "harassment",
    p_detail: "verify run",
  });
  const filedRow = filed as { id?: string; status?: string; created_at?: string } | null;
  check("report_content on a public pebble succeeds",
    !fileErr && !!filedRow?.id && filedRow?.status === "open", fileErr?.message);
  check("report_content emits whole-second UTC created_at",
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/.test(filedRow?.created_at ?? ""),
    filedRow?.created_at);

  const { data: profileFiled, error: profileErr } = await r.rpc("report_content", {
    p_target_kind: "profile",
    p_target_id: owner.id,
    p_reason: "impersonation",
  });
  check("report_content on a public profile succeeds",
    !profileErr && !!(profileFiled as { id?: string } | null)?.id, profileErr?.message);

  // ---------------------------------------------------------------------------
  // 2. Idempotency: filing again returns the STANDING report, not a duplicate
  //    and not an error slug the client would have to special-case.
  // ---------------------------------------------------------------------------
  const { data: refiled, error: refileErr } = await r.rpc("report_content", {
    p_target_kind: "pebble",
    p_target_id: publicPebbleId,
    p_reason: "spam",
    p_detail: "second attempt",
  });
  check("a second file returns the standing report, not an error",
    !refileErr && (refiled as { id?: string } | null)?.id === filedRow?.id,
    refileErr ? refileErr.message : JSON.stringify(refiled));

  // ---------------------------------------------------------------------------
  // 3. The table is opaque — RLS enabled with no policies.
  // ---------------------------------------------------------------------------
  const { data: peek, error: peekErr } = await r.from("content_reports").select("id");
  check("the reporter cannot read their own report back",
    !peekErr && (peek?.length ?? -1) === 0,
    peekErr ? peekErr.message : `rows=${peek?.length}`);

  // ---------------------------------------------------------------------------
  // 4. The visibility gate, and the enumeration-resistance property: a secret
  //    pebble, an unlisted glyph and a random uuid all fail IDENTICALLY.
  // ---------------------------------------------------------------------------
  const { error: secretErr } = await r.rpc("report_content", {
    p_target_kind: "pebble", p_target_id: secretPebbleId, p_reason: "spam",
  });
  check("a secret pebble is not reportable", !!secretErr && secretErr.message.includes("not_found"),
    secretErr?.message);

  const { error: ghostErr } = await r.rpc("report_content", {
    p_target_kind: "pebble", p_target_id: crypto.randomUUID(), p_reason: "spam",
  });
  check("a nonexistent pebble is not reportable", !!ghostErr && ghostErr.message.includes("not_found"),
    ghostErr?.message);

  check("invisible and nonexistent are indistinguishable (no existence oracle)",
    secretErr?.message === ghostErr?.message,
    `secret="${secretErr?.message}" ghost="${ghostErr?.message}"`);

  const { error: unlistedErr } = await r.rpc("report_content", {
    p_target_kind: "glyph", p_target_id: glyph.id, p_reason: "sexual",
  });
  check("an unlisted glyph is not reportable",
    !!unlistedErr && unlistedErr.message.includes("not_found"), unlistedErr?.message);

  // ---------------------------------------------------------------------------
  // 5. Own content, and the input domain.
  // ---------------------------------------------------------------------------
  const { error: ownErr } = await o.rpc("report_content", {
    p_target_kind: "pebble", p_target_id: publicPebbleId, p_reason: "spam",
  });
  check("reporting your own content is refused",
    !!ownErr && ownErr.message.includes("cannot_report_own"), ownErr?.message);

  const { error: kindErr } = await r.rpc("report_content", {
    p_target_kind: "soul", p_target_id: publicPebbleId, p_reason: "spam",
  });
  check("an unknown target kind is refused",
    !!kindErr && kindErr.message.includes("invalid_kind"), kindErr?.message);

  const { error: reasonErr } = await r.rpc("report_content", {
    p_target_kind: "pebble", p_target_id: publicPebbleId, p_reason: "vibes",
  });
  check("an unknown reason is refused",
    !!reasonErr && reasonErr.message.includes("invalid_reason"), reasonErr?.message);

  // The detail cap is a CHECK, so it surfaces as a constraint violation.
  const { error: longErr } = await r.rpc("report_content", {
    p_target_kind: "profile", p_target_id: owner.id, p_reason: "other",
    p_detail: "x".repeat(1001),
  });
  check("an over-long detail is refused", !!longErr, "expected a constraint violation");

  // ---------------------------------------------------------------------------
  // 6. The admin surface is closed to non-admins. This is the security
  //    assertion; the POSITIVE admin path lives in verify-account-purge.ts.
  // ---------------------------------------------------------------------------
  const { error: listErr } = await r.rpc("admin_list_content_reports", { p_status: "open" });
  check("a non-admin cannot read the queue",
    !!listErr && listErr.message.includes("not_admin"), listErr?.message);

  const { error: resolveErr } = await r.rpc("resolve_content_report", {
    p_report_id: filedRow?.id, p_outcome: "dismissed", p_note: "nope",
  });
  check("a non-admin cannot resolve a report",
    !!resolveErr && resolveErr.message.includes("not_admin"), resolveErr?.message);
} catch (err) {
  failed += 1;
  console.error(`✗ aborted: ${err instanceof Error ? err.message : String(err)}`);
} finally {
  // Deleting the owner purges every report filed against them; deleting the
  // reporter detaches whatever is left. Teardown is therefore total.
  for (const u of [reporter, owner]) {
    if (!u) continue;
    try {
      const res = await deleteAccount(u.token);
      if (!res.ok) console.error(`cleanup: delete-account returned ${res.status}`);
    } catch (err) {
      console.error(`cleanup failed — remove reports-verify-* users manually: ${err}`);
    }
  }
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed > 0 ? 1 : 0);
```

- [ ] **Step 2: Run it to verify it fails**

```bash
cd /Users/alexis/code/pbbls && set -a && . ./.env && set +a && \
  deno run --allow-env --allow-net packages/supabase/scripts/verify-content-reports.ts
```

Expected: FAIL, **and fail for the right reason**. The setup must complete (users signed up, pebbles and profile seeded, glyph submitted), then every `report_content` / `admin_list_content_reports` / `resolve_content_report` call must error with `Could not find the function public.report_content(...) in the schema cache`, and the `content_reports` select with `relation "public.content_reports" does not exist`.

**Verify the harness reached the assertions.** If the output is a single `✗ aborted: ...` line, the harness died in its own setup and proves nothing about the missing schema — it would fail identically after Task 2 lands. That is a harness bug to fix here, not a red test. A valid red run prints many `✗` assertion lines, not one abort.

- [ ] **Step 3: Commit the red test**

```bash
git add packages/supabase/scripts/verify-content-reports.ts
git commit -m "test(db): assert the content_reports contract before it exists

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: The `content_reports` migration

**Files:**
- Create: `packages/supabase/supabase/migrations/20260916090000_content_reports.sql`

- [ ] **Step 1: Write the migration**

```sql
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
  -- an OWNERLESS glyph. The third is why this tests v_owner rather than FOUND:
  -- purge_account anonymises a sold glyph to user_id = null, and its delisting
  -- pass only covers submissions the departing user submitted — so a glyph
  -- re-attributed by admin_attribute_glyph can survive as listed with a null
  -- owner. Selecting it yields v_owner = null, which lands here instead of
  -- violating target_user_id's not-null on insert.
  --
  -- Distinguishing the three would make this an existence oracle — feed it
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
```

- [ ] **Step 2: Push it to the linked project**

```bash
cd /Users/alexis/code/pbbls && npm run db:push --workspace=packages/supabase
```

Expected: `Applying migration 20260916090000_content_reports.sql...` then `Finished supabase db push.` If it reports a syntax error, fix the file and re-run — nothing was applied.

- [ ] **Step 3: Run the harness to verify it passes**

```bash
cd /Users/alexis/code/pbbls && set -a && . ./.env && set +a && \
  deno run --allow-env --allow-net packages/supabase/scripts/verify-content-reports.ts
```

Expected: PASS — `Summary: passed=15 failed=0` (the harness defines exactly 15 `check()` calls).

- [ ] **Step 4: Commit**

```bash
git add packages/supabase/supabase/migrations/20260916090000_content_reports.sql
git commit -m "feat(db): add the content_reports primitive and its moderation RPCs

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Extend `purge_account`

**Files:**
- Create: `packages/supabase/supabase/migrations/20260916090100_purge_account_content_reports.sql`

This is a **whole re-emission** of a function that has already lost appends once (`20260731090000` exists to repair exactly that accident). The mechanical recipe below is what keeps it honest — do not retype the body from memory.

- [ ] **Step 1: Copy the previous emission verbatim as the starting point**

```bash
cd /Users/alexis/code/pbbls && \
  cp packages/supabase/supabase/migrations/20260911090100_purge_account_consents.sql \
     packages/supabase/supabase/migrations/20260916090100_purge_account_content_reports.sql
```

- [ ] **Step 2: Replace the header comment block**

Replace everything above the `create or replace function public.purge_account(p_user_id uuid)` line with:

```sql
-- =============================================================================
-- purge_account: detach the reporter, delete the reports against them (#831)
-- =============================================================================
-- content_reports (20260916090000) holds two references to a user, and they
-- are erased DIFFERENTLY on purpose:
--
--   reporter_id    -> DETACHED (set null). The moderation trail must survive a
--                     reporter who leaves; a serial abuser's report history
--                     must not evaporate because one reporter deleted their
--                     account mid-review. Mirrors the glyph_submissions
--                     submitter detach in section (2).
--   target_user_id -> DELETED. The reported content goes with the account, so
--                     an open report about it is unresolvable noise. This also
--                     takes target_snapshot, which is the only place the
--                     departing user's text survives.
--
-- Ordering matters: the detach must precede the delete, or a self-report would
-- be deleted before it could be detached and the counts would mislead.
-- (report_content refuses self-reports today; the ordering is free.)
--
-- The body below is copied VERBATIM from
-- 20260911090100_purge_account_consents.sql with exactly one addition, at the
-- section-(4) `>>> APPEND ... HERE. <<<` marker. Re-emitting this function is
-- how appends get lost: `create or replace` has no merge semantics and git
-- reports no conflict, so anything not carried forward is silently dropped
-- (that is the accident 20260731090000 exists to repair). The check is the
-- pairwise diff of the two function bodies — it must show additions only.
--
-- Design: docs/superpowers/specs/2026-09-16-content-reports-design.md §7.
-- Grants are unchanged (service-role only, set in 20260729201326);
-- create or replace preserves them.
--
-- STANDING-RULE CHECK: no emotion or domain inserts, so
-- sync_achievement_catalog() is deliberately NOT re-run.
-- =============================================================================
```

- [ ] **Step 3: Insert the two statements at the append marker**

Find this line in the new file:

```sql
  -- >>> APPEND new per-user tables from later milestones HERE. <<<
  -- -------------------------------------------------------------------------
```

Immediately **after** that closing `-- ---` line, insert:

```sql

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
```

- [ ] **Step 4: Run the pairwise diff — it MUST show additions only**

```bash
cd /Users/alexis/code/pbbls && diff \
  <(sed -n '/^create or replace function public.purge_account/,$p' packages/supabase/supabase/migrations/20260911090100_purge_account_consents.sql) \
  <(sed -n '/^create or replace function public.purge_account/,$p' packages/supabase/supabase/migrations/20260916090100_purge_account_content_reports.sql)
```

Expected: one hunk, every line prefixed `>`, and nothing prefixed `<`. **A single `<` line means an append was dropped — stop and restore it before pushing.**

- [ ] **Step 5: Push**

```bash
cd /Users/alexis/code/pbbls && npm run db:push --workspace=packages/supabase
```

Expected: `Applying migration 20260916090100_purge_account_content_reports.sql...` then `Finished supabase db push.`

- [ ] **Step 6: Commit**

```bash
git add packages/supabase/supabase/migrations/20260916090100_purge_account_content_reports.sql
git commit -m "feat(db): purge content_reports, detaching the reporter

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Regenerate the database types

**Files:**
- Modify: `packages/supabase/types/database.ts`

- [ ] **Step 1: Regenerate against the REMOTE project**

Use the `:remote` variant. `db:types` targets `--local` (Docker, which this repo does not run) and truncates the file on failure.

```bash
cd /Users/alexis/code/pbbls && npm run db:types:remote --workspace=packages/supabase
```

- [ ] **Step 2: Verify the file grew rather than emptied**

```bash
cd /Users/alexis/code/pbbls && \
  wc -l packages/supabase/types/database.ts && \
  grep -c "content_reports\|report_content\|resolve_content_report" packages/supabase/types/database.ts
```

Expected: a line count in the thousands (NOT 0 or a handful), and a grep count of at least 4. If the file is empty, the generation failed — re-run, do not commit.

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/types/database.ts
git commit -m "chore(db): regenerate types for content_reports

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Extend `verify-account-purge.ts`

This harness holds the service role, so it carries the three assertions the anon-only harness structurally cannot: correct `target_user_id` / `target_snapshot` resolution, reporting a **listed** glyph (it already seeds an approved, listed, sold glyph), and the **positive** admin path including takedown dispatch.

**Files:**
- Modify: `packages/supabase/scripts/verify-account-purge.ts`

- [ ] **Step 1: Extend the file header**

Add to the header comment, after the existing `STANDING RULE` paragraph:

```
 * It also carries the content_reports assertions that verify-content-reports.ts
 * structurally cannot (#831): that harness is anon-only, so it can neither read
 * the table back nor mint an admin past profiles_privileged_guard. Here, the
 * service role does both — so this is where "target_user_id is resolved
 * correctly", "a LISTED glyph is reportable" and the takedown dispatch of
 * resolve_content_report are actually proven.
```

- [ ] **Step 2: Add a moderator helper near the other helpers**

Insert after the `countRows` helper:

```typescript
/**
 * A real authenticated admin session. is_admin is pinned against client writes
 * by profiles_privileged_guard (20260902090000), and the exemption is exactly
 * this: the service role sets it out of band. The admin RPCs read auth.uid(),
 * so they need the USER's session, not the service-role client.
 */
async function mintModerator(email: string, password: string) {
  // The client is built HERE rather than passed in: a parameter typed
  // `ReturnType<typeof createClient>` loses the generic overloads, and every
  // `.rpc(name, args)` call through it then fails `deno check` with "args not
  // assignable to undefined". deno run type-checks by default, so that is a
  // hard failure, not a warning.
  const anonClient = createClient(SUPABASE_URL, ANON_KEY, { auth: { persistSession: false } });
  const { data, error } = await anonClient.auth.signUp({ email, password });
  if (error || !data.user || !data.session) {
    throw new Error(`signUp moderator: ${error?.message ?? "no session"}`);
  }
  const { error: promoteErr } = await admin
    .from("profiles").update({ is_admin: true }).eq("user_id", data.user.id);
  if (promoteErr) throw new Error(`promote moderator: ${promoteErr.message}`);
  return { id: data.user.id, client: anonClient, token: data.session.access_token };
}
```

- [ ] **Step 3: Seed the reports, before the seller is deleted**

Locate the section that seeds the seller's content and the sold glyph (the buyer already holds an entitlement and the submission is `approved` + `listed` by then). Immediately **before** the step that invokes `delete-account` for the seller, add:

```typescript
  // ---------------------------------------------------------------------------
  // content_reports (#831). Four seeds, covering both purge directions and the
  // two things the anon-only harness cannot reach.
  // ---------------------------------------------------------------------------

  // (a) The buyer reports the seller's PUBLIC pebble. This is the row that
  //     must be DELETED when the seller goes.
  const { data: pubPebbleId, error: pubPebbleErr } = await seller.rpc("create_pebble", {
    payload: {
      happened_at: "2026-09-01T12:00:00Z",
      intensity: 2,
      positiveness: 1,
      emotion_id: emotion.id,
      name: "reportable public",
      description: "original text",
      visibility: "public",
    },
  });
  if (pubPebbleErr || !pubPebbleId) throw new Error(`public pebble: ${pubPebbleErr?.message}`);

  const { data: pebbleReport, error: pebbleReportErr } = await buyer.rpc("report_content", {
    p_target_kind: "pebble",
    p_target_id: pubPebbleId,
    p_reason: "harassment",
    p_detail: "purge harness",
  });
  check("buyer can report the seller's public pebble",
    !pebbleReportErr && !!(pebbleReport as { id?: string } | null)?.id, pebbleReportErr?.message);

  // The correctness assertion the anon harness cannot make: the owner was
  // resolved server-side, and the snapshot captured the text at file time.
  const { data: storedReport } = await admin
    .from("content_reports").select("target_user_id, target_snapshot, status")
    .eq("id", (pebbleReport as { id: string }).id).maybeSingle();
  check("report resolved target_user_id server-side",
    storedReport?.target_user_id === sellerId,
    `got ${storedReport?.target_user_id}, want ${sellerId}`);
  check("report snapshotted the reported text",
    (storedReport?.target_snapshot as { description?: string } | null)?.description === "original text",
    JSON.stringify(storedReport?.target_snapshot));

  // Editing after the fact must NOT rewrite the evidence.
  // update_pebble takes the pebble id as its OWN argument (p_pebble_id),
  // separate from the payload — passing it inside payload silently edits
  // nothing and makes the two assertions below pass vacuously.
  const { error: updateErr } = await seller.rpc("update_pebble", {
    p_pebble_id: pubPebbleId,
    payload: { description: "innocent now" },
  });
  if (updateErr) throw new Error(`update_pebble: ${updateErr.message}`);
  const { data: afterEdit } = await admin
    .from("content_reports").select("target_snapshot")
    .eq("id", (pebbleReport as { id: string }).id).maybeSingle();
  check("editing the pebble does not rewrite the snapshot",
    (afterEdit?.target_snapshot as { description?: string } | null)?.description === "original text",
    JSON.stringify(afterEdit?.target_snapshot));

  // (b) A LISTED glyph is reportable — the case the anon harness cannot reach,
  //     because approving a submission needs an admin.
  const { data: glyphReport, error: glyphReportErr } = await buyer.rpc("report_content", {
    p_target_kind: "glyph",
    p_target_id: soldGlyph.id,
    p_reason: "sexual",
  });
  check("a listed marketplace glyph is reportable",
    !glyphReportErr && !!(glyphReport as { id?: string } | null)?.id, glyphReportErr?.message);

  // (c) The seller reports the BUYER. This is the row that must SURVIVE the
  //     seller's purge with reporter_id detached.
  const { error: buyerHandleErr } = await buyer.rpc("set_handle", { p_handle: buyerHandle });
  if (buyerHandleErr) throw new Error(`buyer set_handle: ${buyerHandleErr.message}`);
  const { error: buyerPubErr } = await admin
    .from("profiles").update({ public_profile: true }).eq("user_id", buyerId);
  if (buyerPubErr) throw new Error(`buyer publish: ${buyerPubErr.message}`);

  const { data: sellerFiled, error: sellerFiledErr } = await seller.rpc("report_content", {
    p_target_kind: "profile",
    p_target_id: buyerId,
    p_reason: "impersonation",
  });
  check("seller can report the buyer's public profile",
    !sellerFiledErr && !!(sellerFiled as { id?: string } | null)?.id, sellerFiledErr?.message);
  const sellerFiledId = (sellerFiled as { id: string }).id;

  // ---------------------------------------------------------------------------
  // The POSITIVE admin path — queue read and takedown dispatch. Needs a real
  // admin session, which only the service role can mint.
  // ---------------------------------------------------------------------------
  const moderator = await mintModerator(`purge-test-mod-${runId}@example.test`, password);
  moderatorId = moderator.id;

  const { data: queue, error: queueErr } = await moderator.client
    .rpc("admin_list_content_reports", { p_status: "open" });
  const queueRows = (queue ?? []) as Array<Record<string, unknown>>;
  check("admin queue returns the open reports", !queueErr && queueRows.length >= 3,
    queueErr ? queueErr.message : `rows=${queueRows.length}`);
  const queuedPebble = queueRows.find((r) => r.id === (pebbleReport as { id: string }).id);
  check("queue shows live content beside the snapshot",
    (queuedPebble?.target_live as { description?: string } | null)?.description === "innocent now",
    JSON.stringify(queuedPebble?.target_live));
  check("queue counts open reports per target",
    Number(queuedPebble?.open_reports_against_target) === 1,
    String(queuedPebble?.open_reports_against_target));
  check("queue does not leak reporter emails",
    !Object.keys(queuedPebble ?? {}).some((k) => k.includes("email")),
    Object.keys(queuedPebble ?? {}).join(","));

  // Takedown, one per target_kind.
  const { error: takePebbleErr } = await moderator.client.rpc("resolve_content_report", {
    p_report_id: (pebbleReport as { id: string }).id,
    p_outcome: "actioned",
    p_note: "harness takedown",
  });
  const { data: takenPebble } = await admin
    .from("pebbles").select("visibility").eq("id", pubPebbleId).maybeSingle();
  check("actioning a pebble report drops it to secret",
    !takePebbleErr && takenPebble?.visibility === "secret",
    takePebbleErr ? takePebbleErr.message : takenPebble?.visibility);

  const { error: takeGlyphErr } = await moderator.client.rpc("resolve_content_report", {
    p_report_id: (glyphReport as { id: string }).id,
    p_outcome: "actioned",
  });
  const { data: takenSub } = await admin
    .from("glyph_submissions").select("listed").eq("glyph_id", soldGlyph.id).maybeSingle();
  check("actioning a glyph report delists it",
    !takeGlyphErr && takenSub?.listed === false,
    takeGlyphErr ? takeGlyphErr.message : String(takenSub?.listed));

  // Resolving twice is refused — the verdict is not re-writable.
  const { error: reResolveErr } = await moderator.client.rpc("resolve_content_report", {
    p_report_id: (pebbleReport as { id: string }).id, p_outcome: "dismissed",
  });
  check("a resolved report cannot be resolved again",
    !!reResolveErr && reResolveErr.message.includes("invalid_state"), reResolveErr?.message);
```

**Prerequisites, verified against the file as it stands:** the block uses `seller` and `buyer` (the two anon-key clients, defined at lines 190 and 345), `sellerId`, `buyerId`, `soldGlyph`, `emotion`, `runId`, `password`, `SUPABASE_URL`, `ANON_KEY` and `admin` — all already defined. The one new binding is the buyer's handle: add

```typescript
  const buyerHandle = `purgetestbuyer${runId}`;
```

beside the existing `const handle = \`purgetest${runId}\`;` (line 249). Place the whole block **after** the buyer has bought the glyph (line 351) — `buyer` does not exist before line 345, and the glyph's submission is only `listed` from line 161.

- [ ] **Step 4: Add the purge assertions after the seller is deleted**

First extend the existing `sellerScoped` table (line 389) — the loop below it already turns each pair into a zero-row assertion, so both purge directions ride the established mechanism:

```typescript
    ["content_reports", "target_user_id"],
    ["content_reports", "reporter_id"],
```

`target_user_id` proves the delete (reports against the seller are gone with their content); `reporter_id` proves the *detach* (the column is null now, so nothing matches the seller). Neither proves the detached row still **exists** — that needs the explicit assertion below.

Then, beside the `purged handle resolves null` check, add:

```typescript
  // ...but the report the seller FILED survives, detached. The moderation
  // trail must outlive the reporter.
  const { data: survivor } = await admin
    .from("content_reports").select("id, reporter_id, target_user_id")
    .eq("id", sellerFiledId).maybeSingle();
  check("the report the seller filed survives the purge", !!survivor,
    "the moderation trail was destroyed with the reporter");
  check("the surviving report is detached from the seller",
    survivor?.reporter_id === null, String(survivor?.reporter_id));
  check("the surviving report still names its target",
    survivor?.target_user_id === buyerId, String(survivor?.target_user_id));
```

- [ ] **Step 5: Clean up the moderator in the `finally` block**

In the existing `finally`, beside the seller and buyer cleanup:

```typescript
    if (moderatorId) await forceCleanup(moderatorId, "moderator");
```

Declare `let moderatorId: string | null = null;` beside the other top-level ids and set it (`moderatorId = moderator.id;`) right after `mintModerator` returns.

- [ ] **Step 6: Run the harness**

```bash
cd /Users/alexis/code/pbbls && set -a && . ./.env && set +a && \
  npm run db:verify:purge --workspace=packages/supabase
```

Expected: PASS — `failed=0`. This harness is **not** run by CI, so this manual run is the only proof; do not skip it.

- [ ] **Step 7: Commit**

```bash
git add packages/supabase/scripts/verify-account-purge.ts
git commit -m "test(db): prove the content_reports purge and takedown paths

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Wire the new harness into `db:verify` and CI

**Files:**
- Modify: `packages/supabase/package.json`
- Modify: `.github/workflows/supabase.yml`

- [ ] **Step 1: Add the npm script**

In `packages/supabase/package.json`, add to `scripts`:

```json
    "db:verify:reports": "deno run --allow-env --allow-net scripts/verify-content-reports.ts",
```

and extend the chain (it runs last, after the existing five):

```json
    "db:verify": "npm run db:verify:drafts && npm run db:verify:visibility && npm run db:verify:public-profile && npm run db:verify:guard && npm run db:verify:reference && npm run db:verify:reports",
```

- [ ] **Step 2: Add the CI step**

In `.github/workflows/supabase.yml`, after the `Emotion reference data (#796 drift)` step and before `Write the run log`:

```yaml
      - name: Content reports (#831 UGC report path)
        if: '!cancelled()'
        run: .github/scripts/verify-harness.sh "reports" db:verify:reports
```

- [ ] **Step 3: Run the whole chain**

```bash
cd /Users/alexis/code/pbbls && set -a && . ./.env && set +a && \
  npm run db:verify --workspace=packages/supabase
```

Expected: all six harnesses report `failed=0`.

- [ ] **Step 4: Lint the workspace**

```bash
cd /Users/alexis/code/pbbls && npm run lint --workspace=packages/supabase && \
  npm run build --workspace=packages/supabase
```

Expected: `deno check` passes on every script, `tsc --noEmit` passes.

- [ ] **Step 5: Commit**

```bash
git add packages/supabase/package.json .github/workflows/supabase.yml
git commit -m "test(db): gate the content_reports contract in CI

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Update the Arkaik map

The map is **hosted**. Use the `arkaik-mcp` tools — never edit `docs/arkaik/bundle.json`. If the tools are unavailable, say so and stop.

- [ ] **Step 1: Create the endpoint nodes**

`create_node` for three API nodes — `API-report-content`, `API-admin-list-content-reports`, `API-resolve-content-report` — each with status `development` on the supabase platform, and edges to the views they defend (`V-shared-pebble` and the public-profile and glyph-market views named in the finding's `node_ids`).

- [ ] **Step 2: Create the data model node**

`create_node` for `DM-content-report`, edged from the three endpoints above.

- [ ] **Step 3: Note the ids for the Lab Note**

Record the created ids — they go in the PR's `nodes:` list, most important first.

---

### Task 8: Open the PR

- [ ] **Step 1: Push the branch**

```bash
cd /Users/alexis/code/pbbls && git push -u origin feat/831-content-reports-primitive
```

- [ ] **Step 2: Open the PR**

Title: `feat(db): add the content_reports UGC report primitive`
Body starts with `Resolves #831`, lists the key files, and records the three knowing gaps from the spec (anon share links have no in-product path; block enforcement is out of scope; the positive admin path is proven only in the manual purge harness).

Labels inherited from #831 — `feat`, `db`, `supabase`, `legal` — and milestone `M56 · Compliance Batch B`. **Confirm the inheritance with the user before opening.**

- [ ] **Step 3: Lab Note**

This PR ships no user-visible change on its own — it is the server primitive the clients will use. It carries the `feat` label, so the reminder will fire. Per the gate, the correct move is a **no-lab-note** label plus deleting the section, and the user-facing note lands with the first client PR. **Confirm with the user** rather than deciding alone.

- [ ] **Step 4: Resolve the Kritik finding**

Only **after** the PR merges, `kritik_resolve_finding` with `finding_id: F-2026-08-PLT-supabase-01` and `resolved_by` set to the merged PR URL.

Note the finding's remediation also names client report UI. Resolving the supabase-surface finding is correct — the client work is separate, tracked by the follow-up issues in spec §9 — but flag to the user that Apple 1.2 remains only partly satisfied until those land.

---

## Follow-up issues to open (spec §9)

Not part of this plan's execution. Open them after the PR, one per surface: web / iOS / Android report affordances, the admin moderation queue page, block enforcement (supabase), and the privacy-policy line describing `target_snapshot` retention.
