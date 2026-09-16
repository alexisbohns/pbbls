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
