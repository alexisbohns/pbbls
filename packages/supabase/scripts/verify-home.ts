#!/usr/bin/env -S deno run --allow-env --allow-net --allow-read --allow-write
/**
 * Acceptance test for the Home tab's back-end (#1254) — runs against the REMOTE project.
 *
 *   1. The first five: first-moment unlocks on the first save_moment and
 *      first-pebble on the first complete_pebble; both SURVIVE the deletion of
 *      the only moment (the point of backing the checklist with achievements).
 *      check_achievements never grants blue-pebble.
 *   2. claim_blue_pebble raises not_ready before the five, unlocks once
 *      (returns the row), returns nothing on a second call, and the unlock
 *      count stays one. anon is refused.
 *   3. people_prompts: totals; the no-soul filter; the outward-domain rule; the
 *      valence guard (a low pebble is never asked, whatever its domain); latest
 *      first; the skipped names; the limit; anon refused.
 *   Not provable without a service role, so not claimed here: that the claim
 *   PAYS once (karma_reward is 0 at seed and only an admin can set it; the
 *   exactly-once is argued structurally, the unlocks PK feeding the karma CTE)
 *   and that a moderation-hidden moment is excluded (nothing can hide one).
 *   4. (part 3) wrap_reveals / reveal_wrap / home_wraps — see the section below.
 *
 * HOME_DUMP=1 writes the verbatim people_prompts payload to
 * scripts/oracles/home/payloads/people-prompts.json for the clients' decoding tests.
 *
 * Run: set -a; . ./.env; set +a; npm run db:verify:home --workspace=packages/supabase
 * Expected: `Summary: passed=21 failed=0` (part 2); part 3 raises it.
 * Needs NO service-role key: signs up throwaway users (home-verify-*@example.test)
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
  if (ok) { passed += 1; console.log(`✓ ${name}`); }
  else { failed += 1; console.log(`✗ ${name}${detail ? ` — ${detail}` : ""}`); }
}

const DUMP = Deno.env.get("HOME_DUMP") === "1";
const ORACLE_DIR = new URL("./oracles/home/", import.meta.url);
async function dump(name: string, payload: unknown) {
  if (!DUMP) return;
  await Deno.mkdir(new URL("payloads/", ORACLE_DIR), { recursive: true });
  await Deno.writeTextFile(new URL(`payloads/${name}.json`, ORACLE_DIR), JSON.stringify(payload, null, 2) + "\n");
}

const runId = crypto.randomUUID().slice(0, 8);
const password = `Home-${crypto.randomUUID()}`;
type TestUser = { client: SupabaseClient; id: string; token: string };

async function signUp(label: string): Promise<TestUser> {
  const client = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
  const email = `home-verify-${label}-${runId}@example.test`;
  const { data, error } = await client.auth.signUp({ email, password });
  if (error || !data.session || !data.user) {
    throw new Error(`signUp ${label}: ${error?.message ?? "no session (email confirmations on?)"}`);
  }
  return { client, id: data.user.id, token: data.session.access_token };
}
async function deleteAccount(token: string): Promise<Response> {
  return await fetch(`${SUPABASE_URL}/functions/v1/delete-account`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, apikey: ANON_KEY!, "Content-Type": "application/json" },
  });
}

type Unlock = { slug: string; karma_granted: number };
type Prompts = {
  pebbles: number; souls: number;
  prompts: { pebble_id: string; moment_id: string; name: string | null; happened_at: string; intensity: number; positiveness: number; emotion_id: string }[];
  skipped: { name: string | null }[];
};

/** Reference rows by slug, read as the user (reference tables are public). */
async function refs(c: SupabaseClient) {
  const { data: emotion } = await c.from("emotions").select("id").order("slug").limit(1).single();
  const { data: domains } = await c.from("domains").select("id, slug").in("slug", ["family", "work", "health"]);
  const by = Object.fromEntries((domains ?? []).map((d: { id: string; slug: string }) => [d.slug, d.id]));
  if (!emotion || !by.family || !by.work || !by.health) throw new Error("reference data missing (emotion, family/work/health domains)");
  return { emotionId: emotion.id as string, family: by.family as string, work: by.work as string, health: by.health as string };
}

async function saveMoment(c: SupabaseClient, name: string, at: string): Promise<string> {
  const { data, error } = await c.rpc("save_moment", { payload: { name, happened_at: at } });
  if (error || typeof data !== "string") throw new Error(`save_moment ${name}: ${error?.message}`);
  return data;
}
async function completePebble(c: SupabaseClient, momentId: string, emotionId: string, positiveness: number, domainIds: string[], soulIds: string[] = []): Promise<string> {
  const { data, error } = await c.rpc("complete_pebble", {
    payload: { moment_id: momentId, emotion_id: emotionId, intensity: 2, positiveness, domain_ids: domainIds, soul_ids: soulIds },
  });
  if (error || typeof data !== "string") throw new Error(`complete_pebble: ${error?.message}`);
  return data;
}
async function unlockedSlugs(c: SupabaseClient): Promise<string[]> {
  const { data, error } = await c.from("achievement_unlocks").select("achievements(slug)");
  if (error) throw new Error(`unlocks: ${error.message}`);
  return ((data ?? []) as unknown as { achievements: { slug: string } | null }[]).map((r) => r.achievements?.slug ?? "?").sort();
}

const anon = createClient(SUPABASE_URL, ANON_KEY, { auth: { persistSession: false } });
let alice: TestUser | null = null;
let bob: TestUser | null = null;
let carol: TestUser | null = null;

try {
  // 1. the first five ---------------------------------------------------------------------
  alice = await signUp("a");
  const a = alice.client;
  const r = await refs(a);

  const fresh = await a.rpc("check_achievements");
  check("a fresh account unlocks nothing", !fresh.error && (fresh.data as Unlock[]).length === 0, fresh.error?.message ?? JSON.stringify(fresh.data));

  const m1 = await saveMoment(a, `first ${runId}`, "2026-10-01T10:00:00Z");
  const afterMoment = await a.rpc("check_achievements");
  const momentSlugs = ((afterMoment.data ?? []) as Unlock[]).map((u) => u.slug);
  check("the first save_moment unlocks first-moment and nothing else",
    !afterMoment.error && momentSlugs.length === 1 && momentSlugs[0] === "first-moment",
    afterMoment.error?.message ?? JSON.stringify(momentSlugs));

  await completePebble(a, m1, r.emotionId, 1, [r.family]);
  const afterPebble = await a.rpc("check_achievements");
  const pebbleSlugs = ((afterPebble.data ?? []) as Unlock[]).map((u) => u.slug).sort();
  check("the first complete_pebble unlocks first-pebble, never blue-pebble",
    !afterPebble.error && pebbleSlugs.includes("first-pebble") && !pebbleSlugs.includes("blue-pebble") && !pebbleSlugs.includes("first-moment"),
    afterPebble.error?.message ?? JSON.stringify(pebbleSlugs));

  const del = await a.rpc("delete_moment", { p_moment_id: m1 });
  if (del.error) throw new Error(`delete_moment: ${del.error.message}`);
  const afterDelete = await unlockedSlugs(a);
  check("deleting the only moment keeps first-moment and first-pebble unlocked",
    afterDelete.includes("first-moment") && afterDelete.includes("first-pebble"), JSON.stringify(afterDelete));
  const recheck = await a.rpc("check_achievements");
  check("a re-check after the deletion grants nothing new and revokes nothing",
    !recheck.error && (recheck.data as Unlock[]).length === 0 && (await unlockedSlugs(a)).length === afterDelete.length,
    recheck.error?.message ?? JSON.stringify(recheck.data));

  // 2. the claim ---------------------------------------------------------------------------
  const early = await a.rpc("claim_blue_pebble");
  check("claim_blue_pebble raises not_ready before the five",
    !!early.error && early.error.message.includes("not_ready"), early.error?.message ?? "accepted");

  const { data: soul, error: soulErr } = await a.from("souls").insert({ user_id: alice.id, name: `Mara ${runId}` }).select("id").single();
  if (soulErr || !soul) throw new Error(`soul: ${soulErr?.message}`);
  const { error: colErr } = await a.from("collections").insert({ user_id: alice.id, name: `Autumn ${runId}`, mode: "stack" });
  if (colErr) throw new Error(`collection: ${colErr.message}`);
  const { error: glyphErr } = await a.from("glyphs").insert({
    user_id: alice.id, name: `glyph ${runId}`, view_box: "0 0 200 200",
    strokes: [{ d: "M20,20 L180,180", width: 6 }],
  });
  if (glyphErr) throw new Error(`glyph: ${glyphErr.message}`);
  const five = await a.rpc("check_achievements");
  const fiveSlugs = ((five.data ?? []) as Unlock[]).map((u) => u.slug);
  check("the soul, collection and glyph unlock their firsts, still never blue-pebble",
    !five.error && ["first-soul", "first-collection", "first-glyph"].every((s) => fiveSlugs.includes(s)) && !fiveSlugs.includes("blue-pebble"),
    five.error?.message ?? JSON.stringify(fiveSlugs));

  // Two claims race: both pass the gate, one inserts, the other hits the PK.
  const [c1, c2] = await Promise.all([a.rpc("claim_blue_pebble"), a.rpc("claim_blue_pebble")]);
  const rows = [...((c1.data ?? []) as Unlock[]), ...((c2.data ?? []) as Unlock[])];
  check("two concurrent claims unlock blue-pebble exactly once between them",
    !c1.error && !c2.error && rows.length === 1 && rows[0].slug === "blue-pebble",
    [c1.error?.message, c2.error?.message, JSON.stringify(rows)].filter(Boolean).join(" | "));
  const again = await a.rpc("claim_blue_pebble");
  check("a second claim returns no row", !again.error && (again.data as Unlock[]).length === 0, again.error?.message ?? JSON.stringify(again.data));
  const unlocks = await unlockedSlugs(a);
  check("exactly one blue-pebble unlock row", unlocks.filter((s) => s === "blue-pebble").length === 1, JSON.stringify(unlocks));
  const anonClaim = await anon.rpc("claim_blue_pebble");
  check("claim_blue_pebble is refused to anon", !!anonClaim.error, JSON.stringify(anonClaim.data));

  // 3. people_prompts ----------------------------------------------------------------------
  bob = await signUp("b");
  const b = bob.client;
  const empty = await b.rpc("people_prompts");
  const e = empty.data as Prompts;
  check("people_prompts on a fresh account: zero totals and no prompts",
    !empty.error && e.pebbles === 0 && e.souls === 0 && e.prompts.length === 0 && e.skipped.length === 0,
    empty.error?.message ?? JSON.stringify(e));

  // Seed, latest first in the names: outward+bright, outward+even, outward+low (skipped),
  // inward+bright (skipped), outward+bright with a soul (not soul-less), oldest outward+bright.
  const { data: bobSoul } = await b.from("souls").insert({ user_id: bob.id, name: `Elio ${runId}` }).select("id").single();
  const seed: [string, string, number, string[], string[]][] = [
    ["too old to mention",    "2026-09-20T09:00:00Z", -1, [r.family], []],
    ["oldest outward bright", "2026-10-01T09:00:00Z", 1, [r.family], []],
    ["with a soul",           "2026-10-02T09:00:00Z", 1, [r.family], [bobSoul!.id]],
    ["inward bright",         "2026-10-03T09:00:00Z", 1, [r.health], []],
    ["outward low",           "2026-10-04T09:00:00Z", -1, [r.family], []],
    ["outward even",          "2026-10-05T09:00:00Z", 0, [r.family, r.work], []],
    ["outward bright",        "2026-10-06T09:00:00Z", 1, [r.family], []],
  ];
  for (const [name, at, pos, doms, souls] of seed) {
    const mid = await saveMoment(b, `${name} ${runId}`, at);
    await completePebble(b, mid, r.emotionId, pos, doms, souls);
  }
  const got = await b.rpc("people_prompts");
  const p = got.data as Prompts;
  await dump("people-prompts", got.data);
  const names = p.prompts.map((x) => (x.name ?? "").replace(` ${runId}`, ""));
  check("totals count every pebble and every soul", p.pebbles === 7 && p.souls === 1, JSON.stringify([p.pebbles, p.souls]));
  check("prompts are the soul-less, outward, non-heavy pebbles, latest first, three at most",
    JSON.stringify(names) === JSON.stringify(["outward bright", "outward even", "oldest outward bright"]), JSON.stringify(names));
  check("a low pebble is never asked about, and an inward one neither: both are named as skipped",
    p.skipped.map((s) => (s.name ?? "").replace(` ${runId}`, "")).sort().join("|") === "inward bright|outward low",
    JSON.stringify(p.skipped));
  check("a reject older than the oldest prompt shown is not named",
    !p.skipped.some((s) => (s.name ?? "").startsWith("too old")), JSON.stringify(p.skipped));
  check("timestamps are whole-second UTC", p.prompts.every((x) => /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/.test(x.happened_at)),
    JSON.stringify(p.prompts.map((x) => x.happened_at)));
  const limited = await b.rpc("people_prompts", { p_limit: 1 });
  check("p_limit caps the prompts", !limited.error && (limited.data as Prompts).prompts.length === 1, limited.error?.message);
  const bad = await b.rpc("people_prompts", { p_limit: 0 });
  check("p_limit 0 is refused", !!bad.error && bad.error.message.includes("invalid_limit"), bad.error?.message ?? "accepted");
  const anonPrompts = await anon.rpc("people_prompts");
  check("people_prompts is refused to anon", !!anonPrompts.error, JSON.stringify(anonPrompts.data));

  carol = await signUp("c");
  const c = carol.client;
  for (const [name, at] of [["only inward", "2026-10-02T09:00:00Z"], ["only heavy", "2026-10-03T09:00:00Z"], ["older inward", "2026-10-01T09:00:00Z"]] as const) {
    const mid = await saveMoment(c, `${name} ${runId}`, at);
    await completePebble(c, mid, r.emotionId, name === "only heavy" ? -1 : 1, [name === "only heavy" ? r.family : r.health]);
  }
  const rejectsOnly = await c.rpc("people_prompts");
  const ro = rejectsOnly.data as Prompts;
  check("with pebbles but no eligible one, prompts are empty and the two latest rejects are named",
    !rejectsOnly.error && ro.pebbles === 3 && ro.prompts.length === 0
      && ro.skipped.map((s) => (s.name ?? "").replace(` ${runId}`, "")).join("|") === "only heavy|only inward",
    rejectsOnly.error?.message ?? JSON.stringify(ro));

  // 4. wraps (part 3) ----------------------------------------------------------------------
  // (added in part 3)
} catch (err) {
  failed += 1;
  console.error(`✗ aborted: ${err instanceof Error ? err.message : String(err)}`);
} finally {
  for (const u of [alice, bob, carol]) {
    if (!u) continue;
    const res = await deleteAccount(u.token).catch(() => null);
    if (!res?.ok) console.error(`cleanup: delete-account returned ${res?.status ?? "no response"}`);
  }
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed > 0 ? 1 : 0);
