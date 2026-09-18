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
  // Match the TRIGGER's own message, not merely "some error". Before the
  // migration this fails with "column glyphs.is_system does not exist" and
  // after it fails with the trigger — asserting `err !== null` alone would be
  // green in both states and would stay green if the trigger silently stopped
  // firing. The assertion has to name what refused.
  check("a user cannot promote their own glyph to is_system",
    promoteErr !== null && promoteErr.message.includes("is_system is not user-settable"),
    promoteErr?.message ?? "the update was accepted");
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
  check("a user cannot INSERT a glyph already marked is_system",
    insertErr !== null && insertErr.message.includes("is_system is not user-settable"),
    insertErr?.message ?? "the insert was accepted");

  const { data: bornSystem } = await bob.client
    .from("glyphs").select("id, is_system").eq("name", `glyph-verify born-system ${runId}`);
  check("…and no row was created", (bornSystem ?? []).length === 0,
    JSON.stringify(bornSystem));

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
