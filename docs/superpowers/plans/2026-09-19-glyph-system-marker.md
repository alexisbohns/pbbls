# Glyph System Marker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop a purged seller's paid glyph from becoming a free system glyph, by giving `glyphs` an explicit `is_system` marker and re-keying every reader off it instead of off `user_id is null` (issue #872).

**Architecture:** One migration adds `glyphs.is_system`, backfills it for genuinely first-party rows, guards it against user writes with a trigger, and re-emits `can_use_glyph`, the `glyphs_select` policy and the two admin RPCs that mint system glyphs. Android — the only client that keyed its picker off `user_id is null` — swaps the filter. Two harnesses prove it: a new anon-key one in CI, and the existing service-role purge harness for the end-to-end scenario.

**Tech Stack:** Postgres (Supabase), plpgsql, Deno harnesses against the **linked remote project** (no local Docker), Kotlin + kotlinx.serialization, JUnit.

**Spec:** `docs/superpowers/specs/2026-09-19-glyph-system-marker-design.md`

**Branch:** `fix/872-glyph-system-marker` (already created, spec already committed)

---

## Read this before Task 1

- **Migrations push to the REMOTE linked project.** An applied migration is immutable — a correction is a new migration. Write each migration file completely, then push once.
- **Type regeneration uses `db:types:remote`.** The plain `db:types` targets `--local` (Docker) and truncates `types/database.ts` on failure.
- **Env:** `set -a; . ./.env; set +a` from the repo root. It carries `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY`. Never echo credential values.
- **The order is red-then-green.** Task 1 writes the CI harness against a column that does not exist yet; it must fail. Task 2 makes it pass.
- **Two harnesses, two reasons.** The anon-only one (CI-gated, `db:verify`) proves the *guard* — that a user cannot promote their own glyph to system, and that an unowned glyph is unattachable. The service-role one (manual, `db:verify:purge`) proves the *purge scenario*, because reaching "sold glyph whose seller is gone" needs the service role.
- **Never write a Kritik finding id (`F-…`) in the PR body.** Follow-ups are referenced by GitHub issue number only.

## File structure

| File | Responsibility |
|---|---|
| `packages/supabase/scripts/verify-glyph-usability.ts` | Create: anon-key guard harness (CI-gated) |
| `packages/supabase/package.json` | Modify: `db:verify:glyph` script + add it to `db:verify` |
| `packages/supabase/supabase/migrations/20260919090000_glyph_system_marker.sql` | Create: column, backfill, index, guard trigger, `can_use_glyph`, `glyphs_select`, `admin_set_domain_glyph`, `admin_set_achievement_glyph` |
| `packages/supabase/types/database.ts` | Regenerate |
| `packages/supabase/scripts/verify-account-purge.ts` | Modify: the purged-glyph assertions the issue names |
| `apps/android/.../features/glyph/models/Glyph.kt` | Modify: `isSystem` field |
| `apps/android/.../features/glyph/models/GlyphMarket.kt` | Modify: `isSystem` on `MineGlyphRow`, carried by `toGlyph()` |
| `apps/android/.../features/glyph/services/GlyphService.kt` | Modify: `attachable()` companion fn + column list |
| `apps/android/.../features/glyph/services/GlyphMarketService.kt` | Modify: `mineTab()` companion fn + column list |
| `apps/android/app/src/test/.../glyph/services/GlyphServiceTest.kt` | Modify: `attachable()` cases |
| `apps/android/app/src/test/.../glyph/services/GlyphMarketMineTabTest.kt` | Create: `mineTab()` cases |
| `apps/android/app/src/test/.../glyph/models/GlyphMarketDecodingTest.kt` | Modify: `is_system` decode cases |

---

### Task 1: Red — the anon-key guard harness

The security property first. This harness is the one CI runs, so it must prove the two things a user could otherwise do: attach artwork they never paid for, and promote their own glyph into the curated system set.

**Files:**
- Create: `packages/supabase/scripts/verify-glyph-usability.ts`
- Modify: `packages/supabase/package.json`

- [ ] **Step 1: Write the harness**

Create `packages/supabase/scripts/verify-glyph-usability.ts`:

```typescript
#!/usr/bin/env -S deno run --allow-env --allow-net
/**
 * Acceptance test for the glyph usability guard — runs against the REMOTE project.
 *
 * `can_use_glyph` (20260712000000) decides whether a glyph may be attached to a
 * pebble or a soul: authored by the actor, a system default, or entitled. Until
 * #872 "system default" meant `glyphs.user_id is null` — which is also what
 * `purge_account` leaves behind when it anonymizes a SOLD glyph whose creator
 * deleted their account. A stranger could then attach artwork other people paid
 * karma for, free, and the buyers' entitlements stopped meaning anything.
 *
 * The marker is now `glyphs.is_system`, set only on first-party seeds, and
 * pinned against client writes by `enforce_glyph_system_flag`.
 *
 * What this proves, and why a same-surface unit test cannot:
 *
 *   1. A glyph authored by someone else, never bought, cannot be attached —
 *      through create_pebble AND through the direct souls write (two different
 *      enforcement mechanisms: the RPC guard and the souls_glyph_usable trigger).
 *   2. The guard did not over-block: a system glyph and the caller's own carve
 *      both still attach.
 *   3. `is_system` is not self-settable. Every negative assertion checks the
 *      error AND re-reads the stored value — a 0-row RLS filter would also leave
 *      the value unchanged while proving nothing (the lesson
 *      verify-profiles-privileged-guard.ts already encodes).
 *   4. The browse-friendly glyphs_select RLS still hides an unsubmitted glyph
 *      from a stranger.
 *
 * Run:
 *   SUPABASE_URL=... SUPABASE_ANON_KEY=... \
 *     deno run --allow-env --allow-net \
 *     packages/supabase/scripts/verify-glyph-usability.ts
 *
 * Deliberately needs NO service-role key: it signs up two throwaway users and
 * deletes them through the real `delete-account` edge function. Cleanup runs
 * even on failure. Exits non-zero if any assertion fails.
 *
 * The purged-seller scenario itself lives in verify-account-purge.ts — reaching
 * that state needs the service role, which this harness deliberately lacks.
 *
 * STANDING RULE (#872): a new way to mark a glyph first-party is added to
 * `enforce_glyph_system_flag`'s allowance AND to the negative assertions here in
 * the same change. An unguarded write path is a free-glyph vulnerability.
 */

const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY");

if (!SUPABASE_URL || !ANON_KEY) {
  console.error("SUPABASE_URL and SUPABASE_ANON_KEY must be set");
  Deno.exit(2);
}

import { createClient, type SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

/** The seeded default glyph (20260426000000) — the canonical system row. */
const SYSTEM_GLYPH_ID = "4759c37c-68a6-46a6-b4fc-046bd0316752";

/** The stroke shape every client decodes — `d` plus `width` (#870). */
const STROKES = [{ d: "M20,20 L180,180", width: 6 }];

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
const password = `Glyph-${crypto.randomUUID()}`;

type TestUser = { client: SupabaseClient; id: string; token: string };

async function signUp(label: string): Promise<TestUser> {
  const client = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
  const email = `glyph-verify-${label}-${runId}@example.test`;
  const { data, error } = await client.auth.signUp({ email, password });
  if (error || !data.user || !data.session) {
    throw new Error(`signUp ${label}: ${error?.message ?? "no session"}`);
  }
  return { client, id: data.user.id, token: data.session.access_token };
}

async function deleteAccount(user: TestUser | null, label: string) {
  if (!user) return;
  const res = await fetch(`${SUPABASE_URL}/functions/v1/delete-account`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${user.token}`,
      apikey: ANON_KEY!,
      "Content-Type": "application/json",
    },
  });
  if (!res.ok) console.error(`cleanup ${label} failed: ${res.status} ${await res.text()}`);
}

/** Carve a glyph as `user`. Single-table insert — the path every client uses. */
async function carve(user: TestUser, name: string): Promise<string> {
  const { data, error } = await user.client
    .from("glyphs")
    .insert({ user_id: user.id, name, strokes: STROKES, view_box: "0 0 200 200" })
    .select("id").single();
  if (error || !data) throw new Error(`carve ${name}: ${error?.message}`);
  return data.id as string;
}

/** Attempt a pebble with `glyphId`; returns the error message, or null on success. */
async function attachToPebble(
  user: TestUser,
  glyphId: string,
  emotionId: string,
  label: string,
): Promise<string | null> {
  const { error } = await user.client.rpc("create_pebble", {
    payload: {
      name: `glyph-verify ${label} ${runId}`,
      happened_at: new Date().toISOString(),
      intensity: 2,
      positiveness: 1,
      visibility: "private",
      emotion_id: emotionId,
      glyph_id: glyphId,
    },
  });
  return error?.message ?? null;
}

let alice: TestUser | null = null;
let bob: TestUser | null = null;

try {
  alice = await signUp("alice");
  bob = await signUp("bob");
  console.log(`Created alice ${alice.id} and bob ${bob.id}\n`);

  const { data: emotion, error: emotionErr } = await alice.client
    .from("emotions").select("id").limit(1).single();
  if (emotionErr || !emotion) throw new Error(`emotions read: ${emotionErr?.message}`);
  const emotionId = emotion.id as string;

  const aliceGlyph = await carve(alice, `glyph-verify alice ${runId}`);
  const bobGlyph = await carve(bob, `glyph-verify bob ${runId}`);

  // ---------------------------------------------------------------------------
  // 1. The seeds are system; a user's carve is not.
  // ---------------------------------------------------------------------------
  const { data: systemRow, error: systemErr } = await alice.client
    .from("glyphs").select("id, user_id, is_system").eq("id", SYSTEM_GLYPH_ID).maybeSingle();
  check("the seeded default glyph is marked is_system",
    !systemErr && systemRow?.is_system === true,
    systemErr ? systemErr.message : JSON.stringify(systemRow));
  check("the seeded default glyph is still ownerless",
    systemRow?.user_id === null, String(systemRow?.user_id));

  const { data: carvedRow } = await alice.client
    .from("glyphs").select("is_system").eq("id", aliceGlyph).maybeSingle();
  check("a freshly carved glyph is NOT system", carvedRow?.is_system === false,
    JSON.stringify(carvedRow));

  // ---------------------------------------------------------------------------
  // 2. Bob cannot attach Alice's glyph — the RPC guard and the souls trigger.
  // ---------------------------------------------------------------------------
  const pebbleErr = await attachToPebble(bob, aliceGlyph, emotionId, "steal");
  check("create_pebble rejects another user's unsold glyph",
    pebbleErr !== null && pebbleErr.includes("Glyph not usable by user"),
    pebbleErr ?? "the pebble was created");

  const { error: soulErr } = await bob.client
    .from("souls").insert({ user_id: bob.id, name: `glyph-verify soul ${runId}`, glyph_id: aliceGlyph });
  check("souls_glyph_usable rejects another user's unsold glyph",
    soulErr !== null && soulErr.message.includes("Glyph not usable by user"),
    soulErr?.message ?? "the soul was created");

  // ---------------------------------------------------------------------------
  // 3. The guard did not over-block.
  // ---------------------------------------------------------------------------
  check("create_pebble accepts the system glyph",
    (await attachToPebble(bob, SYSTEM_GLYPH_ID, emotionId, "system")) === null);
  check("create_pebble accepts the caller's own carve",
    (await attachToPebble(bob, bobGlyph, emotionId, "own")) === null);

  const { error: ownSoulErr } = await bob.client
    .from("souls").insert({ user_id: bob.id, name: `glyph-verify own soul ${runId}`, glyph_id: bobGlyph });
  check("souls accepts the caller's own carve", ownSoulErr === null, ownSoulErr?.message);

  // can_use_glyph directly — the helper the three call sites share.
  const { data: usableOwn } = await bob.client
    .rpc("can_use_glyph", { p_glyph_id: bobGlyph, p_user: bob.id });
  check("can_use_glyph(own) is true", usableOwn === true, String(usableOwn));
  const { data: usableTheirs } = await bob.client
    .rpc("can_use_glyph", { p_glyph_id: aliceGlyph, p_user: bob.id });
  check("can_use_glyph(someone else's) is false", usableTheirs === false, String(usableTheirs));
  const { data: usableSystem } = await bob.client
    .rpc("can_use_glyph", { p_glyph_id: SYSTEM_GLYPH_ID, p_user: bob.id });
  check("can_use_glyph(system) is true", usableSystem === true, String(usableSystem));

  // ---------------------------------------------------------------------------
  // 4. is_system is not self-settable (#872 D3).
  //
  // glyphs_update is owner-scoped and has NO WITH CHECK, so Postgres reuses the
  // USING clause — which a self-promotion satisfies. Without the trigger, any
  // user could donate their own glyph into the curated system set, free for
  // everyone, bypassing the admin queue. Assert the error AND re-read.
  // ---------------------------------------------------------------------------
  const { error: promoteErr } = await bob.client
    .from("glyphs").update({ is_system: true }).eq("id", bobGlyph);
  check("a user cannot promote their own glyph to is_system", promoteErr !== null,
    "the update was accepted");
  const { data: afterPromote } = await bob.client
    .from("glyphs").select("is_system").eq("id", bobGlyph).maybeSingle();
  check("…and the stored value is still false", afterPromote?.is_system === false,
    JSON.stringify(afterPromote));

  const { error: insertErr } = await bob.client
    .from("glyphs")
    .insert({
      user_id: bob.id,
      name: `glyph-verify born-system ${runId}`,
      strokes: STROKES,
      view_box: "0 0 200 200",
      is_system: true,
    });
  check("a user cannot INSERT a glyph already marked is_system", insertErr !== null,
    "the insert was accepted");

  // ---------------------------------------------------------------------------
  // 5. glyphs_select still hides an unsubmitted glyph from a stranger.
  // ---------------------------------------------------------------------------
  const { data: peek } = await bob.client
    .from("glyphs").select("id").eq("id", aliceGlyph).maybeSingle();
  check("a stranger cannot read an unsubmitted glyph", peek === null, JSON.stringify(peek));
} catch (err) {
  failed += 1;
  console.error(`✗ aborted: ${err instanceof Error ? err.message : String(err)}`);
} finally {
  await deleteAccount(alice, "alice");
  await deleteAccount(bob, "bob");
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed > 0 ? 1 : 0);
```

- [ ] **Step 2: Wire it into the scripts**

In `packages/supabase/package.json`, add `db:verify:glyph` to the chain and as its own script. Replace the `db:verify` line and add the new one after `db:verify:reports`:

```json
    "db:verify": "npm run db:verify:drafts && npm run db:verify:visibility && npm run db:verify:public-profile && npm run db:verify:guard && npm run db:verify:reference && npm run db:verify:reports && npm run db:verify:glyph",
```

```json
    "db:verify:glyph": "deno run --allow-env --allow-net scripts/verify-glyph-usability.ts",
```

- [ ] **Step 3: Run it to verify it fails**

```bash
cd /Users/alexis/code/pbbls && set -a && . ./.env && set +a && \
  deno run --allow-env --allow-net packages/supabase/scripts/verify-glyph-usability.ts
```

Expected: FAIL. `is_system` does not exist yet, so the section-1 selects error with `column glyphs.is_system does not exist` and the assertions report `✗`. Sections 2, 3 and 5 should already pass — `can_use_glyph` already rejects another user's glyph. That split is the honest red: the new column and its guard are what is missing, not the base guard.

Confirm the run exits non-zero and that the two throwaway users were cleaned up (`delete-account` logged no error).

- [ ] **Step 4: Lint**

```bash
npm run lint --workspace=packages/supabase
```

Expected: PASS (`deno check` over `scripts/*.ts`). Type errors here are real — fix them before committing.

- [ ] **Step 5: Commit**

```bash
git add packages/supabase/scripts/verify-glyph-usability.ts packages/supabase/package.json
git commit -m "test(db): add the anon-key glyph usability harness (#872)

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: Green — the migration

**Files:**
- Create: `packages/supabase/supabase/migrations/20260919090000_glyph_system_marker.sql`
- Modify: `packages/supabase/types/database.ts` (regenerated)

- [ ] **Step 1: Write the migration**

Create `packages/supabase/supabase/migrations/20260919090000_glyph_system_marker.sql`:

```sql
-- =============================================================================
-- Glyph system marker (#872)
-- =============================================================================
-- `user_id is null` meant two incompatible things at once:
--
--   (a) a first-party seed — the default glyph, the per-domain seeds, the
--       glyphs admin_set_domain_glyph / admin_set_achievement_glyph mint; and
--   (b) a glyph whose creator deleted their account, which purge_account
--       deliberately KEEPS and anonymizes because buyers hold entitlements on
--       it (20260729201326:93).
--
-- can_use_glyph and glyphs_select both read (a) and therefore accepted (b): the
-- moment a seller purged, artwork other people paid karma for became free for
-- everyone and indistinguishable from a first-party seed, and it grew the
-- system set with third-party work the admin queue never curated.
--
-- This splits the two meanings. `is_system` is the marker; ownerless is just
-- ownerless.
--
-- Design: docs/superpowers/specs/2026-09-19-glyph-system-marker-design.md
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. The marker
-- ---------------------------------------------------------------------------
alter table public.glyphs
  add column is_system boolean not null default false;

comment on column public.glyphs.is_system is
  'First-party seed: the default glyph, the domain seeds, and the rows '
  'admin_set_domain_glyph / admin_set_achievement_glyph mint. NOT derivable '
  'from user_id — purge_account anonymizes SOLD glyphs to user_id = null and '
  'those must stay entitlement-gated (#872). Not settable by clients; see '
  'enforce_glyph_system_flag.';

-- ---------------------------------------------------------------------------
-- 2. Backfill
-- ---------------------------------------------------------------------------
-- The two `not exists` clauses are what keep this correct at APPLY time rather
-- than only at authoring time. Every sold glyph carries an approved
-- glyph_submissions row by construction (buy_glyph requires status='approved'
-- and listed; purge_account keeps the row and flips only `listed`), so a seller
-- who purges between authoring and deploy is excluded automatically.
--
-- Probed 2026-09-19 against the linked project: 20 ownerless glyphs — 18
-- `domain:<slug>` seeds, the default glyph, and one pre-marketplace row
-- ("Barking Dog", 2026-04-23) that three live rows already point at. None
-- carries a submission or an entitlement, so all 20 match. That last row is
-- also why this is not a provenance allowlist keyed on domains/achievements: it
-- would have been missed, and stripping it would break a soul and two pebbles.
update public.glyphs g
   set is_system = true
 where g.user_id is null
   and not exists (select 1 from public.glyph_submissions s where s.glyph_id = g.id)
   and not exists (select 1 from public.glyph_entitlements e where e.glyph_id = g.id);

create index glyphs_is_system_idx on public.glyphs (is_system) where is_system;

-- ---------------------------------------------------------------------------
-- 3. The write guard
-- ---------------------------------------------------------------------------
-- glyphs_insert forces user_id = auth.uid(), so no client can create an
-- ownerless row. Nothing stopped {user_id: me, is_system: true}: glyphs_update
-- has no WITH CHECK, so Postgres reuses USING, and a self-promotion satisfies
-- `user_id = auth.uid() and not submitted and not entitled`. Left open, any
-- user could donate their glyph into the curated set — the same class of defect
-- this migration closes.
--
-- Column-level REVOKE does not work here: Postgres cannot revoke one column out
-- of a table-level grant, so it would mean revoking table INSERT/UPDATE and
-- re-granting every other column by name, a list that rots as columns are added.
--
-- `auth.uid() is null` admits migrations and the service role (purge_account,
-- the delete-account function). is_admin admits the two admin RPCs below, which
-- run security definer but keep the caller's auth.uid().
create or replace function public.enforce_glyph_system_flag()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.is_system is distinct from coalesce(old.is_system, false)
     and auth.uid() is not null
     and not public.is_admin(auth.uid()) then
    raise exception 'is_system is not user-settable' using errcode = '42501';
  end if;
  return new;
end;
$$;

drop trigger if exists glyphs_system_flag_guard on public.glyphs;
create trigger glyphs_system_flag_guard
  before insert or update on public.glyphs
  for each row execute function public.enforce_glyph_system_flag();

-- ---------------------------------------------------------------------------
-- 4. can_use_glyph — the attach guard (base: 20260712000000, one predicate)
-- ---------------------------------------------------------------------------
-- Shared by create_pebble, update_pebble and the souls_glyph_usable trigger;
-- re-emitting it alone fixes all three. None of those three is touched here.
create or replace function public.can_use_glyph(p_glyph_id uuid, p_user uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select p_glyph_id is null
    or exists (
      select 1 from public.glyphs g
      where g.id = p_glyph_id
        and (g.user_id = p_user or g.is_system)
    )
    or exists (
      select 1 from public.glyph_entitlements e
      where e.glyph_id = p_glyph_id and e.user_id = p_user
    );
$$;

-- ---------------------------------------------------------------------------
-- 5. glyphs_select — the read policy (base: 20260630003348, one predicate)
-- ---------------------------------------------------------------------------
-- Safe for the anonymized sold glyph precisely because purge_account keeps
-- status = 'approved' and flips only `listed`: the approved-submission arm
-- still grants the read, so buyers' pebbles and souls keep rendering and the
-- glyph stays browsable. Only the ATTACH closes.
drop policy if exists "glyphs_select" on public.glyphs;
create policy "glyphs_select" on public.glyphs for select to authenticated
  using (
    user_id = auth.uid()
    or is_system
    or exists (select 1 from public.glyph_submissions s
               where s.glyph_id = glyphs.id and s.status = 'approved')
    or exists (select 1 from public.glyph_entitlements e
               where e.glyph_id = glyphs.id and e.user_id = auth.uid())
  );

-- ---------------------------------------------------------------------------
-- 6. New system glyphs are born system
-- ---------------------------------------------------------------------------
-- Bodies otherwise VERBATIM from 20260731090100 and 20260730150000, with
-- `is_system` added to the first-glyph insert only. The replace-in-place
-- branches touch strokes/view_box and are unchanged. Neither function carries
-- in-body append markers and neither is re-emitted twice in this migration, so
-- the pairwise-diff rule has nothing to union here.
--
-- publish_admin_glyph is deliberately NOT in this list: it writes
-- user_id = v_user, so an admin-published market glyph is owned, not system,
-- and is sold through buy_glyph like any other.
create or replace function public.admin_set_domain_glyph(
  p_domain_id uuid,
  p_strokes jsonb,
  p_view_box text
)
returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_glyph_id uuid;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  if p_strokes is null or jsonb_array_length(p_strokes) = 0 then
    raise exception 'empty_glyph';
  end if;

  select default_glyph_id into v_glyph_id
  from public.domains where id = p_domain_id;
  if not found then
    raise exception 'not_found';
  end if;

  if v_glyph_id is null then
    -- First glyph for this domain: system-owned (NULL user_id), shapeless.
    insert into public.glyphs (user_id, name, strokes, view_box, is_system)
    values (null, null, p_strokes, p_view_box, true)
    returning id into v_glyph_id;

    update public.domains set default_glyph_id = v_glyph_id where id = p_domain_id;
  else
    -- Replace in place: same glyph_id, so FKs and caches keep pointing at it.
    update public.glyphs
       set strokes = p_strokes,
           view_box = p_view_box,
           updated_at = now()
     where id = v_glyph_id;
  end if;

  return v_glyph_id;
end;
$$;

create or replace function public.admin_set_achievement_glyph(
  p_achievement_id uuid,
  p_strokes jsonb,
  p_view_box text
)
returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_glyph_id uuid;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  if p_strokes is null or jsonb_array_length(p_strokes) = 0 then
    raise exception 'empty_glyph';
  end if;

  select glyph_id into v_glyph_id
  from public.achievements where id = p_achievement_id;
  if not found then
    raise exception 'not_found';
  end if;

  if v_glyph_id is null then
    insert into public.glyphs (user_id, name, strokes, view_box, is_system)
    values (null, null, p_strokes, p_view_box, true)
    returning id into v_glyph_id;

    update public.achievements set glyph_id = v_glyph_id where id = p_achievement_id;
  else
    update public.glyphs
       set strokes = p_strokes,
           view_box = p_view_box,
           updated_at = now()
     where id = v_glyph_id;
  end if;

  return v_glyph_id;
end;
$$;
```

**Before writing this file**, confirm the `admin_set_achievement_glyph` signature and body against `packages/supabase/supabase/migrations/20260730150000_admin_achievement_management.sql` (read from the `create or replace function public.admin_set_achievement_glyph` line to its closing `$$;`). The body above is transcribed from it; if the live one differs in any line other than the insert, carry that difference forward rather than the version here.

- [ ] **Step 2: Push the migration**

```bash
cd /Users/alexis/code/pbbls && set -a && . ./.env && set +a && \
  npm run db:push --workspace=packages/supabase
```

Expected: `Applying migration 20260919090000_glyph_system_marker.sql...` then `Finished supabase db push.`

If it errors, the migration has NOT been applied — fix the file and push again. Once it succeeds the file is immutable; a correction is a new migration.

- [ ] **Step 3: Verify the backfill marked exactly the expected rows**

```bash
cd /Users/alexis/code/pbbls && set -a && . ./.env && set +a && \
deno eval '
import { createClient } from "npm:@supabase/supabase-js@2";
const db = createClient(Deno.env.get("SUPABASE_URL"), Deno.env.get("SUPABASE_SERVICE_ROLE_KEY"));
const { data: sys } = await db.from("glyphs").select("id, name").eq("is_system", true);
const { data: orphan } = await db.from("glyphs").select("id, name").is("user_id", null).eq("is_system", false);
console.log("is_system rows:", sys.length);
console.log("ownerless but NOT system:", orphan.length, JSON.stringify(orphan));
'
```

Expected: `is_system rows: 20` and `ownerless but NOT system: 0 []`.

If `is_system rows` is not 20, stop and report — the backfill predicate met data the probe did not see.

- [ ] **Step 4: Regenerate the types**

```bash
cd /Users/alexis/code/pbbls && set -a && . ./.env && set +a && \
  npm run db:types:remote --workspace=packages/supabase && \
  git diff --stat packages/supabase/types/database.ts
```

Expected: a non-empty diff adding `is_system: boolean` to the `glyphs` Row/Insert/Update shapes. If the file came back truncated (a tiny diff removing hundreds of lines), the generation failed — `git checkout packages/supabase/types/database.ts` and retry. Never commit a truncated `database.ts`.

- [ ] **Step 5: Run the harness — it must now pass**

```bash
cd /Users/alexis/code/pbbls && set -a && . ./.env && set +a && \
  deno run --allow-env --allow-net packages/supabase/scripts/verify-glyph-usability.ts
```

Expected: every line `✓`, `Summary: passed=16 failed=0`, exit 0.

- [ ] **Step 6: Run the full CI-gated verify chain**

```bash
cd /Users/alexis/code/pbbls && set -a && . ./.env && set +a && \
  npm run db:verify --workspace=packages/supabase
```

Expected: all seven harnesses pass. A regression in `verify-public-profile` or `verify-pebble-visibility` would mean the `glyphs_select` rewrite narrowed a read that something depended on — investigate rather than adjusting the harness.

- [ ] **Step 7: Lint and commit**

```bash
npm run lint --workspace=packages/supabase
git add packages/supabase/supabase/migrations/20260919090000_glyph_system_marker.sql \
        packages/supabase/types/database.ts
git commit -m "fix(db): mark system glyphs explicitly instead of by null owner (#872)

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: The purge scenario assertion

The assertion #872 names: after a seller purges, a stranger still cannot attach the glyph the buyer paid for. It belongs in `verify-account-purge.ts` because that harness owns the seller/buyer/sale fixture and has the service role needed to reach the state.

**Files:**
- Modify: `packages/supabase/scripts/verify-account-purge.ts`

- [ ] **Step 1: Add the stranger to the fixture**

`verify-account-purge.ts` already mints a moderator through `mintModerator`. The stranger must be an *ordinary* user — an admin would leave a future reader wondering whether the assertion is about admin powers. Add a `strangerId` alongside `moderatorId` (near line 137):

```typescript
let moderatorId: string | null = null;
/** An ordinary third user: holds no entitlement on the sold glyph and never
 *  did. The proof that the purge did not turn paid artwork into a free seed
 *  (#872) has to come from someone with no claim on it at all. */
let strangerId: string | null = null;
```

Add `strangerId` to the `finally` cleanup block, beside the moderator line:

```typescript
    if (moderatorId) await forceCleanup(moderatorId, "moderator");
    if (strangerId) await forceCleanup(strangerId, "stranger");
```

- [ ] **Step 2: Sign the stranger up before the seller is deleted**

Immediately after the `moderatorId = moderator.id;` line (around line 507), add:

```typescript
  // An ordinary third user for the #872 assertions below. Signed up BEFORE the
  // purge so the sold glyph's fate is the only variable.
  const stranger = createAnonClient();
  const { data: strangerAuth, error: strangerErr } = await stranger.auth.signUp({
    email: `purge-test-stranger-${runId}@example.test`,
    password,
  });
  if (strangerErr || !strangerAuth.user) throw new Error(`signUp stranger: ${strangerErr?.message}`);
  strangerId = strangerAuth.user.id;
```

- [ ] **Step 3: Add the assertions**

In section 5, immediately after the existing `check("sold glyph strokes still decode on every client ({d, width})", …)` block and before the `const { data: keptSub }` block, insert:

```typescript
  // #872: keeping the row is right — the buyer paid for it. Turning it into a
  // SYSTEM glyph is not. Before the is_system marker, `user_id is null` was
  // both "first-party seed" and "creator deleted their account", so
  // can_use_glyph handed a stranger free use of artwork someone bought.
  //
  // Anonymized is not system. Counting rows cannot see this: the glyph exists,
  // renders, and the entitlement row is intact in every version of this bug.
  check("anonymized sold glyph is NOT marked is_system",
    keptGlyph?.is_system === false, JSON.stringify(keptGlyph?.is_system));

  const { data: strangerMay, error: strangerMayErr } = await stranger
    .rpc("can_use_glyph", { p_glyph_id: soldGlyph.id, p_user: strangerId });
  check("a stranger may NOT use the purged seller's sold glyph",
    !strangerMayErr && strangerMay === false,
    strangerMayErr ? strangerMayErr.message : String(strangerMay));

  // The RPC is the helper; this is the path a real client takes.
  const { error: strangerPebbleErr } = await stranger.rpc("create_pebble", {
    payload: {
      name: `purge-test stranger steal ${runId}`,
      happened_at: new Date().toISOString(),
      intensity: 2,
      positiveness: 1,
      visibility: "private",
      emotion_id: emotion.id,
      glyph_id: soldGlyph.id,
    },
  });
  check("create_pebble rejects the stranger attaching it",
    strangerPebbleErr !== null &&
      strangerPebbleErr.message.includes("Glyph not usable by user"),
    strangerPebbleErr?.message ?? "the pebble was created");

  // …and the entitlement still MEANS something: the buyer keeps their access.
  const { data: buyerMay, error: buyerMayErr } = await buyer
    .rpc("can_use_glyph", { p_glyph_id: soldGlyph.id, p_user: buyerId });
  check("the buyer who paid may still use it",
    !buyerMayErr && buyerMay === true,
    buyerMayErr ? buyerMayErr.message : String(buyerMay));
```

The existing `keptGlyph` select must also fetch the new column. Change:

```typescript
  const { data: keptGlyph } = await admin
    .from("glyphs").select("user_id, strokes, name").eq("id", soldGlyph.id).maybeSingle();
```

to:

```typescript
  const { data: keptGlyph } = await admin
    .from("glyphs").select("user_id, strokes, name, is_system").eq("id", soldGlyph.id).maybeSingle();
```

- [ ] **Step 4: Update the file's header docblock**

The header's `STANDING RULE (#870)` paragraph says the sold glyph "is the definition of a system glyph". That is no longer true and would mislead. Replace that sentence — change:

```
 * row today: purge_account anonymizes it to user_id = null, which is the
 * definition of a system glyph, so every run used to hand the shared project
 * one more glyph offered to every user. Seed shapes the clients actually
 * decode, and clean up what outlives the purge.
```

to:

```
 * row today: purge_account anonymizes it to user_id = null. That used to be the
 * definition of a system glyph, so every run handed the shared project one more
 * glyph offered to every user for free (#872 — ownerless and system are now
 * separate, see glyphs.is_system). The fixture still outlives the purge by
 * design, so this script still has to remove it. Seed shapes the clients
 * actually decode, and clean up what outlives the purge.
```

Also update the summary line near the top — change `the buyer's glyph still renders (user_id = null + entitlement + delisted-but-approved submission)` to `the buyer's glyph still renders AND stays paid (user_id = null, is_system false, entitlement intact, delisted-but-approved submission)`.

- [ ] **Step 5: Run the purge harness**

```bash
cd /Users/alexis/code/pbbls && set -a && . ./.env && set +a && \
  npm run db:verify:purge --workspace=packages/supabase
```

Expected: every line `✓`, including the four new ones, and `failed=0`. This harness is slow (it creates and deletes four users); let it finish.

If `a stranger may NOT use the purged seller's sold glyph` fails with `true`, the migration's `can_use_glyph` did not take — re-check that section 4 of the migration actually applied.

- [ ] **Step 6: Lint and commit**

```bash
npm run lint --workspace=packages/supabase
git add packages/supabase/scripts/verify-account-purge.ts
git commit -m "test(db): assert a purged seller's sold glyph stays paid (#872)

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: Android — the only client that leaked

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/glyph/models/Glyph.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/glyph/models/GlyphMarket.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/glyph/services/GlyphService.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/glyph/services/GlyphMarketService.kt`
- Modify: `apps/android/app/src/test/kotlin/app/pbbls/android/features/glyph/services/GlyphServiceTest.kt`
- Create: `apps/android/app/src/test/kotlin/app/pbbls/android/features/glyph/services/GlyphMarketMineTabTest.kt`
- Modify: `apps/android/app/src/test/kotlin/app/pbbls/android/features/glyph/models/GlyphMarketDecodingTest.kt`

The two filters live inline inside `suspend` functions today, so nothing can test them. Both move to `companion object` pure functions, which is the pattern `GlyphService.withEntitled` and `normalizedName` already established in this exact file — not a refactor, the shape this codebase uses for testable service logic.

- [ ] **Step 1: Write the failing tests**

Append to `apps/android/app/src/test/kotlin/app/pbbls/android/features/glyph/services/GlyphServiceTest.kt`, inside the class, and extend the `glyph` helper to carry the flag:

```kotlin
    private fun glyph(
        id: String,
        userId: String? = null,
        isSystem: Boolean = false,
    ) = Glyph(id = id, strokes = emptyList(), viewBox = "0 0 200 200", userId = userId, isSystem = isSystem)

    @Test
    fun `attachable keeps own and system glyphs`() {
        val rows =
            listOf(
                glyph("own", userId = "me"),
                glyph("seed", isSystem = true),
                glyph("theirs", userId = "someone"),
            )
        assertEquals(listOf("own", "seed"), GlyphService.attachable(rows, "me").map { it.id })
    }

    @Test
    fun `attachable drops an ownerless glyph that is not system`() {
        // A purged seller's SOLD glyph: user_id null, is_system false (#872).
        // Keying off `userId == null` handed it to every user for free.
        val rows = listOf(glyph("purged"), glyph("seed", isSystem = true))
        assertEquals(listOf("seed"), GlyphService.attachable(rows, "me").map { it.id })
    }

    @Test
    fun `attachable keeps server order`() {
        val rows = listOf(glyph("seed", isSystem = true), glyph("own", userId = "me"))
        assertEquals(listOf("seed", "own"), GlyphService.attachable(rows, "me").map { it.id })
    }

    @Test
    fun `attachable with a null session keeps only system glyphs`() {
        val rows = listOf(glyph("own", userId = "me"), glyph("seed", isSystem = true))
        assertEquals(listOf("seed"), GlyphService.attachable(rows, null).map { it.id })
    }
```

Create `apps/android/app/src/test/kotlin/app/pbbls/android/features/glyph/services/GlyphMarketMineTabTest.kt`:

```kotlin
package app.pbbls.android.features.glyph.services

import app.pbbls.android.features.glyph.models.MineGlyphRow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [GlyphMarketService.mineTab] ordering and membership (M43 D7, #872).
 *
 * Android deliberately keeps system glyphs pickable in Mine — a named
 * deviation from iOS's `eq(user_id, me)`. The membership test is
 * `is_system`, NOT `user_id is null`: purge_account anonymizes a SOLD glyph
 * when its creator deletes their account, and that row must stay out of every
 * stranger's Mine tab.
 */
class GlyphMarketMineTabTest {
    private fun row(
        id: String,
        userId: String? = null,
        isSystem: Boolean = false,
    ) = MineGlyphRow(
        id = id,
        strokes = emptyList(),
        viewBox = "0 0 200 200",
        userId = userId,
        isSystem = isSystem,
    )

    @Test
    fun `own creations come first, then system glyphs`() {
        val rows =
            listOf(
                row("seed", isSystem = true),
                row("own", userId = "me"),
            )
        assertEquals(listOf("own", "seed"), GlyphMarketService.mineTab(rows, "me").map { it.id })
    }

    @Test
    fun `an ownerless non-system glyph is excluded`() {
        // The purged seller's sold glyph (#872) — free to everyone before the fix.
        val rows = listOf(row("own", userId = "me"), row("purged"))
        assertEquals(listOf("own"), GlyphMarketService.mineTab(rows, "me").map { it.id })
    }

    @Test
    fun `another user's glyph is excluded`() {
        val rows = listOf(row("theirs", userId = "someone"), row("seed", isSystem = true))
        assertEquals(listOf("seed"), GlyphMarketService.mineTab(rows, "me").map { it.id })
    }
}
```

Add to `apps/android/app/src/test/kotlin/app/pbbls/android/features/glyph/models/GlyphMarketDecodingTest.kt`, inside the class:

```kotlin
    @Test
    fun `mine row decodes is_system and defaults it to false when absent`() {
        val flagged =
            json.decodeFromString<MineGlyphRow>(
                """
                { "id": "g1", "strokes": [], "view_box": "0 0 200 200",
                  "user_id": null, "is_system": true }
                """.trimIndent(),
            )
        assertTrue(flagged.isSystem)
        assertTrue(flagged.toGlyph().isSystem)

        // Column-restricted embeds (the pebble-detail
        // `glyphs(id, name, strokes, view_box)` select) omit the key entirely.
        val absent =
            json.decodeFromString<MineGlyphRow>(
                """{ "id": "g2", "strokes": [], "view_box": "0 0 200 200" }""",
            )
        assertEquals(false, absent.isSystem)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd /Users/alexis/code/pbbls && export ANDROID_HOME="$HOME/Library/Android/sdk" && \
  npm run test --workspace=@pbbls/android
```

Expected: FAIL — compilation errors. `Glyph` has no `isSystem` parameter, `MineGlyphRow` has no `isSystem`, `GlyphService.attachable` and `GlyphMarketService.mineTab` are unresolved.

If `ANDROID_HOME` is not set the Gradle wrapper no-ops via `scripts/gradle-if-sdk.sh` and reports success without running anything — that is not a pass. Confirm the output shows actual test execution.

- [ ] **Step 3: Add `isSystem` to the models**

In `apps/android/app/src/main/kotlin/app/pbbls/android/features/glyph/models/Glyph.kt`, extend the KDoc and add the field:

```kotlin
/**
 * Full glyph model — mirrors iOS `Glyph.swift`. Stored in `public.glyphs`.
 *
 * [viewBox] is `"0 0 200 200"` for glyphs carved on iOS; imported web glyphs
 * may use other view-box values. [name] and [userId] are nullable and default
 * to `null`: a system glyph has no owner, and column-restricted selects
 * (e.g. the pebble-detail `glyphs(id, name, strokes, view_box)` embed) omit
 * `user_id` entirely — an absent key must still decode.
 *
 * [isSystem] is the first-party marker (#872) and defaults to `false` for the
 * same absent-key reason. It is NOT `userId == null`: `purge_account` keeps a
 * SOLD glyph when its creator deletes their account and anonymizes it, and that
 * row stays entitlement-gated. Filter pickers on this, never on `userId`.
 */
@Serializable
data class Glyph(
    val id: String,
    val name: String? = null,
    val strokes: List<GlyphStroke>,
    @SerialName("view_box")
    val viewBox: String,
    @SerialName("user_id")
    val userId: String? = null,
    @SerialName("is_system")
    val isSystem: Boolean = false,
)
```

In `apps/android/app/src/main/kotlin/app/pbbls/android/features/glyph/models/GlyphMarket.kt`, add the field to `MineGlyphRow` (after `userId`) and carry it through `toGlyph()`:

```kotlin
    @SerialName("user_id")
    val userId: String? = null,
    @SerialName("is_system")
    val isSystem: Boolean = false,
```

```kotlin
    fun toGlyph(): Glyph =
        Glyph(id = id, name = name, strokes = strokes, viewBox = viewBox, userId = userId, isSystem = isSystem)
```

`MarketGlyphRow` is deliberately left alone: it reads `v_glyph_market`, which is approved+listed listings only and never contains a seed, so it has no `is_system` column to select.

- [ ] **Step 4: Add the two pure filters**

In `apps/android/app/src/main/kotlin/app/pbbls/android/features/glyph/services/GlyphService.kt`, add to the `companion object`, after `withEntitled`:

```kotlin
        /**
         * Own ∪ system — the client half of `can_use_glyph`'s accepted set
         * (entitled glyphs arrive separately, through [withEntitled]).
         *
         * Keyed on [Glyph.isSystem], never on `userId == null` (#872): an
         * ownerless glyph is a glyph whose creator deleted their account, and
         * if it was ever sold the buyers' entitlements are the only claim on
         * it. Offering it here would let the server reject the attach with
         * SQLSTATE 42501 at best, and hand out paid artwork at worst.
         */
        fun attachable(
            rows: List<Glyph>,
            me: String?,
        ): List<Glyph> = rows.filter { it.isSystem || (me != null && it.userId == me) }
```

Replace the filter inside `list()`:

```kotlin
                        }.decodeList<Glyph>()
                        .filter { it.userId == null || it.userId == me }
```

with:

```kotlin
                        }.decodeList<Glyph>()
                        .let { attachable(it, me) }
```

and update that `select` column list to fetch the flag:

```kotlin
                        .select(Columns.raw("id, name, strokes, view_box, user_id, is_system")) {
```

Also update the KDoc on `list()` — change `own + system (`user_id is null`) +` to `own + system (`is_system`, #872) +`.

The entitlement select in the same function embeds `glyphs(id, name, strokes, view_box, user_id)`; add `is_system` there too so an entitled glyph decodes with the right flag:

```kotlin
                        .select(Columns.raw("glyphs(id, name, strokes, view_box, user_id, is_system)")) {
```

`create()` and `updateName()` select back `id, name, strokes, view_box, user_id`. Add `is_system` to both so a freshly carved glyph round-trips the field rather than defaulting it:

```kotlin
                select(Columns.raw("id, name, strokes, view_box, user_id, is_system"))
```

In `apps/android/app/src/main/kotlin/app/pbbls/android/features/glyph/services/GlyphMarketService.kt`, add a `companion object` at the end of the class body (after `requireUserId`):

```kotlin
    companion object {
        /**
         * The Mine tab's membership and order: the caller's own creations
         * (server order, newest first) then system glyphs — design D7, the
         * named deviation from iOS's `eq(user_id, me)`.
         *
         * Keyed on [MineGlyphRow.isSystem], never on `userId == null` (#872):
         * `purge_account` anonymizes a SOLD glyph when its creator deletes
         * their account, and that row must not appear in a stranger's Mine tab
         * as a free first-party seed.
         */
        fun mineTab(
            rows: List<MineGlyphRow>,
            me: String,
        ): List<MineGlyphRow> {
            val (own, rest) = rows.partition { it.userId == me }
            return own + rest.filter { it.isSystem }
        }
    }
```

Replace the body of `listMine()`'s partition — change:

```kotlin
        val (own, rest) = rows.partition { it.userId == me }
        val system = rest.filter { it.userId == null }
        return (own + system).map { row ->
```

to:

```kotlin
        return mineTab(rows, me).map { row ->
```

and add `is_system` to that function's `Columns.raw`:

```kotlin
                        "id, name, strokes, view_box, user_id, is_system, created_at, glyph_submissions(price, status, listed)",
```

Update the `listMine()` KDoc — change `Android keeps system glyphs pickable (design D7, a named deviation from iOS's `eq(user_id, me)` which silently drops them)` to `Android keeps system glyphs pickable (design D7, a named deviation from iOS's `eq(user_id, me)` which silently drops them) — membership is `is_system`, not a null owner (#872)`.

`listOwned()` embeds `glyphs(id, name, strokes, view_box, user_id, created_at)`; add `is_system`:

```kotlin
                Columns.raw("price_paid, created_at, glyphs(id, name, strokes, view_box, user_id, is_system, created_at)"),
```

- [ ] **Step 5: Run the tests to verify they pass**

```bash
cd /Users/alexis/code/pbbls && export ANDROID_HOME="$HOME/Library/Android/sdk" && \
  npm run test --workspace=@pbbls/android
```

Expected: PASS. All of `GlyphServiceTest`, `GlyphMarketMineTabTest` and `GlyphMarketDecodingTest` green, and no other suite regressed.

- [ ] **Step 6: Lint and build**

```bash
cd /Users/alexis/code/pbbls && export ANDROID_HOME="$HOME/Library/Android/sdk" && \
  npm run lint --workspace=@pbbls/android && \
  npm run build --workspace=@pbbls/android
```

Expected: ktlint clean, Gradle build succeeds. ktlint is strict about trailing commas and line length in this repo — if it reports violations in the lines above, fix the formatting, not the logic.

- [ ] **Step 7: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/features/glyph \
        apps/android/app/src/test/kotlin/app/pbbls/android/features/glyph
git commit -m "fix(android): key the glyph picker off is_system, not a null owner (#872)

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: Cross-surface check, follow-ups, and the PR

- [ ] **Step 1: Confirm iOS and web genuinely need no change**

The standing rule is that a contract change on one surface means *checking* the other three, and the check has to be real. Run:

```bash
cd /Users/alexis/code/pbbls && \
  grep -rn "user_id" apps/ios/Pebbles/Features/Glyph/Services/ && \
  grep -n 'from("glyphs")' apps/web/lib/data/supabase-provider.ts
```

Expected, and the reason each is safe:
- iOS `GlyphMarketService.listMine()` → `.eq("user_id", value: userId)` — an ownerless row can never match, so a purged seller's glyph was never offered.
- iOS `GlyphMarketService.listCommunity()` → `.neq("user_id", value: userId)` reading `v_glyph_market`, which filters `status = 'approved' and listed`; `purge_account` sets `listed = false`.
- iOS `GlyphService.list()` is unfiltered but is no longer used by the picker (#547 moved it to Mine ∪ Owned); `GlyphsListView` uses it for the gallery, where reading is the point.
- web `supabase-provider.ts` → `.eq("user_id", this.userId)` for `marks`, `v_glyph_market` with `owned = true` for `entitledMarks`.

Record the result in the PR body. If any of these has changed since the spec was written, stop and report rather than editing them silently.

- [ ] **Step 2: File the two follow-up issues**

```bash
gh issue create --title "[Bug] glyphs.is_custom drifts when a creator purges their account" \
  --label bug --label db --milestone "M43 · Glyph Studio & Store" \
  --body "Split from #872.

\`glyphs.is_custom\` is \`generated always as (user_id is not null) stored\` (\`20260501000006_glyphs_is_custom.sql\`). \`purge_account\` anonymizes a sold glyph to \`user_id = null\`, so \`is_custom\` flips true → false and the analytics \"% of pebbles with a custom glyph\" metric reclassifies bought artwork as system-seeded.

Same root cause as #872, which fixed the user-facing half by adding \`glyphs.is_system\`. The honest definition is now \`is_custom = not is_system\`, but redefining a generated column means \`DROP COLUMN\`, which cascades into \`v_analytics_pebble_volume_daily\`, \`v_analytics_pebble_enrichment_daily\` and their RPCs — a separate change with its own review surface.

Admin-facing analytics only. No user impact.

Affected readers: \`get_pebble_volume_series\`, \`get_pebble_enrichment\`, and both views above."
```

```bash
gh issue create --title "[Bug] profiles.glyph_id has no can_use_glyph guard" \
  --label bug --label db --milestone "M43 · Glyph Studio & Store" \
  --body "Found while fixing #872.

\`can_use_glyph\` (\`20260712000000\`) gates glyph attachment on two of the three surfaces that reference a glyph:

- \`pebbles.glyph_id\` — guarded inside \`create_pebble\` / \`update_pebble\`
- \`souls.glyph_id\` — guarded by the \`souls_glyph_usable\` trigger
- \`profiles.glyph_id\` — **not guarded at all**

\`profiles\` is written directly by every client, so a profile glyph can be set to any glyph the user can *read*. Under the browse-friendly \`glyphs_select\` policy that includes every approved community glyph, bought or not — so a user can wear a glyph they never paid for as their profile mark.

#872 neither widened nor narrowed this; it is pre-existing. The fix is the same shape as the souls trigger: a \`before insert or update of glyph_id on public.profiles\` trigger calling \`can_use_glyph(new.glyph_id, new.user_id)\`.

Note the ordering constraint: \`purge_account\` keeps a glyph that another user's profile points at, so the trigger must not fire on the purge's own writes."
```

Note both numbers — they go in the PR body as issue references, never as `F-…` finding ids.

- [ ] **Step 3: Move the Arkaik status**

The map is hosted. If the `arkaik-mcp` tools are unavailable, say so and stop — do not touch `docs/arkaik/bundle.json`.

Find the glyph-market nodes and check whether any acceptance or view covering the glyph picker / marketplace needs moving to `development`:

```
mcp__arkaik-mcp__list_nodes  (filter for glyph / market / picker)
```

This is a bug fix inside shipped behaviour, so the likely answer is that nothing moves — the Arkaik GitHub App marks `development` on PR open and `releasing` on merge by itself. Do not set anything to `live`.

- [ ] **Step 4: Push and open the PR**

```bash
git push -u origin fix/872-glyph-system-marker
```

```bash
gh pr create --title "fix(db): separate system glyphs from ownerless ones" \
  --label fix --label db --label core --milestone "M43 · Glyph Studio & Store" \
  --body-file -
```

PR body (fill `<is_custom issue>` and `<profiles issue>` with the numbers from Step 2):

```markdown
Resolves #872

`user_id is null` meant two incompatible things: a first-party seed, and a glyph whose creator deleted their account. `purge_account` deliberately keeps a sold glyph and anonymizes it (buyers hold entitlements), so the moment a seller deleted their account, artwork other people paid karma for became free for everyone on Android and indistinguishable from a seed.

## The change

- **`glyphs.is_system`** — an explicit marker, backfilled for genuinely first-party rows (`user_id is null` AND no submission AND no entitlement, so a seller who purges before deploy is excluded automatically), pinned against client writes by `enforce_glyph_system_flag`.
- **`can_use_glyph`** and the **`glyphs_select`** policy key off `is_system` instead of a null owner. The read stays open for the purged glyph through the approved-submission arm (`purge_account` flips `listed`, not `status`), so buyers' pebbles and souls keep rendering. Only the attach closes.
- **`admin_set_domain_glyph`** and **`admin_set_achievement_glyph`** mint their glyphs `is_system = true`. `publish_admin_glyph` deliberately does not — it writes `user_id = v_user`, so an admin-published market glyph is owned and sold like any other.
- **Android** swaps both `user_id == null` filters for `is_system`. The D7 deviation stays a deviation: Android still offers system glyphs in Mine and the picker.

## Cross-surface check

iOS and web needed no change, and the check was real: iOS `listMine()` is `.eq("user_id", me)` and its picker is Mine ∪ Owned (#547); iOS `listCommunity()` reads `v_glyph_market`, which excludes the delisted row; web's provider is `.eq("user_id", this.userId)` plus `v_glyph_market` with `owned = true`. None of them ever offered an ownerless glyph.

## Proof

- **`verify-glyph-usability.ts`** (new, anon-key, wired into `db:verify` so CI gates it) — an unowned glyph is unattachable through both `create_pebble` and the souls trigger; the guard did not over-block; `is_system` is not self-settable on insert *or* update, checked by the error **and** a re-read.
- **`verify-account-purge.ts`** gains the assertion the issue names: after the seller purges, a stranger gets `false` from `can_use_glyph` and a 42501 from `create_pebble`, while the buyer who paid still gets `true`, and the kept row reads `is_system = false`.

Backfill verified against the linked project: 20 ownerless glyphs, all first-party, all marked; zero ownerless-but-not-system rows remain.

## Key files

- `packages/supabase/supabase/migrations/20260919090000_glyph_system_marker.sql`
- `packages/supabase/scripts/verify-glyph-usability.ts` (new)
- `packages/supabase/scripts/verify-account-purge.ts`
- `apps/android/.../glyph/models/{Glyph,GlyphMarket}.kt`
- `apps/android/.../glyph/services/{GlyphService,GlyphMarketService}.kt`
- `docs/superpowers/specs/2026-09-19-glyph-system-marker-design.md`

## Follow-ups (out of scope, deliberately)

- #<is_custom issue> — `glyphs.is_custom` is generated from `user_id` and drifts the same way. Admin analytics only; redefining a generated column cascades into two views and their RPCs.
- #<profiles issue> — `profiles.glyph_id` has no `can_use_glyph` guard, unlike `pebbles` and `souls`. Pre-existing and untouched by this change.

## Lab Note (EN/FR)

```yaml
species: feature
platform: android
status: in_progress
published: false
en:
  title: "The glyphs you paid for stay yours"
  summary: "A glyph you swapped karma for now stays properly yours, even if the artist who made it leaves Pebbles. Their work no longer quietly becomes a free default for everyone."
fr:
  title: "Les glyphes que tu as payés restent les tiens"
  summary: "Un glyphe obtenu contre du karma reste bien à toi, même si l'artiste qui l'a dessiné quitte Pebbles. Son travail ne devient plus discrètement un modèle gratuit pour tout le monde."
nodes: []
suggested:
  molecule: pbbls
  type: fix
  tags: [changelog]
```
```

Before submitting, replace `nodes: []` with the real node ids read off the hosted map in Step 3 (`list_nodes` / `get_node`) — the glyph picker view and the marketplace flow if they exist. An id that matches nothing is dropped from the changelog entry and reported in the App's delivery response. If Step 3 found no relevant node, omit the `nodes:` key entirely rather than shipping an empty list.

- [ ] **Step 5: Confirm CI is green**

```bash
gh pr checks --watch
```

Expected: `web.yml` (ESLint + Vitest, runs on every PR), `supabase.yml` (typecheck + migration replay + the seven contract harnesses), `android.yml` (build + lint) all pass.

`supabase.yml` replays the full migration chain on a local stack. If it fails on `20260919090000` there, the migration is not replayable from scratch — most likely the backfill or the trigger assumes rows the fresh stack does not have. Report it rather than patching the applied remote migration.

---

## Self-review notes

- **Spec coverage:** §2/D1–D2 → Task 2 Step 1 (§1–2); D3 → Task 2 Step 1 (§3) + Task 1 assertions 4; D4 → Task 2 Step 1 (§6); D5 → Task 2 Step 1 (§4–5); D6 → Task 5 Step 1; D7 → Task 4. §3 → Task 4. §4 → Tasks 1 and 3. §5 → Task 5 Step 2.
- **Naming consistency:** `GlyphService.attachable(rows, me)` and `GlyphMarketService.mineTab(rows, me)` are used with those exact signatures in both the tests (Task 4 Step 1) and the implementations (Task 4 Step 4). `me` is `String?` on `attachable` (the session may be absent) and `String` on `mineTab` (`requireUserId()` already threw).
- **Assertion count:** Task 2 Step 5 expects `passed=16`. Count it from the harness rather than trusting the number — if the harness as written yields a different total, that total is correct and the expectation line is what is wrong.
