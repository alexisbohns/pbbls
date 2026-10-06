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
 *   4. Nightly only (RECENT_AUTH_STALE_CASE=1): enforcement is on (#977).
 *      It waits past the 10-minute window with the same token, then expects
 *      `reauth_required` from both gates: the public flip is refused and left
 *      off, and delete-account answers 428. Gated because the wait would add
 *      over ten minutes to every PR run.
 *
 * Run:
 *   SUPABASE_URL=... SUPABASE_ANON_KEY=... [RECENT_AUTH_STALE_CASE=1] \
 *     deno run --allow-env --allow-net packages/supabase/scripts/verify-recent-auth.ts
 *
 * Needs NO service-role key: it signs up a throwaway user and deletes it
 * through the real delete-account edge function. Cleanup runs even on failure.
 * Exits non-zero if any assertion fails.
 */

const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY");
const STALE_CASE = Deno.env.get("RECENT_AUTH_STALE_CASE") === "1";
/** Past the 10-minute window, with margin for clock skew between runner and server. */
const STALE_WAIT_MS = 10.5 * 60 * 1000;

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

const email = `recent-auth-verify-${runId}@example.test`;

async function signUp(): Promise<TestUser> {
  const client = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
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

/** Re-sign in as the throwaway user: a fresh `amr` stamp, as the clients' re-auth does. */
async function freshToken(): Promise<string> {
  const client = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
  const { data, error } = await client.auth.signInWithPassword({ email, password });
  if (error || !data.session) throw new Error(`re-sign-in: ${error?.message ?? "no session"}`);
  return data.session.access_token;
}

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
    // Far past the window: the runner's clock is compared with the server's.
    ["30-minute-old stamp", [{ method: "password", timestamp: nowS() - 1800 }], false],
    // Out of timestamp range: compared in epoch space, so it answers instead of
    // raising. (A future stamp cannot be forged — the claim is signed.)
    ["out-of-range stamp does not raise", [{ method: "password", timestamp: 1e20 }], true],
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
  // 4. Nightly only: the same token, past the window, is refused by both gates.
  // -------------------------------------------------------------------------
  if (STALE_CASE) {
    const { data: enforced, error: enforcedErr } = await c.rpc("recent_auth_enforced");
    check("enforcement is on", !enforcedErr && enforced === true, enforcedErr?.message ?? String(enforced));

    console.log(`… waiting ${STALE_WAIT_MS / 60000} minutes for the sign-in to go stale`);
    await new Promise((resolve) => setTimeout(resolve, STALE_WAIT_MS));

    // A token client, not `c`: it must present exactly the token signed in
    // eleven minutes ago (a refresh would keep the same stamp anyway).
    const stale = createClient(SUPABASE_URL!, ANON_KEY!, {
      auth: { persistSession: false, autoRefreshToken: false },
      global: { headers: { Authorization: `Bearer ${user.token}` } },
    });

    const { data: staleOk } = await stale.rpc("recent_auth_ok", { p_amr: payload.amr, p_max_age: "10 minutes" });
    check("the same amr is stale past the window", staleOk === false, String(staleOk));

    const { error: staleAssert } = await stale.rpc("assert_recent_auth");
    check("assert_recent_auth refuses a stale session", staleAssert?.message === "reauth_required", staleAssert?.message);

    const { error: staleFlip } = await stale.from("profiles").update({ public_profile: true }).eq("user_id", user.id);
    const { data: staleRow } = await stale.from("profiles").select("public_profile").eq("user_id", user.id).single();
    check(
      "a stale session cannot turn the public profile on",
      staleFlip?.message === "reauth_required" && staleRow?.public_profile === false,
      `${staleFlip?.message} / stored ${staleRow?.public_profile}`,
    );

    const { error: staleOff } = await stale.from("profiles").update({ public_profile: false }).eq("user_id", user.id);
    check("a stale session can still turn it off", !staleOff, staleOff?.message);

    const staleDelete = await deleteAccount(user.token);
    const staleBody = await staleDelete.text();
    check(
      "delete-account answers 428 reauth_required to a stale session",
      staleDelete.status === 428 && staleBody.includes("reauth_required"),
      `status ${staleDelete.status}: ${staleBody}`,
    );

    // Confirm it's you, as every client does: sign in again for a fresh stamp.
    user.token = await freshToken();
  }

  // -------------------------------------------------------------------------
  // 5. delete-account passes the gate for a fresh session (this is cleanup).
  // -------------------------------------------------------------------------
  const res = await deleteAccount(user.token);
  deleted = res.status === 200;
  check("delete-account accepts a fresh session", deleted, `status ${res.status}: ${await res.text()}`);
} catch (err) {
  failed += 1;
  console.error(`✗ aborted: ${err instanceof Error ? err.message : String(err)}`);
} finally {
  if (user && !deleted) {
    // With enforcement on, the sign-up token may be stale by now: sign in again.
    const token = await freshToken().catch(() => user!.token);
    const res = await deleteAccount(token).catch(() => null);
    console.log(`… cleanup: ${res ? res.status : "FAILED — remove recent-auth-verify-* manually"}`);
  }
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed > 0 ? 1 : 0);
