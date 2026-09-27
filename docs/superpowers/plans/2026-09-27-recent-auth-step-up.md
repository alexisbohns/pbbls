# Recent Sign-in Step-up Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Require a sign-in from the last 10 minutes before account deletion, password change and turning the public profile on (server check shipped with enforcement off; Android client re-auth), plus a "sign out of all devices" entry on Android.

**Architecture:** GoTrue stamps every real authentication into the JWT `amr` claim, and a token refresh keeps those stamps. A pure SQL evaluator reads them; `assert_recent_auth()` raises `reauth_required` when enforcement is on and the stamp is stale. It is called by a `profiles` trigger (public_profile false→true) and by the `delete-account` edge function (→ HTTP 428). Android decodes the same claim client-side to decide when to show a "Confirm it's you" dialog, re-signs in (password or Google), then runs the action.

**Tech Stack:** Postgres/plpgsql migrations, Supabase Edge Functions (Deno), Deno verify harness, Kotlin + Jetpack Compose, supabase-kt 3.8.0, JUnit4 + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-27-recent-auth-step-up-design.md` · **Issues:** #976 (this plan), #977 (enforcement, later)

## Global rules for every task

- Worktree: `/Users/alexis/code/pbbls/.claude/worktrees/saf-android-03-reauth`. Run every command from there (absolute paths). Never `cd` to `/Users/alexis/code/pbbls`.
- Commits: conventional, lowercase, no period, ending with the trailer line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Android commands need `export ANDROID_HOME=$HOME/Library/Android/sdk` first. Unit tests: `cd apps/android && ./gradlew :app:testDebugUnitTest --tests '<FQN>'`. Lint: `npm run lint --workspace=@pbbls/android` (ktlint) **and** `cd apps/android && ./gradlew :app:lintDebug`.
- Do not touch `docs/arkaik/**`. Do not write any `F-…` id anywhere in code, commits, or docs.
- Never run `supabase db push` or `supabase functions deploy`: those write to production and the controller runs them after asking the maintainer.
- Android screenshot tests are a CI-only gate. Do not re-baseline locally.

## File map

**Part 1: branch `feat/976-recent-auth-db`** (already checked out)
- Create `packages/supabase/supabase/migrations/20260927120000_recent_auth.sql`: evaluator, enforcement switch, assert, and trigger.
- Modify `packages/supabase/supabase/functions/delete-account/index.ts`: call `assert_recent_auth` before purge and map it to 428.
- Create `packages/supabase/scripts/verify-recent-auth.ts`: the harness.
- Modify `packages/supabase/package.json` (the `db:verify:recent-auth` script, and add it to `db:verify`).
- Modify `.github/workflows/supabase.yml` (one harness step).
- Modify `packages/supabase/types/database.ts`: regenerated after the maintainer-approved push.

**Part 2: branch `feat/976-android-global-signout`** (stacked on Part 1)
- Modify `apps/android/app/src/main/kotlin/app/pbbls/android/core/data/SupabaseService.kt`: `signOut(everywhere)`.
- Modify `apps/android/app/src/test/kotlin/app/pbbls/android/testing/FakeSupabaseService.kt`.
- Modify `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/SettingsViewModel.kt` and `SettingsScreen.kt`.
- Modify `apps/android/app/src/main/res/values/strings.xml` and `values-fr/strings.xml`.
- Test `apps/android/app/src/test/kotlin/app/pbbls/android/features/profile/SettingsViewModelTest.kt`.

**Part 3: branch `feat/976-android-reauth`** (stacked on Part 2)
- Create `apps/android/app/src/main/kotlin/app/pbbls/android/core/data/RecentAuth.kt`: the pure freshness check plus the two exceptions and `isReauthRequired()`.
- Create `apps/android/app/src/test/kotlin/app/pbbls/android/core/data/RecentAuthTest.kt`.
- Modify `SupabaseService.kt` (`reauthenticate`, `reauthenticateWithGoogle`), `ProfileService.kt` (`deleteAccount` 428 mapping), `FakeSupabaseService.kt`, `TestSessions.kt`.
- Create `apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/ReauthDialog.kt`.
- Modify `SettingsViewModel.kt`, `SettingsScreen.kt`, both `strings.xml`, `SettingsViewModelTest.kt`.

---

## PART 1 — server (branch `feat/976-recent-auth-db`)

### Task 1: Recent-auth migration

**Files:**
- Create: `packages/supabase/supabase/migrations/20260927120000_recent_auth.sql`

- [ ] **Step 1: Write the migration**

```sql
-- Migration: recent sign-in (step-up auth) for high-harm account actions (#976)
--
-- Account deletion and turning the public profile on used to need nothing but a
-- live session. A copied token or an unlocked phone could therefore destroy or
-- expose an account. This migration adds the server half of a "recent sign-in"
-- requirement:
--
--   recent_auth_ok(amr, max_age)  pure evaluator over the JWT `amr` claim
--   recent_auth_enforced()        the rollout switch — FALSE in this migration
--   assert_recent_auth()          raises `reauth_required` when enforced + stale
--   profiles_public_profile_recent_auth   trigger: public_profile false→true
--
-- Why `amr`: GoTrue stamps each real authentication (password, oauth, otp, …)
-- into the session's `amr` claim with an epoch-seconds timestamp, and a token
-- REFRESH keeps the original stamps. So "the newest amr timestamp is recent"
-- means "this person proved the credential recently", which a stolen refresh
-- token cannot fake. No table, no token plumbing, and every surface reads it the
-- same way.
--
-- Rollout: enforcement ships OFF because web and shipped iOS builds do not
-- re-authenticate yet; turning it on here would break account deletion and the
-- public flip for them. #977 re-emits recent_auth_enforced() as TRUE once every
-- client prompts first. A function rather than a settings row so the flip is a
-- reviewed migration and no client role can toggle it.
--
-- The delete-account edge function calls assert_recent_auth() through the
-- caller's forwarded JWT before it purges (index.ts). Password change stays
-- with GoTrue (`secure_password_change`, also #977).

-- ---------------------------------------------------------------------------
-- 1. The evaluator. Pure over its arguments (it reads now(), hence STABLE).
--
-- True when any element carries a numeric `timestamp` no older than p_max_age.
-- CASE, not AND, guards jsonb_array_elements: Postgres does not promise to
-- short-circuit AND, and jsonb_array_elements raises on a non-array.
-- ---------------------------------------------------------------------------
create function public.recent_auth_ok(p_amr jsonb, p_max_age interval)
returns boolean
language sql
stable
set search_path = public
as $$
  select case
    when jsonb_typeof(p_amr) = 'array' then exists (
      select 1
      from jsonb_array_elements(p_amr) as e
      where jsonb_typeof(e) = 'object'
        and jsonb_typeof(e -> 'timestamp') = 'number'
        and to_timestamp((e ->> 'timestamp')::double precision) >= now() - p_max_age
    )
    else false
  end;
$$;

-- ---------------------------------------------------------------------------
-- 2. The rollout switch. #977 re-emits this returning true.
-- ---------------------------------------------------------------------------
create function public.recent_auth_enforced()
returns boolean
language sql
immutable
set search_path = public
as $$
  select false;
$$;

-- ---------------------------------------------------------------------------
-- 3. The assertion. security INVOKER: auth.jwt() must be the caller's claims.
-- The message is exactly `reauth_required` — every client maps that one token.
-- The 10-minute window is duplicated on Android (RecentAuth.WINDOW); change
-- both together.
-- ---------------------------------------------------------------------------
create function public.assert_recent_auth()
returns void
language plpgsql
stable
set search_path = public
as $$
begin
  if public.recent_auth_enforced()
     and not public.recent_auth_ok(auth.jwt() -> 'amr', interval '10 minutes') then
    raise exception 'reauth_required'
      using hint = 'Sign in again (password or provider) and retry within 10 minutes.';
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- 4. The trigger. Same exemption as profiles_privileged_guard (20260902090000):
-- only PostgREST client roles are gated. Definer paths (consent withdrawal,
-- moderation) and the service role only ever turn the flag OFF anyway, and
-- the WHEN clause already skips everything but false→true.
-- ---------------------------------------------------------------------------
create function public.enforce_public_profile_recent_auth()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if current_user in ('authenticated', 'anon') then
    perform public.assert_recent_auth();
  end if;
  return new;
end;
$$;

create trigger profiles_public_profile_recent_auth
  before update of public_profile on public.profiles
  for each row
  when (old.public_profile = false and new.public_profile = true)
  execute function public.enforce_public_profile_recent_auth();

-- ---------------------------------------------------------------------------
-- 5. Grants. recent_auth_ok is exposed so the harness can prove the evaluator
-- directly; it reads nothing but its arguments.
-- ---------------------------------------------------------------------------
revoke all on function public.recent_auth_ok(jsonb, interval) from public, anon;
revoke all on function public.recent_auth_enforced()          from public, anon;
revoke all on function public.assert_recent_auth()            from public, anon;
grant execute on function public.recent_auth_ok(jsonb, interval) to authenticated;
grant execute on function public.recent_auth_enforced()          to authenticated;
grant execute on function public.assert_recent_auth()            to authenticated;
```

- [ ] **Step 2: Prove the evaluator on a scratch Postgres (no Docker)**

Only `recent_auth_ok` is self-contained. Run it on the native Homebrew Postgres if it's available (`which psql`). Skip this step if it isn't, and say so in the report: CI's migration replay covers the whole file.

```bash
createdb recent_auth_scratch 2>/dev/null; psql -X -v ON_ERROR_STOP=1 recent_auth_scratch <<'SQL'
create schema if not exists public;
create or replace function public.recent_auth_ok(p_amr jsonb, p_max_age interval)
returns boolean language sql stable as $$
  select case when jsonb_typeof(p_amr) = 'array' then exists (
    select 1 from jsonb_array_elements(p_amr) as e
    where jsonb_typeof(e) = 'object' and jsonb_typeof(e -> 'timestamp') = 'number'
      and to_timestamp((e ->> 'timestamp')::double precision) >= now() - p_max_age)
  else false end; $$;
select
  public.recent_auth_ok(jsonb_build_array(jsonb_build_object('method','password','timestamp', extract(epoch from now())::bigint)), '10 minutes') as fresh_true,
  public.recent_auth_ok(jsonb_build_array(jsonb_build_object('method','password','timestamp', extract(epoch from now() - interval '11 minutes')::bigint)), '10 minutes') as stale_false,
  public.recent_auth_ok('[]', '10 minutes') as empty_false,
  public.recent_auth_ok(null, '10 minutes') as null_false,
  public.recent_auth_ok('{"method":"password"}', '10 minutes') as object_false,
  public.recent_auth_ok('[{"method":"password","timestamp":"123"}]', '10 minutes') as string_ts_false,
  public.recent_auth_ok('[1, "x", null]', '10 minutes') as junk_false;
SQL
dropdb recent_auth_scratch
```

Expected: `fresh_true = t`, and every other column `f`. No error.

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/supabase/migrations/20260927120000_recent_auth.sql
git commit -m "feat(db): add a recent sign-in check with enforcement off

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 2: `delete-account` asserts recent auth

**Files:**
- Modify: `packages/supabase/supabase/functions/delete-account/index.ts`

- [ ] **Step 1: Add the assertion between `getUser` and the purge**

In the header comment, add this paragraph after the resume matrix (before `If a user's JWT expires…`):

```ts
 * Recent sign-in (#976): before step 1 the caller's own token must pass
 * `assert_recent_auth()` (a sign-in within the last 10 minutes, read from the
 * JWT `amr` claim). It runs on the auth-FORWARDED client so `auth.jwt()` is the
 * caller's claims, never the service role's. A refusal is 428
 * `{error:"reauth_required"}`: Precondition Required, because clients read
 * 401/403 as a dead session and must instead prompt a re-auth and retry. Ships
 * as a no-op until `recent_auth_enforced()` flips (#977).
```

Then, right after the `if (userError || !userId) { … }` block and before `const admin = createAdminClient();`, insert:

```ts
  // Recent sign-in gate — before anything irreversible (see header).
  const { error: recentAuthError } = await authClient.rpc("assert_recent_auth");
  if (recentAuthError) {
    if (recentAuthError.message === "reauth_required") {
      return json({ error: "reauth_required" }, 428);
    }
    console.error("delete-account: assert_recent_auth failed:", recentAuthError);
    return json({ error: `recent auth check failed: ${recentAuthError.message}` }, 500);
  }
```

- [ ] **Step 2: Typecheck**

Run: `cd packages/supabase && deno check supabase/functions/delete-account/index.ts`
Expected: `Check …index.ts` with no errors. (If `deno check` fails on remote imports, run the workspace typecheck the `supabase.yml` "checks" job uses instead: find it with `grep -n "deno check\|deno lint" .github/workflows/supabase.yml`.)

- [ ] **Step 3: Commit**

```bash
git add packages/supabase/supabase/functions/delete-account/index.ts
git commit -m "feat(db): gate delete-account on a recent sign-in

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 3: `verify-recent-auth.ts` harness

**Files:**
- Create: `packages/supabase/scripts/verify-recent-auth.ts`
- Modify: `packages/supabase/package.json`
- Modify: `.github/workflows/supabase.yml`

- [ ] **Step 1: Write the harness**

```ts
#!/usr/bin/env -S deno run --allow-env --allow-net
/**
 * Acceptance test for the recent sign-in check (#976) — runs against the
 * REMOTE project.
 *
 * `assert_recent_auth()` reads the caller's JWT `amr` claim. It is called by
 * the delete-account edge function and by the profiles trigger on
 * public_profile false→true. What this proves:
 *
 *   1. The evaluator `recent_auth_ok` is right over crafted amr values —
 *      fresh, stale, mixed, empty, null, and malformed.
 *   2. A token straight out of sign-in carries an amr stamp the evaluator
 *      accepts (the real GoTrue shape, printed for cross-surface tests).
 *   3. Neither gate over-blocks a fresh session: the public flip round-trips
 *      and delete-account succeeds (it is also the cleanup).
 *
 * The stale-token REFUSAL cannot be asserted while `recent_auth_enforced()` is
 * false (#976 ships it off). #977 adds a nightly-only case that waits past
 * the window and expects `reauth_required` from both gates.
 *
 * Run:
 *   SUPABASE_URL=... SUPABASE_ANON_KEY=... \
 *     deno run --allow-env --allow-net packages/supabase/scripts/verify-recent-auth.ts
 *
 * Needs NO service-role key: it signs up a throwaway user and deletes it
 * through the real delete-account edge function. Cleanup runs even on failure.
 * Exits non-zero if any assertion fails.
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
const password = `Recent-${crypto.randomUUID()}`;
const handle = `rav${runId}`;

type TestUser = { client: SupabaseClient; id: string; token: string };

async function signUp(): Promise<TestUser> {
  const client = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
  const email = `recent-auth-verify-${runId}@example.test`;
  const { data, error } = await client.auth.signUp({ email, password });
  if (error || !data.session || !data.user) {
    throw new Error(`signUp: ${error?.message ?? "no session (email confirmations on?)"}`);
  }
  return { client, id: data.user.id, token: data.session.access_token };
}

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

/** Base64url JWT payload — the harness reads what GoTrue actually issued. */
function jwtPayload(token: string): Record<string, unknown> {
  const part = token.split(".")[1];
  const b64 = part.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(part.length / 4) * 4, "=");
  return JSON.parse(atob(b64));
}

const nowS = () => Math.floor(Date.now() / 1000);

let user: TestUser | null = null;
let deleted = false;

try {
  user = await signUp();
  const c = user.client;

  // -------------------------------------------------------------------------
  // 1. The evaluator over crafted amr values.
  // -------------------------------------------------------------------------
  const cases: Array<[string, unknown, boolean]> = [
    ["fresh password stamp", [{ method: "password", timestamp: nowS() }], true],
    ["fresh oauth stamp", [{ method: "oauth", timestamp: nowS() - 60 }], true],
    ["11-minute-old stamp", [{ method: "password", timestamp: nowS() - 660 }], false],
    ["old + fresh mixed", [{ method: "password", timestamp: nowS() - 7200 }, { method: "oauth", timestamp: nowS() }], true],
    ["empty array", [], false],
    ["null", null, false],
    ["object, not array", { method: "password", timestamp: nowS() }, false],
    ["element without timestamp", [{ method: "password" }], false],
    ["string timestamp", [{ method: "password", timestamp: String(nowS()) }], false],
    ["junk elements", [1, "x", null], false],
  ];
  for (const [label, amr, expected] of cases) {
    const { data, error } = await c.rpc("recent_auth_ok", { p_amr: amr, p_max_age: "10 minutes" });
    check(`recent_auth_ok: ${label} → ${expected}`, !error && data === expected, error?.message ?? String(data));
  }

  // -------------------------------------------------------------------------
  // 2. A real sign-in token carries a stamp the evaluator accepts.
  // -------------------------------------------------------------------------
  const payload = jwtPayload(user.token);
  // Printed verbatim so client tests can pin the real GoTrue shape.
  console.log(`… amr from a real sign-up token: ${JSON.stringify(payload.amr)}`);
  check("the sign-up token has an amr array", Array.isArray(payload.amr), JSON.stringify(payload.amr));
  const { data: ownOk, error: ownErr } = await c.rpc("recent_auth_ok", {
    p_amr: payload.amr,
    p_max_age: "10 minutes",
  });
  check("the real token's own amr is fresh", !ownErr && ownOk === true, ownErr?.message);

  const { error: assertErr } = await c.rpc("assert_recent_auth");
  check("assert_recent_auth passes a fresh session", !assertErr, assertErr?.message);

  // -------------------------------------------------------------------------
  // 3. The trigger does not over-block the public flip.
  // -------------------------------------------------------------------------
  const { error: handleErr } = await c.rpc("set_handle", { p_handle: handle });
  check("claiming a handle works", !handleErr, handleErr?.message);

  const { error: onErr } = await c.from("profiles").update({ public_profile: true }).eq("user_id", user.id);
  const { data: onRow } = await c.from("profiles").select("public_profile").eq("user_id", user.id).single();
  check("a fresh session can turn the public profile on", !onErr && onRow?.public_profile === true, onErr?.message);

  const { error: offErr } = await c.from("profiles").update({ public_profile: false }).eq("user_id", user.id);
  const { data: offRow } = await c.from("profiles").select("public_profile").eq("user_id", user.id).single();
  check("turning it off is never gated", !offErr && offRow?.public_profile === false, offErr?.message);

  // -------------------------------------------------------------------------
  // 4. delete-account passes the gate for a fresh session (this is cleanup).
  // -------------------------------------------------------------------------
  const res = await deleteAccount(user.token);
  deleted = res.status === 200;
  check("delete-account accepts a fresh session", deleted, `status ${res.status}: ${await res.text()}`);
} catch (err) {
  failed += 1;
  console.error(`✗ aborted: ${err instanceof Error ? err.message : String(err)}`);
} finally {
  if (user && !deleted) {
    const res = await deleteAccount(user.token).catch(() => null);
    console.log(`… cleanup: ${res ? res.status : "FAILED — remove recent-auth-verify-* manually"}`);
  }
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed > 0 ? 1 : 0);
```

Before relying on the `set_handle` call, confirm its parameter name: `grep -n "function public.set_handle" packages/supabase/supabase/migrations/*.sql`. The Android client sends `p_handle`, so `p_handle` is expected.

- [ ] **Step 2: Wire the script**

In `packages/supabase/package.json`, add after the `db:verify:glyph` line:

```json
    "db:verify:recent-auth": "deno run --allow-env --allow-net scripts/verify-recent-auth.ts",
```

and append ` && npm run db:verify:recent-auth` to the end of the `db:verify` command string.

In `.github/workflows/supabase.yml`, after the "Content reports (#831 UGC report path)" step (the one running `verify-harness.sh "reports"`), add:

```yaml
      # The recent sign-in check (#976). Anon-only: it proves the evaluator and
      # that neither gate over-blocks a fresh session. The stale-token refusal
      # needs enforcement on and lands with #977.
      - name: Recent sign-in check (#976)
        if: '!cancelled()'
        run: .github/scripts/verify-harness.sh "recent-auth" db:verify:recent-auth
```

- [ ] **Step 3: Lint the harness**

Run: `cd packages/supabase && deno check scripts/verify-recent-auth.ts && deno lint scripts/verify-recent-auth.ts`
Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add packages/supabase/scripts/verify-recent-auth.ts packages/supabase/package.json .github/workflows/supabase.yml
git commit -m "test(db): verify the recent sign-in check against the linked project

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 4: Push, deploy, types, harness run (CONTROLLER ONLY, after asking the maintainer)

These are production writes. The controller asks before running them.

- [ ] **Step 1:** `npm run db:push --workspace=packages/supabase`. Expected: applies `20260927120000_recent_auth.sql`.
- [ ] **Step 2:** `cd packages/supabase && npx supabase functions deploy delete-account`.
- [ ] **Step 3:** `npm run db:types:remote --workspace=packages/supabase`, then `git diff --stat packages/supabase/types/database.ts`. Expected: only the three new functions added, file not truncated.
- [ ] **Step 4:** `set -a; . /Users/alexis/code/pbbls/.env; set +a; npm run db:verify:recent-auth --workspace=packages/supabase`. Expected: `failed=0`. Record the printed `amr` line for Task 6.
- [ ] **Step 5:** Also run `db:verify:public-profile` and `db:verify:guard`. Expected: green (they flip public_profile and delete users with fresh sessions).
- [ ] **Step 6:** Commit the types: `git add packages/supabase/types/database.ts && git commit -m "chore(db): regenerate types for the recent sign-in check"` (with the trailer).

---

## PART 2 — Android: sign out of all devices (branch `feat/976-android-global-signout`)

The controller creates the branch stacked on Part 1 (`gh stack` — see the `gh-stack` skill) before this task.

### Task 5: "Sign out of all devices"

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/core/data/SupabaseService.kt`
- Modify: `apps/android/app/src/test/kotlin/app/pbbls/android/testing/FakeSupabaseService.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/SettingsViewModel.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/SettingsScreen.kt`
- Modify: `apps/android/app/src/main/res/values/strings.xml`, `apps/android/app/src/main/res/values-fr/strings.xml`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/profile/SettingsViewModelTest.kt`

- [ ] **Step 1: Write the failing tests** (append to `SettingsViewModelTest`, in a new `// MARK: - Sign out everywhere` section before `// MARK: - Deletion`)

```kotlin
    // MARK: - Sign out everywhere (#976)

    @Test
    fun `signing out everywhere asks first, then revokes every session`() =
        runTest {
            val supabase = FakeSupabaseService(session = session())
            val viewModel = viewModel(supabase = supabase)
            advanceUntilIdle()

            viewModel.requestSignOutEverywhere()
            assertEquals(SignOutEverywhereState.CONFIRMING, viewModel.uiState.value.signOutEverywhere)
            assertEquals(0, supabase.signOutEverywhereCount)

            viewModel.confirmSignOutEverywhere()
            advanceUntilIdle()

            assertEquals(1, supabase.signOutEverywhereCount)
        }

    @Test
    fun `cancelling sign out everywhere signs nobody out`() =
        runTest {
            val supabase = FakeSupabaseService(session = session())
            val viewModel = viewModel(supabase = supabase)
            advanceUntilIdle()

            viewModel.requestSignOutEverywhere()
            viewModel.cancelSignOutEverywhere()
            advanceUntilIdle()

            assertEquals(SignOutEverywhereState.IDLE, viewModel.uiState.value.signOutEverywhere)
            assertEquals(0, supabase.signOutCount)
        }

    @Test
    fun `a failed global sign-out says so instead of pretending`() =
        runTest {
            val supabase = FakeSupabaseService(session = session())
            val viewModel = viewModel(supabase = supabase)
            advanceUntilIdle()
            supabase.failNext = IOException("offline")

            viewModel.requestSignOutEverywhere()
            viewModel.confirmSignOutEverywhere()
            advanceUntilIdle()

            assertEquals(SignOutEverywhereState.FAILED, viewModel.uiState.value.signOutEverywhere)
            viewModel.dismissSignOutEverywhereError()
            assertEquals(SignOutEverywhereState.IDLE, viewModel.uiState.value.signOutEverywhere)
        }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk; cd apps/android && ./gradlew :app:testDebugUnitTest --tests 'app.pbbls.android.features.profile.SettingsViewModelTest'`
Expected: compilation failure (`requestSignOutEverywhere`, `SignOutEverywhereState`, `signOutEverywhereCount` unresolved).

- [ ] **Step 3: Service — `signOut(everywhere)`**

In `SupabaseService.kt`, change the interface method to:

```kotlin
    /**
     * [everywhere] revokes every refresh token the user holds (all devices),
     * not just this one. A local sign-out never throws; a global one rethrows,
     * because "your other devices are signed out" must not be claimed when the
     * server was never reached.
     */
    suspend fun signOut(everywhere: Boolean = false)
```

Add the import `io.github.jan.supabase.auth.SignOutScope`, and replace the implementation with:

```kotlin
        /**
         * Sign out. A local sign-out's failures are logged but never surfaced —
         * the local token is wiped regardless and the collector emits
         * `NotAuthenticated`. A global one ([everywhere]) rethrows: the caller
         * must not report other devices signed out when the server never heard.
         */
        override suspend fun signOut(everywhere: Boolean) {
            try {
                client.auth.signOut(if (everywhere) SignOutScope.GLOBAL else SignOutScope.LOCAL)
            } catch (e: Exception) {
                Log.e(TAG, "signOut failed (everywhere=$everywhere)", e)
                if (everywhere) throw e
            }
        }
```

(If `SignOutScope` is not in `io.github.jan.supabase.auth` in 3.8.0, locate it with `find ~/.gradle/caches -name 'auth-kt-*sources*.jar' | head -1 | xargs unzip -l | grep SignOutScope`.)

- [ ] **Step 4: Fake**

In `FakeSupabaseService.kt`, replace the `signOut` override and add a counter:

```kotlin
    var signOutEverywhereCount = 0
        private set

    /**
     * A local sign-out never throws, matching the real service: it catches and
     * logs, because the local token is wiped regardless. A global one fires
     * [failNext], because the real one rethrows (the server must confirm).
     */
    override suspend fun signOut(everywhere: Boolean) {
        if (everywhere) {
            armed.fire()
            signOutEverywhereCount += 1
        }
        signOutCount += 1
        session = null
    }
```

- [ ] **Step 5: ViewModel**

In `SettingsViewModel.kt`, next to `DeletionState` add:

```kotlin
/** "Sign out of all devices": ask, run, or report that the server never heard. */
enum class SignOutEverywhereState { IDLE, CONFIRMING, WORKING, FAILED }
```

Add the field `val signOutEverywhere: SignOutEverywhereState = SignOutEverywhereState.IDLE,` to `SettingsUiState` right after `deletion`. Then, before `// MARK: - Deletion`, add:

```kotlin
        // MARK: - Sign out everywhere (#976)

        fun requestSignOutEverywhere() =
            _uiState.update { it.copy(signOutEverywhere = SignOutEverywhereState.CONFIRMING) }

        fun cancelSignOutEverywhere() = _uiState.update { it.copy(signOutEverywhere = SignOutEverywhereState.IDLE) }

        fun dismissSignOutEverywhereError() =
            _uiState.update { it.copy(signOutEverywhere = SignOutEverywhereState.IDLE) }

        /**
         * Revokes every session the user holds, this one included. On success
         * the session drops and the authed NavHost unmounts to Welcome, so
         * there is nothing to navigate. Not gated by a recent sign-in: it only
         * ever takes access away.
         */
        fun confirmSignOutEverywhere() {
            if (_uiState.value.signOutEverywhere == SignOutEverywhereState.WORKING) return
            _uiState.update { it.copy(signOutEverywhere = SignOutEverywhereState.WORKING) }
            viewModelScope.launch {
                runCatchingCancellable { supabase.signOut(everywhere = true) }
                    .onFailure {
                        Log.e(TAG, "global sign-out failed", it)
                        _uiState.update { state -> state.copy(signOutEverywhere = SignOutEverywhereState.FAILED) }
                    }
            }
        }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: the same command as Step 2. Expected: all `SettingsViewModelTest` tests PASS.

- [ ] **Step 7: Strings**

Add to `values/strings.xml`, right after `settings_delete_account_error`:

```xml
    <string name="settings_sign_out_everywhere">Sign out of all devices</string>
    <string name="settings_sign_out_everywhere_title">Sign out everywhere?</string>
    <string name="settings_sign_out_everywhere_message">This signs you out on every phone and browser, this one included. You\'ll sign back in with your password or Google.</string>
    <string name="settings_sign_out_everywhere_confirm">Sign out everywhere</string>
    <string name="settings_sign_out_everywhere_error">We couldn\'t reach the server, so your other devices may still be signed in. Please try again.</string>
```

Add to `values-fr/strings.xml` at the matching spot (informal "tu", no em dashes):

```xml
    <string name="settings_sign_out_everywhere">Se déconnecter de tous les appareils</string>
    <string name="settings_sign_out_everywhere_title">Te déconnecter partout ?</string>
    <string name="settings_sign_out_everywhere_message">Tu seras déconnecté·e sur tous tes téléphones et navigateurs, y compris celui-ci. Tu pourras revenir avec ton mot de passe ou Google.</string>
    <string name="settings_sign_out_everywhere_confirm">Tout déconnecter</string>
    <string name="settings_sign_out_everywhere_error">Impossible de joindre le serveur : tes autres appareils sont peut-être encore connectés. Réessaie.</string>
```

Check how existing FR strings handle gender before keeping `déconnecté·e`. If the file avoids inclusive dots, rephrase to "Ça te déconnecte de tous tes téléphones et navigateurs, y compris celui-ci. Tu pourras revenir avec ton mot de passe ou Google."

- [ ] **Step 8: Screen**

In `SettingsScreen.kt`, in the account `PebblesListSection`, add a row **before** the delete-account row:

```kotlin
                        {
                            val isWorking = uiState.signOutEverywhere == SignOutEverywhereState.WORKING
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.settings_sign_out_everywhere)) },
                                trailingContent =
                                    if (isWorking) {
                                        { LoadingIndicator(modifier = Modifier.size(24.dp)) }
                                    } else {
                                        null
                                    },
                                modifier = Modifier.clickable(enabled = !isWorking, onClick = viewModel::requestSignOutEverywhere),
                                colors = settingsRowColors(),
                            )
                        },
```

After the `DeletionState.FAILED` dialog block, add the two dialogs. `ConfirmDeleteDialog` is the destructive confirm; reuse it rather than adding a new component:

```kotlin
    if (uiState.signOutEverywhere == SignOutEverywhereState.CONFIRMING) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.settings_sign_out_everywhere_title),
            message = stringResource(R.string.settings_sign_out_everywhere_message),
            confirmText = stringResource(R.string.settings_sign_out_everywhere_confirm),
            onConfirm = viewModel::confirmSignOutEverywhere,
            onDismiss = viewModel::cancelSignOutEverywhere,
        )
    }
    if (uiState.signOutEverywhere == SignOutEverywhereState.FAILED) {
        DeleteErrorDialog(
            message = stringResource(R.string.settings_sign_out_everywhere_error),
            onDismiss = viewModel::dismissSignOutEverywhereError,
        )
    }
```

Read `core/designsystem/DeleteDialogs.kt` first. If `ConfirmDeleteDialog` hard-codes deletion wording (an icon, or a fixed title/cancel), mention it in your report and still reuse it only if the parameters above fully control the visible text.

- [ ] **Step 9: Lint + full unit tests**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk; npm run lint --workspace=@pbbls/android && cd apps/android && ./gradlew :app:testDebugUnitTest :app:lintDebug`
Expected: BUILD SUCCESSFUL. Screenshot-test failures that also fail on `main` are pre-existing (CI-only gate). Say which in the report.

- [ ] **Step 10: Commit**

```bash
git add apps/android/app/src
git commit -m "feat(android): sign out of all devices from settings

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## PART 3 — Android: re-auth before the three actions (branch `feat/976-android-reauth`)

The controller creates the branch stacked on Part 2 before Task 6.

### Task 6: `RecentAuth` — pure freshness check + error types

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/core/data/RecentAuth.kt`
- Modify: `apps/android/app/src/test/kotlin/app/pbbls/android/testing/TestSessions.kt`
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/core/data/RecentAuthTest.kt`

- [ ] **Step 1: Test helpers** (append to `TestSessions.kt`)

```kotlin
/** An unsigned JWT around [payloadJson] — enough for code that only reads the payload. */
fun testJwt(payloadJson: String): String {
    val enc = java.util.Base64.getUrlEncoder().withoutPadding()
    val header = enc.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
    val payload = enc.encodeToString(payloadJson.toByteArray())
    return "$header.$payload.signature"
}

/** An access token whose `amr` says the user signed in with a password at [at]. */
fun accessTokenSignedInAt(at: java.time.Instant): String =
    testJwt("""{"sub":"user-1","amr":[{"method":"password","timestamp":${at.epochSecond}}]}""")

/** A session for [userId] whose token proves a sign-in right now. */
fun freshSession(
    userId: String = "user-1",
    email: String? = "pebbler@example.com",
    identities: List<io.github.jan.supabase.auth.user.Identity>? = null,
) = UserSession(
    accessToken = accessTokenSignedInAt(java.time.Instant.now()),
    refreshToken = "refresh",
    expiresIn = 3600,
    tokenType = "bearer",
    user = UserInfo(id = userId, aud = "authenticated", email = email, identities = identities),
)
```

- [ ] **Step 2: Write the failing test**

`RealPayloads.PASSWORD` must be the payload of a real GoTrue access token, pasted verbatim (the cross-surface payload rule). The controller hands you the `amr` line printed by `verify-recent-auth.ts` in Task 4. Build the payload from it as shown below, keeping the `amr` array **exactly** as printed. If you were not given one, stop and ask. Do not invent the shape.

```kotlin
package app.pbbls.android.core.data

import app.pbbls.android.testing.postgrestException
import app.pbbls.android.testing.testJwt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The client half of the recent sign-in check (#976). Must agree with the SQL
 * `recent_auth_ok` (20260927120000): any numeric amr `timestamp` within
 * [RecentAuth.WINDOW] counts; anything unparseable does not.
 */
class RecentAuthTest {
    private val now = Instant.parse("2026-09-27T12:00:00Z")

    /**
     * Real GoTrue payloads. The `amr` arrays are pasted verbatim from a
     * sign-up token printed by verify-recent-auth.ts; only the timestamps are
     * substituted (via [at]) so the test controls the clock.
     */
    private object RealPayloads {
        fun password(at: Long) =
            """{"aud":"authenticated","role":"authenticated","aal":"aal1","amr":[{"method":"password","timestamp":$at}],"is_anonymous":false}"""

        fun oauth(at: Long) =
            """{"aud":"authenticated","role":"authenticated","aal":"aal1","amr":[{"method":"oauth","timestamp":$at}],"is_anonymous":false}"""
    }

    private fun tokenAt(secondsAgo: Long, payload: (Long) -> String = RealPayloads::password) =
        testJwt(payload(now.epochSecond - secondsAgo))

    @Test
    fun `a password sign-in a minute ago is fresh`() = assertTrue(RecentAuth.isFresh(tokenAt(60), now))

    @Test
    fun `an oauth sign-in a minute ago is fresh`() = assertTrue(RecentAuth.isFresh(tokenAt(60, RealPayloads::oauth), now))

    @Test
    fun `exactly at the window edge is fresh`() = assertTrue(RecentAuth.isFresh(tokenAt(600), now))

    @Test
    fun `eleven minutes ago is stale`() = assertFalse(RecentAuth.isFresh(tokenAt(660), now))

    @Test
    fun `any fresh stamp in a mixed amr counts`() {
        val old = now.epochSecond - 7200
        val fresh = now.epochSecond - 30
        val token = testJwt("""{"amr":[{"method":"password","timestamp":$old},{"method":"oauth","timestamp":$fresh}]}""")
        assertTrue(RecentAuth.isFresh(token, now))
    }

    @Test
    fun `missing, empty and malformed amr are never fresh`() {
        listOf(
            """{}""",
            """{"amr":[]}""",
            """{"amr":null}""",
            """{"amr":{"method":"password","timestamp":${now.epochSecond}}}""",
            """{"amr":[{"method":"password"}]}""",
            """{"amr":[{"method":"password","timestamp":"${now.epochSecond}"}]}""",
            """{"amr":[1,"x",null,{"timestamp":{"nested":1}}]}""",
        ).forEach { assertFalse(it, RecentAuth.isFresh(testJwt(it), now)) }
    }

    @Test
    fun `tokens that are not JWTs are never fresh`() {
        listOf(null, "", "token", "a.b", "a.%%%.c", "a.${"not json".encodeToByteArray().let { java.util.Base64.getUrlEncoder().encodeToString(it) }}.c")
            .forEach { assertFalse(it.toString(), RecentAuth.isFresh(it, now)) }
    }

    @Test
    fun `reauth_required from PostgREST and from the edge function are both recognized`() {
        assertTrue(postgrestException("reauth_required").isReauthRequired())
        assertTrue(ReauthRequiredException().isReauthRequired())
        assertFalse(postgrestException("handle_taken").isReauthRequired())
        assertFalse(java.io.IOException("offline").isReauthRequired())
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk; cd apps/android && ./gradlew :app:testDebugUnitTest --tests 'app.pbbls.android.core.data.RecentAuthTest'`
Expected: compilation failure (`RecentAuth` unresolved).

- [ ] **Step 4: Implement**

```kotlin
package app.pbbls.android.core.data

import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.Duration
import java.time.Instant
import java.util.Base64

/**
 * The client half of the recent sign-in check (#976).
 *
 * GoTrue stamps every real authentication into the access token's `amr` claim
 * (`[{"method":"password","timestamp":<epoch s>}]`), and a refresh keeps the
 * stamps — so a fresh stamp means the credential was proven recently. The
 * server's `recent_auth_ok` (20260927120000) applies the same rule; this copy
 * only decides whether to PROMPT, the server stays the authority. [WINDOW] is
 * duplicated in that migration — change both together.
 *
 * The payload is read without verifying the signature, which is fine for a
 * UX decision about our own token and never a security one.
 */
object RecentAuth {
    val WINDOW: Duration = Duration.ofMinutes(10)

    fun isFresh(
        accessToken: String?,
        now: Instant = Instant.now(),
    ): Boolean {
        val amr = accessToken?.let(::payloadOf)?.get("amr") as? JsonArray ?: return false
        val cutoff = now.minus(WINDOW).epochSecond.toDouble()
        return amr.any { entry ->
            val stamp = (entry as? JsonObject)?.get("timestamp") as? JsonPrimitive
            val seconds = stamp?.takeUnless { it.isString }?.doubleOrNull
            seconds != null && seconds >= cutoff
        }
    }

    private fun payloadOf(token: String): JsonObject? {
        val encoded = token.split('.').getOrNull(1) ?: return null
        return runCatching {
            Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)) as? JsonObject
        }.getOrNull()
    }
}

/** The raised condition's text, shared by the SQL and the edge function. */
const val REAUTH_REQUIRED = "reauth_required"

/** delete-account answered 428: the session is live but its sign-in is too old. */
class ReauthRequiredException(
    cause: Throwable? = null,
) : Exception(REAUTH_REQUIRED, cause)

/** A provider re-auth came back as a DIFFERENT user; that session was signed out. */
class ReauthAccountMismatchException : Exception("reauth_account_mismatch")

/**
 * True for either shape of "sign in again": the edge function's 428 (already
 * mapped to [ReauthRequiredException] by `ProfileService`), or the profiles
 * trigger's `raise exception 'reauth_required'` surfaced by PostgREST.
 */
fun Throwable.isReauthRequired(): Boolean =
    this is ReauthRequiredException || (this is PostgrestRestException && error == REAUTH_REQUIRED)
```

- [ ] **Step 5: Run to verify it passes**

Same command as Step 3. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/android/app/src/main/kotlin/app/pbbls/android/core/data/RecentAuth.kt apps/android/app/src/test/kotlin/app/pbbls/android/core/data/RecentAuthTest.kt apps/android/app/src/test/kotlin/app/pbbls/android/testing/TestSessions.kt
git commit -m "feat(android): read the recent sign-in stamp from the access token

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 7: Re-auth service methods + 428 mapping

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/core/data/SupabaseService.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/core/data/ProfileService.kt`
- Modify: `apps/android/app/src/test/kotlin/app/pbbls/android/testing/FakeSupabaseService.kt`

No new unit test here. The real service needs a live client, and Task 8's ViewModel tests exercise the fake's contract. Keep the real code thin.

- [ ] **Step 1: Interface** — add to `SupabaseServicing`, after `signInWithGoogle()`:

```kotlin
    /**
     * Re-proves the password of the CURRENT user (#976): signs in again with the
     * session's own email, which re-issues a session with a fresh `amr` stamp.
     * Throws on a wrong password (supabase-kt's auth exception) or when the
     * session has no email.
     */
    suspend fun reauthenticate(password: String)

    /**
     * Re-runs Google OAuth for the current user and suspends until a session
     * with a fresh `amr` stamp arrives. If it comes back as a different user,
     * that session is signed out and [ReauthAccountMismatchException] thrown.
     * Suspends until then — cancel the caller to abandon.
     */
    suspend fun reauthenticateWithGoogle()
```

- [ ] **Step 2: Real implementation** — add to `SupabaseService`, after `signInWithGoogle()`, with the imports `kotlinx.coroutines.flow.filterIsInstance`, `kotlinx.coroutines.flow.first`, `kotlinx.coroutines.flow.map`:

```kotlin
        override suspend fun reauthenticate(password: String) {
            val email = session?.user?.email ?: error("reauthenticate: the session has no email")
            signIn(email, password)
        }

        /**
         * Collecting `sessionStatus` here is a second, short-lived collector,
         * not [start]'s. The no-re-entry rule is about calling supabase-kt from
         * INSIDE a collector; the sign-out below runs after `first` returned.
         */
        override suspend fun reauthenticateWithGoogle() {
            val before = session?.user ?: error("reauthenticateWithGoogle: not authenticated")
            try {
                client.auth.signInWith(Google) {
                    // Steer Google to the same account; a different one is caught below.
                    before.email?.let { queryParams["login_hint"] = it }
                    queryParams["prompt"] = "select_account"
                }
            } catch (e: Exception) {
                Log.e(TAG, "reauthenticateWithGoogle failed", e)
                throw e
            }
            val returned =
                client.auth.sessionStatus
                    .filterIsInstance<SessionStatus.Authenticated>()
                    .map { it.session }
                    .first { RecentAuth.isFresh(it.accessToken) }
            if (returned.user?.id != before.id) {
                Log.e(TAG, "reauthenticateWithGoogle: came back as a different user")
                signOut()
                throw ReauthAccountMismatchException()
            }
        }
```

Verify `queryParams` exists on supabase-kt 3.8.0's external-auth config builder: `find ~/.gradle/caches -name 'auth-kt*sources*.jar' | head -1 | xargs -I{} unzip -p {} '*ExternalAuthConfig*' | grep -n queryParams`. If it doesn't exist, drop the two `queryParams` lines, keep a comment saying hints are unavailable, and report it.

- [ ] **Step 3: ProfileService 428 mapping** — replace `deleteAccount()` in `ProfileService.kt`:

```kotlin
        /**
         * Invokes the `delete-account` edge function, which purges the row graph and
         * the auth user. Throws on failure; the caller signs out and maps the error.
         * A 428 is the recent sign-in gate (#976) and becomes
         * [ReauthRequiredException] so the caller prompts instead of failing.
         *
         * Lives here rather than on the screen (#848) so `SupabaseServicing` never
         * has to expose the raw client — a client on that interface would make every
         * fake of it pointless.
         */
        override suspend fun deleteAccount() {
            try {
                supabase.client.functions.invoke("delete-account")
            } catch (e: RestException) {
                if (e.statusCode == HTTP_PRECONDITION_REQUIRED) throw ReauthRequiredException(e)
                throw e
            }
        }
```

Add `import io.github.jan.supabase.exceptions.RestException` (check the package with `grep -rn "import .*RestException" apps/android/app/src/main/kotlin | head -3`), and a `private const val HTTP_PRECONDITION_REQUIRED = 428` at the file's top level (or inside an existing companion if the file has one).

- [ ] **Step 4: Fake** — add to `FakeSupabaseService`:

```kotlin
    /** Every password passed to [reauthenticate], oldest first. */
    val reauthCalls = mutableListOf<String>()

    var googleReauthCount = 0
        private set

    /**
     * What a successful re-auth leaves behind. Defaults to the same user with
     * a freshly stamped token, which is what the real re-sign-in produces.
     */
    var sessionAfterReauth: () -> UserSession? = {
        session?.let { freshSession(userId = it.user?.id ?: "user-1", email = it.user?.email, identities = it.user?.identities) }
    }

    override suspend fun reauthenticate(password: String) {
        reauthCalls += password
        armed.fire()
        session = sessionAfterReauth()
    }

    override suspend fun reauthenticateWithGoogle() {
        googleReauthCount += 1
        armed.fire()
        session = sessionAfterReauth()
    }
```

- [ ] **Step 5: Compile + existing tests**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk; cd apps/android && ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (nothing calls the new methods yet; `ServiceFakesTest` may need nothing).

- [ ] **Step 6: Commit**

```bash
git add apps/android/app/src
git commit -m "feat(android): re-authenticate the current user by password or google

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 8: SettingsViewModel re-auth state machine

**Files:**
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/SettingsViewModel.kt`
- Modify: `apps/android/app/src/main/res/values/strings.xml`, `values-fr/strings.xml` (two error strings only; the dialog strings come in Task 9)
- Test: `apps/android/app/src/test/kotlin/app/pbbls/android/features/profile/SettingsViewModelTest.kt`

- [ ] **Step 1: Make existing tests reflect the new rule**

The test file's `session()` helper returns `accessToken = "token"`, which is now **stale** (unparseable). Change that helper so the default token is fresh, and add a `stale` switch:

```kotlin
    private fun session(
        email: String? = "pebbler@example.com",
        providers: List<String> = listOf("google"),
        fresh: Boolean = true,
    ) = UserSession(
        accessToken =
            accessTokenSignedInAt(
                if (fresh) java.time.Instant.now() else java.time.Instant.now().minus(java.time.Duration.ofHours(2)),
            ),
        // …rest unchanged
```

(import `app.pbbls.android.testing.accessTokenSignedInAt`). Existing deletion and save tests construct `FakeSupabaseService()` with **no** session. Give each of the three deletion tests, and every save test that sets a password or turns public on (`claiming a handle and going public writes the handle first`, `clearing the ViewModel mid-save still finishes the sequence`, and any other test that sets `newPassword` or turns public on and expects the write), a fresh session: `FakeSupabaseService(session = session())`. Don't change what they assert.

- [ ] **Step 2: Write the failing tests** (new section `// MARK: - Recent sign-in (#976)` before `// MARK: - Deletion`)

```kotlin
    // MARK: - Recent sign-in (#976)

    @Test
    fun `deleting with a stale sign-in asks to re-auth and deletes nothing yet`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService(session = session(providers = listOf("email"), fresh = false))
            val viewModel = viewModel(profile, supabase)
            advanceUntilIdle()

            viewModel.requestDelete()
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertEquals(DeletionState.REAUTHENTICATING, viewModel.uiState.value.deletion)
            assertEquals(ReauthMethod.PASSWORD, viewModel.uiState.value.reauth?.method)
            assertEquals(0, profile.deleteAccountCount)
        }

    @Test
    fun `re-entering the password then deletes and signs out`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService(session = session(providers = listOf("email"), fresh = false))
            val viewModel = viewModel(profile, supabase)
            advanceUntilIdle()

            viewModel.requestDelete()
            viewModel.confirmDelete()
            viewModel.onReauthPasswordChange("hunter2")
            viewModel.submitReauth()
            advanceUntilIdle()

            assertEquals(listOf("hunter2"), supabase.reauthCalls)
            assertNull(viewModel.uiState.value.reauth)
            assertEquals(1, profile.deleteAccountCount)
            assertEquals(1, supabase.signOutCount)
        }

    @Test
    fun `a fresh sign-in skips the re-auth dialog`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService(session = session())
            val viewModel = viewModel(profile, supabase)
            advanceUntilIdle()

            viewModel.requestDelete()
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.reauth)
            assertTrue(supabase.reauthCalls.isEmpty())
            assertEquals(1, profile.deleteAccountCount)
        }

    @Test
    fun `a wrong password keeps the dialog open with an error and deletes nothing`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService(session = session(providers = listOf("email"), fresh = false))
            val viewModel = viewModel(profile, supabase)
            advanceUntilIdle()
            viewModel.requestDelete()
            viewModel.confirmDelete()
            supabase.failNext = postgrestException("invalid_credentials", sqlState = null)

            viewModel.onReauthPasswordChange("wrong")
            viewModel.submitReauth()
            advanceUntilIdle()

            val reauth = viewModel.uiState.value.reauth
            assertEquals(R.string.reauth_wrong_password, reauth?.errorRes)
            assertFalse(reauth!!.isWorking)
            assertEquals("", reauth.password)
            assertEquals(DeletionState.REAUTHENTICATING, viewModel.uiState.value.deletion)
            assertEquals(0, profile.deleteAccountCount)
        }

    @Test
    fun `cancelling the re-auth returns to idle and deletes nothing`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService(session = session(fresh = false))
            val viewModel = viewModel(profile, supabase)
            advanceUntilIdle()

            viewModel.requestDelete()
            viewModel.confirmDelete()
            viewModel.cancelReauth()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.reauth)
            assertEquals(DeletionState.IDLE, viewModel.uiState.value.deletion)
            assertEquals(0, profile.deleteAccountCount)
        }

    @Test
    fun `the server asking for a re-auth reopens the dialog instead of failing`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService(session = session())
            val viewModel = viewModel(profile, supabase)
            advanceUntilIdle()
            profile.failNext = ReauthRequiredException()

            viewModel.requestDelete()
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertEquals(DeletionState.REAUTHENTICATING, viewModel.uiState.value.deletion)
            assertEquals(0, supabase.signOutCount)
        }

    @Test
    fun `a google-only account re-auths with google`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService(session = session(providers = listOf("google"), fresh = false))
            val viewModel = viewModel(profile, supabase)
            advanceUntilIdle()

            viewModel.requestDelete()
            viewModel.confirmDelete()
            assertEquals(ReauthMethod.GOOGLE, viewModel.uiState.value.reauth?.method)
            viewModel.submitReauth()
            advanceUntilIdle()

            assertEquals(1, supabase.googleReauthCount)
            assertEquals(1, profile.deleteAccountCount)
        }

    @Test
    fun `a google re-auth that comes back as someone else stops everything`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService(session = session(providers = listOf("google"), fresh = false))
            val viewModel = viewModel(profile, supabase)
            advanceUntilIdle()
            viewModel.requestDelete()
            viewModel.confirmDelete()
            supabase.failNext = ReauthAccountMismatchException()

            viewModel.submitReauth()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.reauth)
            assertEquals(DeletionState.IDLE, viewModel.uiState.value.deletion)
            assertEquals(0, profile.deleteAccountCount)
        }

    @Test
    fun `editing only the name never asks for a re-auth`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService(session = session(fresh = false))
            val viewModel = viewModel(profile, supabase)
            advanceUntilIdle()

            viewModel.onDisplayNameChange("Sam")
            viewModel.save()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.reauth)
            assertEquals(1, profile.saveSettingsCalls.size)
        }

    @Test
    fun `a new password with a stale sign-in re-auths first, then saves it`() =
        runTest {
            val profile = FakeProfileService(profile = profileRow())
            val supabase = FakeSupabaseService(session = session(providers = listOf("email"), fresh = false))
            val viewModel = viewModel(profile, supabase)
            advanceUntilIdle()

            viewModel.onPasswordChange("new-secret")
            viewModel.save()
            advanceUntilIdle()
            assertEquals(ReauthPurpose.SAVE, viewModel.uiState.value.reauth?.purpose)
            assertTrue(profile.saveSettingsCalls.isEmpty())

            viewModel.onReauthPasswordChange("old-secret")
            viewModel.submitReauth()
            advanceUntilIdle()

            assertEquals(listOf("old-secret"), supabase.reauthCalls)
            assertEquals("new-secret", profile.saveSettingsCalls.single().third)
        }

    @Test
    fun `going public needs a re-auth, going private does not`() =
        runTest {
            val stale = session(fresh = false)
            val goPublic = FakeProfileService(profile = profileRow(handle = "sam"))
            val vm1 = viewModel(goPublic, FakeSupabaseService(session = stale))
            advanceUntilIdle()
            vm1.onPublicProfileChange(true)
            vm1.save()
            advanceUntilIdle()
            assertEquals(ReauthPurpose.SAVE, vm1.uiState.value.reauth?.purpose)
            assertTrue(goPublic.setPublicProfileCalls.isEmpty())

            val goPrivate = FakeProfileService(profile = profileRow(handle = "sam", publicProfile = true))
            val vm2 = viewModel(goPrivate, FakeSupabaseService(session = stale))
            advanceUntilIdle()
            vm2.onPublicProfileChange(false)
            vm2.save()
            advanceUntilIdle()
            assertNull(vm2.uiState.value.reauth)
            assertEquals(listOf(false), goPrivate.setPublicProfileCalls)
        }
```

Imports to add: `app.pbbls.android.core.data.ReauthAccountMismatchException`, `app.pbbls.android.core.data.ReauthRequiredException`.

- [ ] **Step 3: Run to verify they fail**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk; cd apps/android && ./gradlew :app:testDebugUnitTest --tests 'app.pbbls.android.features.profile.SettingsViewModelTest'`
Expected: compilation failure (`ReauthMethod`, `submitReauth`, … unresolved).

- [ ] **Step 4: Strings for the two errors** (the dialog's own strings are in Task 9)

`values/strings.xml`:

```xml
    <string name="reauth_wrong_password">That password doesn\'t match. Try again.</string>
    <string name="reauth_error">We couldn\'t confirm it\'s you. Please try again.</string>
```

`values-fr/strings.xml`:

```xml
    <string name="reauth_wrong_password">Ce mot de passe ne correspond pas. Réessaie.</string>
    <string name="reauth_error">On n\'a pas pu confirmer que c\'est toi. Réessaie.</string>
```

- [ ] **Step 5: Implement in `SettingsViewModel.kt`**

5a. The types, next to `DeletionState` (replace the enum):

```kotlin
enum class DeletionState { IDLE, CONFIRMING, REAUTHENTICATING, DELETING, FAILED }

/** How the user proves it is them again (#976). */
enum class ReauthMethod { PASSWORD, GOOGLE }

/** What runs once the re-auth succeeds. */
enum class ReauthPurpose { DELETE, SAVE }

/**
 * The "Confirm it's you" dialog. [password] lives here and nowhere else — never
 * in SavedStateHandle (see the class KDoc), and cleared after every attempt.
 */
data class ReauthUi(
    val purpose: ReauthPurpose,
    val method: ReauthMethod,
    val password: String = "",
    val isWorking: Boolean = false,
    @StringRes val errorRes: Int? = null,
)
```

5b. `SettingsInitial` gains `val hasPasswordIdentity: Boolean = false,` (after `providers`). `SettingsUiState` gains `val reauth: ReauthUi? = null,` (after `signOutEverywhere`), plus this derived property inside `SettingsUiState`:

```kotlin
    /**
     * The save carries a gated change (#976): a new password, or turning the
     * public profile on. Turning it off, or any other field, never asks.
     */
    val saveNeedsRecentAuth: Boolean
        get() =
            form.newPassword.isNotEmpty() ||
                (!initial.publicProfile && form.isPublicProfile && normalizedHandle.isNotEmpty())
```

5c. In `onProfileLoaded`, set `hasPasswordIdentity` from the RAW identities (the display list `providers` drops `email`):

```kotlin
                    hasPasswordIdentity =
                        supabase.session
                            ?.user
                            ?.identities
                            .orEmpty()
                            .any { it.provider == "email" },
```

5d. Replace `save()` with:

```kotlin
        fun save() {
            val state = _uiState.value
            if (!state.isDirty || state.isSaving || state.reauth != null) return
            if (state.saveNeedsRecentAuth && !isSignInFresh()) {
                startReauth(ReauthPurpose.SAVE)
                return
            }
            runSave(state)
        }

        private fun runSave(state: SettingsUiState) {
            _uiState.update { it.copy(isSaving = true, didSaveFail = false, handleErrorRes = null) }
            viewModelScope.launch {
                // Everything from the first write to the last, uninterruptibly:
                // there is no point in this sequence where stopping leaves the
                // account consistent.
                withContext(NonCancellable) { performSave(state) }
            }
        }
```

In `performSave`'s final `rest.fold(onFailure = …)`, branch on the server gate:

```kotlin
                onFailure = {
                    Log.e(TAG, "settings save failed", it)
                    if (it.isReauthRequired()) {
                        // The server's clock or the #977 switch disagrees with ours.
                        // The handle (if any) is already stored and re-sending it
                        // is a no-op, so re-running the whole save after the
                        // re-auth is safe.
                        _uiState.update { state -> state.copy(isSaving = false) }
                        startReauth(ReauthPurpose.SAVE)
                    } else {
                        _uiState.update { state -> state.copy(isSaving = false, didSaveFail = true) }
                    }
                },
```

Check that re-sending the user's own current handle to `set_handle` really is a no-op and not `handle_taken`: read `create or replace function public.set_handle` in the latest migration that defines it (`grep -ln "function public.set_handle" packages/supabase/supabase/migrations/*.sql | tail -1`). If it would raise, report it rather than working around it.

5e. Replace `confirmDelete()` and add the re-auth API. The existing KDoc stays on `runDelete`:

```kotlin
        fun confirmDelete() {
            val deletion = _uiState.value.deletion
            if (deletion == DeletionState.DELETING || deletion == DeletionState.REAUTHENTICATING) return
            if (!isSignInFresh()) {
                startReauth(ReauthPurpose.DELETE)
                return
            }
            runDelete()
        }

        /** (existing KDoc of confirmDelete moves here) */
        private fun runDelete() {
            _uiState.update { it.copy(deletion = DeletionState.DELETING) }
            viewModelScope.launch {
                withContext(NonCancellable) {
                    runCatchingCancellable {
                        profileService.deleteAccount()
                        supabase.signOut()
                    }.onFailure {
                        Log.e(TAG, "account deletion failed", it)
                        if (it.isReauthRequired()) {
                            startReauth(ReauthPurpose.DELETE)
                        } else {
                            _uiState.update { state -> state.copy(deletion = DeletionState.FAILED) }
                        }
                    }
                }
            }
        }

        // MARK: - Recent sign-in (#976)

        private var reauthJob: Job? = null

        private fun isSignInFresh(): Boolean = RecentAuth.isFresh(supabase.session?.accessToken)

        private fun startReauth(purpose: ReauthPurpose) {
            val method = if (_uiState.value.initial.hasPasswordIdentity) ReauthMethod.PASSWORD else ReauthMethod.GOOGLE
            _uiState.update {
                it.copy(
                    reauth = ReauthUi(purpose = purpose, method = method),
                    deletion = if (purpose == ReauthPurpose.DELETE) DeletionState.REAUTHENTICATING else it.deletion,
                )
            }
        }

        fun onReauthPasswordChange(value: String) =
            _uiState.update { it.copy(reauth = it.reauth?.copy(password = value, errorRes = null)) }

        fun cancelReauth() {
            reauthJob?.cancel()
            reauthJob = null
            _uiState.update {
                it.copy(
                    reauth = null,
                    deletion = if (it.deletion == DeletionState.REAUTHENTICATING) DeletionState.IDLE else it.deletion,
                )
            }
        }

        /**
         * Password: signs in again as the same user. Google: runs OAuth and
         * suspends until the fresh session lands, so the dialog stays in its
         * working state while the Custom Tab is up; Cancel abandons it.
         */
        fun submitReauth() {
            val reauth = _uiState.value.reauth ?: return
            if (reauth.isWorking) return
            if (reauth.method == ReauthMethod.PASSWORD && reauth.password.isEmpty()) return
            _uiState.update { it.copy(reauth = reauth.copy(isWorking = true, errorRes = null)) }
            reauthJob =
                viewModelScope.launch {
                    runCatchingCancellable {
                        when (reauth.method) {
                            ReauthMethod.PASSWORD -> supabase.reauthenticate(reauth.password)
                            ReauthMethod.GOOGLE -> supabase.reauthenticateWithGoogle()
                        }
                    }.fold(
                        onSuccess = { onReauthenticated(reauth.purpose) },
                        onFailure = { onReauthFailed(reauth, it) },
                    )
                }
        }

        private fun onReauthenticated(purpose: ReauthPurpose) {
            _uiState.update { it.copy(reauth = null) }
            when (purpose) {
                ReauthPurpose.DELETE -> runDelete()
                ReauthPurpose.SAVE -> runSave(_uiState.value)
            }
        }

        private fun onReauthFailed(
            reauth: ReauthUi,
            error: Throwable,
        ) {
            Log.e(TAG, "re-auth failed", error)
            if (error is ReauthAccountMismatchException) {
                // That other account was signed out; the session is gone and the
                // authed NavHost unmounts. Nothing further to run.
                cancelReauth()
                return
            }
            val wrongPassword =
                reauth.method == ReauthMethod.PASSWORD &&
                    error.toDataError().let { it is DataError.Conflict || it is DataError.Unauthorized }
            _uiState.update {
                it.copy(
                    reauth =
                        it.reauth?.copy(
                            isWorking = false,
                            password = "",
                            errorRes = if (wrongPassword) R.string.reauth_wrong_password else R.string.reauth_error,
                        ),
                )
            }
        }
```

Imports: `app.pbbls.android.core.data.DataError`, `app.pbbls.android.core.data.RecentAuth`, `app.pbbls.android.core.data.ReauthAccountMismatchException`, `app.pbbls.android.core.data.isReauthRequired`, `kotlinx.coroutines.Job`.

Edge: `onReauthenticated(SAVE)` calls `runSave(_uiState.value)`, which re-reads the current form, so edits made while the dialog was up are included.

- [ ] **Step 6: Run to verify all tests pass**

Same command as Step 3. Expected: every `SettingsViewModelTest` test PASSES. If `a wrong password keeps the dialog open…` fails because `postgrestException(…, sqlState = null)` does not map to `Conflict`, read `dataErrorOf`: a 400 with a non-blank error is `Conflict`. Fix the test's exception, not the rule.

- [ ] **Step 7: Commit**

```bash
git add apps/android/app/src
git commit -m "feat(android): ask for a recent sign-in before deletion, password and going public

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 9: `ReauthDialog` + screen wiring

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/ReauthDialog.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/features/profile/SettingsScreen.kt`
- Modify: `apps/android/app/src/main/res/values/strings.xml`, `values-fr/strings.xml`

- [ ] **Step 1: Strings**

`values/strings.xml`:

```xml
    <string name="reauth_title">Confirm it\'s you</string>
    <string name="reauth_message_password">Enter your password to continue.</string>
    <string name="reauth_message_google">Continue with Google to confirm it\'s you.</string>
    <string name="reauth_password_label">Password</string>
    <string name="reauth_confirm">Confirm</string>
    <string name="reauth_continue_google">Continue with Google</string>
```

`values-fr/strings.xml`:

```xml
    <string name="reauth_title">Confirme que c\'est bien toi</string>
    <string name="reauth_message_password">Saisis ton mot de passe pour continuer.</string>
    <string name="reauth_message_google">Continue avec Google pour confirmer que c\'est bien toi.</string>
    <string name="reauth_password_label">Mot de passe</string>
    <string name="reauth_confirm">Confirmer</string>
    <string name="reauth_continue_google">Continuer avec Google</string>
```

- [ ] **Step 2: The dialog**

Read `core/designsystem/DeleteDialogs.kt` first and match its structure (M3 `AlertDialog`, `TextButton`s, the cancel string it uses, e.g. `R.string.action_cancel`).

```kotlin
package app.pbbls.android.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.pbbls.android.R

/**
 * "Confirm it's you" (#976): the recent sign-in step before a high-harm account
 * action. [usesPassword] picks the proof: a password field for accounts with an
 * email identity, a Google round-trip otherwise. Stateless — the caller owns the
 * password text, the working flag and the error.
 */
@Composable
internal fun ReauthDialog(
    usesPassword: Boolean,
    password: String,
    onPasswordChange: (String) -> Unit,
    isWorking: Boolean,
    errorText: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reauth_title)) },
        text = {
            Column {
                Text(
                    stringResource(
                        if (usesPassword) R.string.reauth_message_password else R.string.reauth_message_google,
                    ),
                )
                if (usesPassword) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = { Text(stringResource(R.string.reauth_password_label)) },
                        singleLine = true,
                        enabled = !isWorking,
                        isError = errorText != null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Done,
                            ),
                        keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (errorText != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isWorking && (!usesPassword || password.isNotEmpty())) {
                if (isWorking) {
                    LoadingIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(stringResource(if (usesPassword) R.string.reauth_confirm else R.string.reauth_continue_google))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
```

If `LoadingIndicator` needs an opt-in annotation in this codebase (check how `SettingsScreen.kt` uses it), mirror that. Keep Cancel enabled while working: that's how a user abandons the Google round-trip.

- [ ] **Step 3: Wire it in `SettingsScreen.kt`**, after the deletion dialogs:

```kotlin
    uiState.reauth?.let { reauth ->
        ReauthDialog(
            usesPassword = reauth.method == ReauthMethod.PASSWORD,
            password = reauth.password,
            onPasswordChange = viewModel::onReauthPasswordChange,
            isWorking = reauth.isWorking,
            errorText = reauth.errorRes?.let { stringResource(it) },
            onConfirm = viewModel::submitReauth,
            onDismiss = viewModel::cancelReauth,
        )
    }
```

Also: the delete row's loading indicator shows while `deletion == DELETING`. Leave that alone, because `REAUTHENTICATING` has the dialog as its feedback.

- [ ] **Step 4: Lint, tests, build**

Run: `export ANDROID_HOME=$HOME/Library/Android/sdk; npm run lint --workspace=@pbbls/android && cd apps/android && ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Screenshot-test failures also present on `main` are pre-existing; list them.

- [ ] **Step 5: Commit**

```bash
git add apps/android/app/src
git commit -m "feat(android): add the confirm-it's-you dialog to settings

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 10: Device verification (CONTROLLER)

- [ ] On the Pixel 7 emulator with a throwaway email account: sign in, wait 11 minutes (or use an older session), then change the password. Expected: the dialog appears; a wrong password shows the error; the right one saves; the app stays on Settings (same-user session swap is a no-op for `RootViewModel`, which keys on user id).
- [ ] Turn the public profile on with a stale sign-in. Expected: the dialog appears, then the save completes.
- [ ] Delete the throwaway account through the dialog. Expected: back to Welcome.
- [ ] "Sign out of all devices" on a second throwaway. Expected: confirm dialog, then Welcome.
- [ ] Google-only re-auth: exercise it on the emulator only if a Google test account is available there; otherwise say it's untested on device.
- [ ] Do not use the maintainer's physical Pixel 8 account without asking.

---

## After the stack

- PR bodies: `Resolves #976` on the top PR only (lower PRs say `Part of #976`). No finding ids anywhere. Labels: `feat` + `auth` + surface (`supabase` for Part 1, `android` for Parts 2–3); milestone M55 · Compliance Batch A. Lab Note: Parts 2 and 3 are user-facing (`platform: android`); Part 1 gets `no-lab-note`.
- Arkaik: move the affected acceptance/view nodes (Settings view, account deletion, public profile) to `development` at the start of Part 3; the App handles the rest.
- `docs/decisions/log.md`: one entry. Recent sign-in is read from the JWT `amr` claim with a 10-minute window, staged behind `recent_auth_enforced()`; a single pebble going public is deliberately ungated.
