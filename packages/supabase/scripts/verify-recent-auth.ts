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
