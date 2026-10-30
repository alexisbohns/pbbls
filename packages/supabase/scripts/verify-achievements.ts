#!/usr/bin/env -S deno run --allow-env --allow-net --allow-read --allow-write
/**
 * Acceptance test for the achievements page back-end (#1305) — runs against the REMOTE project.
 *
 *   1. The model (part 1):
 *      - the six firsts unlock independently, so three pebbles earn all six;
 *      - every pebble-backed unlock records its crossing pebble;
 *      - check_achievements returns only the rows to celebrate: contributions,
 *        pebble-count-1 and a heavy-crossed level are recorded in
 *        achievement_unlocks but never returned (the shipped clients celebrate
 *        every returned row), and contributions pay nothing;
 *      - making the six firsts whole records one completion, once;
 *      - the new tables are owner-scoped and not client-writable.
 *
 * ACHIEVEMENTS_DUMP=1 writes the verbatim payloads to scripts/oracles/achievements/payloads/.
 *
 * Run: set -a; . ./.env; set +a; npm run db:verify:achievements --workspace=packages/supabase
 * Needs NO service-role key: signs up throwaway users (achievements-verify-*@example.test)
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

const DUMP = Deno.env.get("ACHIEVEMENTS_DUMP") === "1";
const ORACLE_DIR = new URL("./oracles/achievements/", import.meta.url);
// Verbatim: the clients' tests replay the server's bytes, numeric scale and all
// (the standing rule), so the dump re-asks PostgREST and writes its raw body.
async function dump(name: string, token: string, fn: string, args: Record<string, unknown> = {}) {
  if (!DUMP) return;
  const res = await fetch(`${SUPABASE_URL}/rest/v1/rpc/${fn}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, apikey: ANON_KEY!, "Content-Type": "application/json" },
    body: JSON.stringify(args),
  });
  if (!res.ok) throw new Error(`dump ${name}: ${res.status}`);
  await Deno.mkdir(new URL("payloads/", ORACLE_DIR), { recursive: true });
  await Deno.writeTextFile(new URL(`payloads/${name}.json`, ORACLE_DIR), await res.text());
}

const runId = crypto.randomUUID().slice(0, 8);
const password = `Ach-${crypto.randomUUID()}`;
type TestUser = { client: SupabaseClient; id: string; token: string };
type Unlock = { slug: string; karma_granted: number };

async function signUp(label: string): Promise<TestUser> {
  const client = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
  const email = `achievements-verify-${label}-${runId}@example.test`;
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
async function pebble(c: SupabaseClient, name: string, at: string, emotionId: string,
  intensity: number, positiveness: number, domainIds: string[]): Promise<{ momentId: string; pebbleId: string }> {
  const { data: momentId, error: mErr } = await c.rpc("save_moment", { payload: { name, happened_at: at } });
  if (mErr || typeof momentId !== "string") throw new Error(`save_moment ${name}: ${mErr?.message}`);
  const { data: pebbleId, error: pErr } = await c.rpc("complete_pebble", {
    payload: { moment_id: momentId, emotion_id: emotionId, intensity, positiveness, domain_ids: domainIds, soul_ids: [] },
  });
  if (pErr || typeof pebbleId !== "string") throw new Error(`complete_pebble ${name}: ${pErr?.message}`);
  return { momentId, pebbleId };
}
async function unlocks(c: SupabaseClient): Promise<{ slug: string; pebble_id: string | null }[]> {
  const { data, error } = await c.from("achievement_unlocks").select("pebble_id, achievements(slug)");
  if (error) throw new Error(`unlocks: ${error.message}`);
  return ((data ?? []) as unknown as { pebble_id: string | null; achievements: { slug: string } | null }[])
    .map((r) => ({ slug: r.achievements?.slug ?? "?", pebble_id: r.pebble_id }));
}

const anon = createClient(SUPABASE_URL, ANON_KEY, { auth: { persistSession: false } });
const users: TestUser[] = [];

try {
  const alice = await signUp("a"); users.push(alice);
  const a = alice.client;
  const { data: emo } = await a.from("emotions").select("id").order("slug").limit(1).single();
  const { data: dom } = await a.from("domains").select("id").order("slug").limit(1).single();
  if (!emo || !dom) throw new Error("reference data missing");

  // 1. the model ----------------------------------------------------------------------------
  // A silent row is RECORDED but NOT RETURNED: the shipped clients celebrate
  // every returned row, so the assertions on silence read achievement_unlocks.
  // Three pebbles earn all six firsts: (small, lowlight), (medium, neutral), (large, highlight).
  const { data: silentRows } = await a.from("achievements").select("id, slug")
    .in("family", ["emotion_first", "domain_first", "first_stone", "set_complete"]);
  const silentIds = (silentRows ?? []).map((r: { id: string }) => r.id);
  const silentSlugs = new Set([...(silentRows ?? []).map((r: { slug: string }) => r.slug), "pebble-count-1"]);
  const returnsNothingSilent = (rows: Unlock[]) => rows.every((u) => !silentSlugs.has(u.slug));

  const p1 = await pebble(a, `heavy small ${runId}`, "2026-03-02T09:00:00Z", emo.id, 1, -1, [dom.id]);
  const firstRes = await a.rpc("check_achievements");
  if (firstRes.error) throw new Error(`check_achievements: ${firstRes.error.message}`);
  const first = firstRes.data as Unlock[];
  check("the first, heavy pebble returns only first-moment, the one row celebrated (D7, D8)",
    first.map((u) => u.slug).join(",") === "first-moment", JSON.stringify(first));
  const heldFirst = (await unlocks(a)).map((u) => u.slug);
  check("…and records pebble-count-1, first-pebble, both stone firsts, an emotion and a domain first",
    ["pebble-count-1", "first-pebble", "first-stone-small", "first-stone-lowlight"].every((s) => heldFirst.includes(s))
      && heldFirst.some((s) => s.startsWith("emotion-first-")) && heldFirst.some((s) => s.startsWith("domain-first-")),
    JSON.stringify(heldFirst));
  const { data: karma } = await a.from("karma_events").select("delta, ref_id").eq("reason", "grant").in("ref_id", silentIds);
  check("the contributions pay nothing (D7)", (karma ?? []).length === 0, JSON.stringify(karma));

  await pebble(a, `medium even ${runId}`, "2026-03-03T09:00:00Z", emo.id, 2, 0, [dom.id]);
  const p3 = await pebble(a, `large bright ${runId}`, "2026-03-04T09:00:00Z", emo.id, 3, 1, [dom.id]);
  const second = (await a.rpc("check_achievements")).data as Unlock[];
  const six = ["small", "medium", "large", "lowlight", "neutral", "highlight"].map((k) => `first-stone-${k}`);
  const held = (await unlocks(a)).map((u) => u.slug);
  check("the second check (medium neutral, large bright) returns nothing", (second ?? []).length === 0, JSON.stringify(second));
  check("three pebbles earn all six firsts", six.every((s) => held.includes(s)), JSON.stringify(held));
  check("no returned row is ever a silent family", returnsNothingSilent([...first, ...(second ?? [])]),
    JSON.stringify([...first, ...(second ?? [])]));

  const rows = await unlocks(a);
  const pebbleOf = (s: string) => rows.find((r) => r.slug === s)?.pebble_id ?? null;
  check("pebble-count-1 and first-pebble record the first pebble (D4)",
    pebbleOf("pebble-count-1") === p1.pebbleId && pebbleOf("first-pebble") === p1.pebbleId,
    JSON.stringify([pebbleOf("pebble-count-1"), pebbleOf("first-pebble"), p1.pebbleId]));
  check("first-stone-large records the large pebble", pebbleOf("first-stone-large") === p3.pebbleId,
    JSON.stringify([pebbleOf("first-stone-large"), p3.pebbleId]));
  check("first-moment records no pebble", pebbleOf("first-moment") === null, JSON.stringify(pebbleOf("first-moment")));

  const { data: comps } = await a.from("achievement_completions").select("stack, pulled_at");
  check("the six firsts made whole record one completion, not yet pulled",
    (comps ?? []).length === 1 && comps![0].stack === "six_firsts" && comps![0].pulled_at === null, JSON.stringify(comps));
  await a.rpc("check_achievements");
  const { data: comps2 } = await a.from("achievement_completions").select("stack");
  check("a re-check records no second completion", (comps2 ?? []).length === 1, JSON.stringify(comps2));

  const { error: wv } = await a.from("achievement_views").insert({ user_id: alice.id, card: "explorer" });
  const { error: wc } = await a.from("achievement_completions").update({ pulled_at: new Date().toISOString() }).eq("stack", "six_firsts");
  const { data: stillOpen } = await a.from("achievement_completions").select("pulled_at").eq("stack", "six_firsts").single();
  check("a client cannot write achievement_views or achievement_completions",
    wv !== null && (wc !== null || stillOpen?.pulled_at === null), [wv?.message, wc?.message].join(" | "));

  const bob = await signUp("b"); users.push(bob);
  const { data: peek } = await bob.client.from("achievement_completions").select("*");
  check("a user sees only their own completions", (peek ?? []).length === 0, JSON.stringify(peek));
  // A fresh bright first pebble: first-pebble celebrates it; pebble-count-1 and
  // its stone firsts are recorded, not returned.
  await pebble(bob.client, `bright first ${runId}`, "2026-03-05T09:00:00Z", emo.id, 2, 1, [dom.id]);
  const bobRows = ((await bob.client.rpc("check_achievements")).data ?? []) as Unlock[];
  const bobSlugs = bobRows.map((u) => u.slug);
  check("a bright first pebble returns first-pebble, not pebble-count-1 nor any first-stone-*",
    bobSlugs.includes("first-pebble") && !bobSlugs.includes("pebble-count-1")
      && !bobSlugs.some((s) => s.startsWith("first-stone-")) && returnsNothingSilent(bobRows),
    JSON.stringify(bobSlugs));
  const anonCheck = await anon.rpc("check_achievements");
  check("check_achievements is refused to anon", !!anonCheck.error, JSON.stringify(anonCheck.data));
  const { data: ladder } = await a.from("achievements").select("threshold, is_active").eq("family", "pebble_count").order("threshold");
  const want = [1, 10, 25, 50, 100, 250, 500, 1000, ...Array.from({ length: 18 }, (_, k) => 1500 + 500 * k)];
  check("the pebble ladder has no gap up to 10 000 and every rung is active (D3)",
    JSON.stringify((ladder ?? []).map((r: { threshold: number }) => r.threshold)) === JSON.stringify(want)
      && (ladder ?? []).every((r: { is_active: boolean }) => r.is_active), JSON.stringify(ladder));
  // D3's other half, "no user within 500 of the top", needs every user's count:
  // it cannot run anon. The maintainer runs it by hand in the SQL editor:
  //   select max(n) from (select count(*) n from pebbles group by user_id) x;   -- must stay below 9 500
  // check_achievements writes, so its dump is the first call's parsed rows (shape only).
  if (DUMP) await Deno.mkdir(new URL("payloads/", ORACLE_DIR), { recursive: true });
  if (DUMP) await Deno.writeTextFile(new URL("payloads/check-achievements.json", ORACLE_DIR), JSON.stringify(first, null, 2) + "\n");
} catch (err) {
  failed += 1;
  console.error(`✗ aborted: ${err instanceof Error ? err.message : String(err)}`);
} finally {
  for (const u of users) {
    const res = await deleteAccount(u.token).catch(() => null);
    if (!res?.ok) console.error(`cleanup: delete-account returned ${res?.status ?? "no response"}`);
  }
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed > 0 ? 1 : 0);
