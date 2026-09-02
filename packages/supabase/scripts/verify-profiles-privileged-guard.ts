#!/usr/bin/env -S deno run --allow-env --allow-net
/**
 * Acceptance test for the privileged-column guard — runs against the REMOTE project.
 *
 * `profiles_update` is scoped to the row's owner, which is correct for the
 * columns a user owns and wrong for the columns that grant capability. Owning a
 * row is not authority to raise your own capability in it, so
 * `profiles_privileged_guard` (20260902090000) pins four columns to their OLD
 * values for any PostgREST client role:
 *
 *   is_admin              gates every admin RPC, all analytics RPCs, lab-assets
 *                         storage writes, and unpublished-logs reads
 *   max_media_per_pebble  the server-side media quota
 *   terms_accepted_at     consent proof, kept for GDPR accountability
 *   privacy_accepted_at   ditto
 *
 * What this proves, and why a same-surface unit test cannot:
 *
 *   1. The four columns are not self-writable by an ordinary authenticated user.
 *   2. The refusal is the GUARD, not a coincidence — a 0-row RLS filter would
 *      also leave the value unchanged while proving nothing. Every negative
 *      assertion checks the error AND re-reads the stored value.
 *   3. The guard did not over-block: the columns a user does own still write.
 *   4. Mixing a benign column with a pinned one is rejected atomically.
 *
 * Run:
 *   SUPABASE_URL=... SUPABASE_ANON_KEY=... \
 *     deno run --allow-env --allow-net \
 *     packages/supabase/scripts/verify-profiles-privileged-guard.ts
 *
 * Deliberately needs NO service-role key: it signs up a throwaway user and
 * deletes it through the real `delete-account` edge function. Cleanup runs even
 * on failure. Exits non-zero if any assertion fails.
 *
 * STANDING RULE: a new profiles column that gates access is added to the
 * trigger's column list AND to `PINNED` below in the same change. A privileged
 * column absent from both is writable by every authenticated user.
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
const password = `Guard-${crypto.randomUUID()}`;

type TestUser = { client: SupabaseClient; id: string; token: string };

async function signUp(label: string): Promise<TestUser> {
  const client = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
  const email = `guard-verify-${label}-${runId}@example.test`;
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

async function readProfile(u: TestUser): Promise<Record<string, unknown>> {
  const { data, error } = await u.client
    .from("profiles")
    .select("display_name, is_admin, max_media_per_pebble, terms_accepted_at, privacy_accepted_at")
    .eq("user_id", u.id)
    .single();
  if (error || !data) throw new Error(`readProfile: ${error?.message ?? "no row"}`);
  return data as Record<string, unknown>;
}

/**
 * The escalation attempt, as a client actually makes it: a PATCH on the row the
 * caller owns, holding nothing but the publishable anon key.
 *
 * `blocked` requires BOTH halves. An error alone could be RLS or a network
 * blip; an unchanged value alone could be a 0-row filter. Only "the write
 * errored AND the stored value is untouched" distinguishes a guard that fires
 * from a request that never landed.
 */
async function attempt(
  u: TestUser,
  patch: Record<string, unknown>,
): Promise<{ message: string; code: string }> {
  const { error } = await u.client.from("profiles").update(patch).eq("user_id", u.id).select();
  return { message: error?.message ?? "", code: error?.code ?? "" };
}

/** Column → the escalated value an attacker would want. */
const PINNED: Array<[string, unknown, string]> = [
  ["is_admin", true, "full operator: every admin RPC, all analytics, lab-assets writes"],
  ["max_media_per_pebble", 99, "bypasses the server-side media quota"],
  ["terms_accepted_at", "2020-01-01T00:00:00.000Z", "forges consent proof (GDPR accountability)"],
  ["privacy_accepted_at", "2020-01-01T00:00:00.000Z", "forges consent proof (GDPR accountability)"],
];

let alice: TestUser | null = null;

try {
  alice = await signUp("alice");
  const before = await readProfile(alice);
  check("throwaway user starts non-admin", before.is_admin === false, JSON.stringify(before));

  // ---------------------------------------------------------------------------
  // 1. Each pinned column refuses a self-write, with the guard's own error.
  // ---------------------------------------------------------------------------
  for (const [column, escalated, why] of PINNED) {
    const { message, code } = await attempt(alice, { [column]: escalated });
    check(
      `${column}: self-write rejected (${why})`,
      message.includes("profiles_privileged_column"),
      `code=${code} message=${message || "(no error — THE WRITE SUCCEEDED)"}`,
    );
    const after = await readProfile(alice);
    check(
      `${column}: stored value unchanged`,
      after[column] === before[column],
      `${JSON.stringify(before[column])} → ${JSON.stringify(after[column])}`,
    );
  }

  // ---------------------------------------------------------------------------
  // 2. The guard did not over-block. A guard that also breaks the legitimate
  //    update path would be reverted in a week, and the hole would come back.
  // ---------------------------------------------------------------------------
  const renamed = `guard-verify-${runId}`;
  const { message: benignErr } = await attempt(alice, { display_name: renamed });
  check("display_name still writable by its owner", benignErr === "", benignErr);
  check("display_name actually changed", (await readProfile(alice)).display_name === renamed);

  // ---------------------------------------------------------------------------
  // 3. Re-sending an unchanged pinned value succeeds. The trigger fires on the
  //    SET list but raises only on a real change (`is distinct from`), so a
  //    client that echoes back the whole profile row is not broken by this.
  // ---------------------------------------------------------------------------
  const { message: echoErr } = await attempt(alice, { is_admin: false });
  check("echoing an unchanged is_admin is not an error", echoErr === "", echoErr);

  // ---------------------------------------------------------------------------
  // 4. A mixed patch is rejected whole. Postgres aborts the statement, so the
  //    benign half must not land either — otherwise an attacker learns to
  //    smuggle the privileged column alongside a legitimate one.
  // ---------------------------------------------------------------------------
  const smuggled = `smuggled-${runId}`;
  const { message: mixedErr } = await attempt(alice, { display_name: smuggled, is_admin: true });
  check("mixed benign+privileged patch rejected", mixedErr.includes("profiles_privileged_column"), mixedErr);
  const afterMixed = await readProfile(alice);
  check("the benign half did not land either", afterMixed.display_name === renamed, String(afterMixed.display_name));
  check("still not an admin", afterMixed.is_admin === false);
} catch (err) {
  failed += 1;
  console.error(`✗ aborted: ${err instanceof Error ? err.message : String(err)}`);
} finally {
  if (alice) {
    const res = await deleteAccount(alice.token).catch(() => null);
    console.log(`… cleanup alice: ${res ? res.status : "FAILED — remove guard-verify-* manually"}`);
  }
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed > 0 ? 1 : 0);
