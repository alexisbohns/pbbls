#!/usr/bin/env -S deno run --allow-env --allow-net
/**
 * Acceptance test for the moments / pebbles split — runs against the REMOTE project.
 *
 *   1. A moment needs a date plus a name or a photo; saving one costs no karma.
 *   2. A pebble needs emotion, valence and a domain; the first completion is the
 *      reward (one pebble_created event), later edits are enrichment deltas.
 *   3. The glyph is derived: domain default first, a collection glyph wins once set.
 *   4. Readers: path_moments shows a null pebble for an uncompleted moment;
 *      v_moments_full nests only the caller's pebble; a second user reads
 *      nothing until the pebble is public, and never the sharer's pebble body.
 *   5. Compat: create_pebble + v_pebbles_full + path_pebbles + the compose-pebble edge function still answer.
 *   6. happened_at is truncated to whole seconds by save_moment (direct and via create_pebble).
 *   7. delete_pebble keeps the moment; delete_moment removes all and reverses karma.
 *
 * Run: set -a; . ./.env; set +a; npm run db:verify:moments --workspace=packages/supabase
 * Expected: `Summary: passed=36 failed=0` (36 executed checks, each if/else pair counting once — recount if you add one).
 * A founding write that fails (first save_moment, first complete_pebble, the
 * collection insert) aborts the run with one `✗ aborted:` line instead of a
 * cascade of dependent reds.
 * Needs NO service-role key: it signs up throwaway users (moments-verify-*@example.test)
 * and deletes them through the real `delete-account` edge function, even on failure.
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
const password = `Moments-${crypto.randomUUID()}`;
type TestUser = { client: SupabaseClient; id: string; token: string };

async function signUp(label: string): Promise<TestUser> {
  const client = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
  const email = `moments-verify-${label}-${runId}@example.test`;
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
    headers: { Authorization: `Bearer ${token}`, apikey: ANON_KEY!, "Content-Type": "application/json" },
  });
}

/** A write the rest of the run stands on: fail loudly once rather than ~30 times. */
function must<T>(label: string, r: { data: T | null; error: { message: string } | null }): T {
  if (r.error || r.data === null || r.data === undefined) {
    throw new Error(`${label}: ${r.error?.message ?? "no data"}`);
  }
  return r.data;
}

// RLS scopes karma_events to the caller (karma_events_select), and a fresh user starts at 0.
async function karmaSum(client: SupabaseClient): Promise<number> {
  const { data } = await client.from("karma_events").select("delta");
  return (data ?? []).reduce((acc: number, r: { delta: number }) => acc + r.delta, 0);
}

let alice: TestUser | null = null;
let bob: TestUser | null = null;

try {
  alice = await signUp("a");
  bob = await signUp("b");
  const a = alice.client;
  const b = bob.client;
  console.log(`alice=${alice.id} bob=${bob.id}\n`);

  const { data: emotion } = await a.from("emotions").select("id").limit(1).single();
  const { data: domain } = await a
    .from("domains").select("id, default_glyph_id").not("default_glyph_id", "is", null).limit(1).single();
  if (!emotion || !domain) throw new Error("reference data missing (emotion / domain with a default glyph)");

  // 1. moments ----------------------------------------------------------------
  const empty = await a.rpc("save_moment", { payload: {} });
  check("a moment with neither name nor photo is refused",
    !!empty.error && empty.error.message.includes("moment_empty"), empty.error?.message);

  const saved = await a.rpc("save_moment", { payload: { name: `coffee ${runId}`, happened_at: "2026-10-11T10:00:00.123Z" } });
  check("a name alone saves a moment", !saved.error && typeof saved.data === "string", saved.error?.message);
  const momentId = must<string>("save_moment", saved);
  check("saving a moment grants no karma", (await karmaSum(a)) === 0);

  const { data: m1 } = await a.from("moments").select("happened_at").eq("id", momentId).single();
  check("happened_at is stored at whole seconds",
    !!m1 && new Date(m1.happened_at).getMilliseconds() === 0, m1?.happened_at);

  const { data: path1 } = await a.rpc("path_moments");
  const row1 = (path1 ?? []).find((r: { id: string }) => r.id === momentId);
  check("path_moments lists the moment with a null pebble", !!row1 && row1.pebble === null);

  // 2. completion ---------------------------------------------------------------
  const noDomain = await a.rpc("complete_pebble", {
    payload: { moment_id: momentId, emotion_id: emotion.id, intensity: 2, positiveness: 1 },
  });
  check("a pebble without a domain is refused",
    !!noDomain.error && noDomain.error.message.includes("pebble_needs_domain"), noDomain.error?.message);

  const done = await a.rpc("complete_pebble", {
    payload: { moment_id: momentId, emotion_id: emotion.id, intensity: 2, positiveness: 1, domain_ids: [domain.id] },
  });
  check("emotion + valence + domain completes the pebble", !done.error && typeof done.data === "string", done.error?.message);
  const pebbleId = must<string>("complete_pebble", done);

  const { data: events1 } = await a.from("karma_events").select("reason, delta").eq("ref_id", pebbleId);
  check("the first completion writes exactly one pebble_created event",
    (events1 ?? []).length === 1 && events1![0].reason === "pebble_created", JSON.stringify(events1));

  const again = await a.rpc("complete_pebble", {
    payload: { moment_id: momentId, emotion_id: emotion.id, intensity: 3, positiveness: 1, domain_ids: [domain.id] },
  });
  check("completing again updates the same pebble", !again.error && again.data === pebbleId, again.error?.message);
  const { count: pebbleCount } = await a.from("pebbles").select("*", { count: "exact", head: true }).eq("moment_id", momentId);
  check("one pebble per (moment, user)", pebbleCount === 1, `count=${pebbleCount}`);

  // 3. glyph --------------------------------------------------------------------
  const { data: full1 } = await a.from("v_moments_full").select("pebble").eq("id", momentId).single();
  check("the glyph resolves from the domain default",
    full1?.pebble?.glyph?.source === "domain" && full1?.pebble?.glyph?.id === domain.default_glyph_id,
    JSON.stringify(full1?.pebble?.glyph));

  const coll = must<{ id: string }>(
    "collections insert",
    await a.from("collections").insert({ user_id: alice.id, name: `trip ${runId}` }).select("id").single(),
  );
  // The domain default is a system glyph, so can_use_glyph accepts it for any user.
  const setGlyph = await a.rpc("set_collection_glyph", { p_collection_id: coll.id, p_glyph_id: domain.default_glyph_id });
  check("a collection can carry a usable glyph", !setGlyph.error, setGlyph.error?.message);
  // An update: only moment_id + collection_ids, so no domain / emotion / valence rule fires.
  const addColl = await a.rpc("complete_pebble", { payload: { moment_id: momentId, collection_ids: [coll.id] } });
  const { data: full2 } = await a.from("v_moments_full").select("pebble").eq("id", momentId).single();
  check("a collection glyph wins over the domain",
    !addColl.error && full2?.pebble?.glyph?.source === "collection",
    addColl.error?.message ?? JSON.stringify(full2?.pebble?.glyph));

  // 4. readers and RLS ------------------------------------------------------------
  const probeSecret = await b.rpc("resolve_pebble_glyph", { p_pebble_id: pebbleId });
  check("a secret pebble's glyph is null to another user",
    !probeSecret.error && probeSecret.data === null, probeSecret.error?.message ?? JSON.stringify(probeSecret.data));
  const probeUnknown = await b.rpc("resolve_pebble_glyph", { p_pebble_id: crypto.randomUUID() });
  check("an unknown pebble id answers null",
    !probeUnknown.error && probeUnknown.data === null, probeUnknown.error?.message ?? JSON.stringify(probeUnknown.data));
  const { data: bobSees1 } = await b.from("v_moments_full").select("id").eq("id", momentId);
  check("a secret pebble's moment is invisible to another user", (bobSees1 ?? []).length === 0);

  const toPublic = await a.rpc("complete_pebble", { payload: { moment_id: momentId, visibility: "public" } });
  check("making the pebble public succeeds", !toPublic.error, toPublic.error?.message);
  const { data: bobSees2 } = await b.from("v_moments_full").select("id, name, pebble").eq("id", momentId);
  check("a public pebble opens its moment to another user",
    (bobSees2 ?? []).length === 1 && bobSees2![0].name === `coffee ${runId}`, JSON.stringify(bobSees2));
  check("the other user never gets the sharer's pebble body",
    (bobSees2 ?? []).length === 1 && bobSees2![0].pebble === null);
  const { data: bobPebbles } = await b.from("pebbles").select("id").eq("moment_id", momentId);
  check("pebbles_select still shows the public pebble row", (bobPebbles ?? []).length === 1);

  // The computed field (#1050) answers the same as the resolver, in the same select.
  const { data: viaField, error: viaFieldErr } = await b.from("pebbles").select("id, glyph").eq("id", pebbleId).single();
  check("the pebbles.glyph computed field resolves for another user",
    !viaFieldErr && viaField?.glyph?.id === domain.default_glyph_id,
    viaFieldErr?.message ?? JSON.stringify(viaField));
  // The collection carries the same glyph as the domain, so the id is the same either way.
  const probePublic = await b.rpc("resolve_pebble_glyph", { p_pebble_id: pebbleId });
  check("a public pebble's glyph resolves for another user",
    !probePublic.error && probePublic.data?.id === domain.default_glyph_id,
    probePublic.error?.message ?? JSON.stringify(probePublic.data));
  // A client with no session: the share page's reader. Nothing to clean up.
  const anonClient = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
  const sharedAnon = await anonClient.rpc("get_shared_pebble", { p_pebble_id: pebbleId });
  check("get_shared_pebble answers anon with a glyph",
    !sharedAnon.error && !!sharedAnon.data?.glyph?.id, sharedAnon.error?.message ?? JSON.stringify(sharedAnon.data));
  const shared = await b.rpc("get_shared_pebble", { p_pebble_id: pebbleId });
  check("get_shared_pebble carries the moment name and a glyph",
    !shared.error && shared.data?.name === `coffee ${runId}` && !!shared.data?.glyph?.id,
    shared.error?.message ?? JSON.stringify(shared.data));

  // 5. compat (web, until #1050) ----------------------------------------------------
  const compat = await a.rpc("create_pebble", {
    payload: {
      name: `compat ${runId}`, happened_at: "2026-10-11T11:00:00.456Z",
      emotion_id: emotion.id, intensity: 1, positiveness: 0, domain_ids: [domain.id], glyph_id: null,
    },
  });
  check("create_pebble wrapper still returns a pebble id", !compat.error && typeof compat.data === "string", compat.error?.message);
  const compatId = compat.data as string;
  const { data: vpf } = await a.from("v_pebbles_full").select("id, name, glyph_id, happened_at").eq("id", compatId).single();
  check("v_pebbles_full compat row carries name and resolved glyph_id",
    vpf?.name === `compat ${runId}` && vpf?.glyph_id === domain.default_glyph_id, JSON.stringify(vpf));
  check("the compat row's happened_at is at whole seconds",
    !!vpf?.happened_at && new Date(vpf.happened_at).getMilliseconds() === 0 &&
      new Date(vpf.happened_at).toISOString().startsWith("2026-10-11T11:00:00"), vpf?.happened_at);
  const { data: pp } = await a.rpc("path_pebbles");
  check("path_pebbles compat answers with render_svg null",
    Array.isArray(pp) && pp.length >= 2 && pp.every((r: { render_svg: unknown }) => r.render_svg === null),
    JSON.stringify(pp));
  // The edge function web still writes through (#1050): same wrapper, null render keys.
  const composeRes = await fetch(`${SUPABASE_URL}/functions/v1/compose-pebble`, {
    method: "POST",
    headers: { Authorization: `Bearer ${alice.token}`, apikey: ANON_KEY!, "Content-Type": "application/json" },
    body: JSON.stringify({
      payload: {
        name: `composed ${runId}`, happened_at: "2026-10-11T12:00:00Z",
        emotion_id: emotion.id, intensity: 1, positiveness: 0, domain_ids: [domain.id],
      },
    }),
  });
  const composed = await composeRes.json().catch(() => null) as
    | { pebble_id?: string; render_svg?: unknown; render_version?: unknown; karma_delta?: unknown; error?: string }
    | null;
  check("compose-pebble answers 200 with a pebble id and null render keys",
    composeRes.status === 200 && typeof composed?.pebble_id === "string" &&
      composed?.render_svg === null && composed?.render_version === null &&
      typeof composed?.karma_delta === "number",
    `status=${composeRes.status} ${JSON.stringify(composed)}`);
  if (composed?.pebble_id) {
    const { data: composedPebble } = await a.from("pebbles").select("moment_id").eq("id", composed.pebble_id).single();
    const delComposed = composedPebble?.moment_id
      ? await a.rpc("delete_moment", { p_moment_id: composedPebble.moment_id })
      : { error: { message: "no composed pebble row" } };
    check("delete_moment removes the composed moment", !delComposed.error, delComposed.error?.message);
  } else {
    check("delete_moment removes the composed moment", false, "compose-pebble returned no pebble id");
  }
  const { data: compatPebble } = await a.from("pebbles").select("moment_id").eq("id", compatId).single();
  // A broken compat layer must not abort the delete section below.
  if (compatPebble?.moment_id) {
    const delCompat = await a.rpc("delete_moment", { p_moment_id: compatPebble.moment_id });
    check("delete_moment removes the compat moment", !delCompat.error, delCompat.error?.message);
  } else {
    check("delete_moment removes the compat moment", false, "no compat pebble row to delete");
  }

  // 6/7. deletes ---------------------------------------------------------------------
  const delP = await a.rpc("delete_pebble", { p_pebble_id: pebbleId });
  check("delete_pebble removes the pebble", !delP.error, delP.error?.message);
  const { data: stillThere } = await a.from("moments").select("id").eq("id", momentId);
  check("the moment survives its pebble", (stillThere ?? []).length === 1);
  check("deleting the pebble reverses its karma", (await karmaSum(a)) === 0, `sum=${await karmaSum(a)}`);

  const delM = await a.rpc("delete_moment", { p_moment_id: momentId });
  check("delete_moment returns the snap paths to remove",
    !delM.error && Array.isArray(delM.data?.storage_paths), delM.error?.message ?? JSON.stringify(delM.data));
  const { data: gone } = await a.from("moments").select("id").eq("id", momentId);
  check("the moment is gone", (gone ?? []).length === 0);
} catch (err) {
  failed += 1;
  console.error(`✗ aborted: ${err instanceof Error ? err.message : String(err)}`);
} finally {
  for (const [user, label] of [[alice, "alice"], [bob, "bob"]] as const) {
    if (!user) continue;
    const res = await deleteAccount(user.token).catch(() => null);
    console.log(`… cleanup ${label}: ${res ? res.status : "FAILED — remove moments-verify-* manually"}`);
  }
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed > 0 ? 1 : 0);
