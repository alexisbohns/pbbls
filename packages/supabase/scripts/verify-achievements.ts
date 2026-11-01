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
 *   2. The read (part 2):
 *      - get_achievements answers with every card and header key, the first pebble at
 *        the centre; its raw body holds no null but shelf[].threshold, and an unnamed
 *        moment's snapshot omits name (D17);
 *      - six_firsts and the first five come in order, rings_done holds six_firsts, and
 *        the completion carries its pebble, is dated at whole seconds and is offered at
 *        06:00 the next local day, so not ready yet (D9);
 *      - emotions serve 38 seats and the sheet's seven families, the met seat dated;
 *      - domains report the entered one and the 'few' tier; coverage stays few just
 *        below a third, then crosses third, half, most and all at their boundaries (D11);
 *      - pebbles report the next level, what is left and the progress; after the only
 *        pebble is deleted, level 1 stays dated without a pebble and the next restarts;
 *        with no glyph, both glyph ladders start at their first rung;
 *      - nothing is new before the first look (D10); timestamps are whole-second UTC;
 *      - the owner's shelf leaves out the private families (D18);
 *      - the year serves its day target and done flags, and achievements_year equals
 *        the oracle (scripts/oracles/achievements/year.js) month by month on fixed edge
 *        days (zone, week, DST and month boundaries, the current month around today)
 *        plus a seeded random 2025 (ACHIEVEMENTS_SEED replays one), and on the current
 *        year, next step and listed months included; today is the server's;
 *      - in a fresh user's first month only the full weeks from the first moment's
 *        day on count, and the current year equals the oracle;
 *      - get_achievements serves the current year and each past year with a moment,
 *        newest first, as the oracle's yearsServed does;
 *      - an unknown zone is refused, and anon is refused.
 *   3. The writes (part 3):
 *      - mark_achievements_seen refuses an unknown card; an item unlocked after a look
 *        is new and the ones before it are not, on the explorer and the emotions card;
 *        the next look clears it, and seen is served at whole-second UTC (D10);
 *      - the check never unlocks a set_complete row, active or not;
 *      - the emotions and domains ceremonies are deferred past V1 (#1458): their
 *        set_complete rows are inactive, and a domains set made whole is recorded
 *        but its card carries no completion and its pull says not_ready;
 *      - pull_ceremony refuses an unknown stack, an unknown or null zone, and raises
 *        not_ready before the offer (in two zones, agreeing with the payload's ready)
 *        and without a completion; anon is refused (D9, §3.4);
 *      - with SUPABASE_SERVICE_ROLE_KEY only (a run by hand; CI has no such key): the
 *        completion is back-dated past its offer, two concurrent pulls return one row
 *        between them, a third pull returns no row, and the payload's completion
 *        carries pulled_at and is no longer ready. While complete-six-firsts is active,
 *        the pull unlocks it and pays its karma at most once and as returned; while it
 *        is inactive (until the clients ship the ceremony), the row says 0 and nothing
 *        is unlocked or paid. Past their offer, an emotions and a domains completion
 *        stay off their cards, refuse the pull and stay unpulled (#1458). Without the
 *        key a completion can never be past its offer in the run: the offer is never
 *        less than 5 hours after completed_at.
 *
 * ACHIEVEMENTS_DUMP=1 writes the verbatim payloads to scripts/oracles/achievements/payloads/.
 *
 * Run: set -a; . ./.env; set +a; npm run db:verify:achievements --workspace=packages/supabase
 * Anon in CI. With SUPABASE_SERVICE_ROLE_KEY set it also runs section 3's ready path, which
 * writes (back-dates a completion) only to this run's own users. Signs up throwaway users (achievements-verify-*@example.test)
 * and deletes them through the real `delete-account` edge function, even on failure.
 */

const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY");
if (!SUPABASE_URL || !ANON_KEY) {
  console.error("SUPABASE_URL and SUPABASE_ANON_KEY must be set");
  Deno.exit(2);
}

import { createClient, type SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";
import { yearPayload, yearsServed } from "./oracles/achievements/year.js";

let passed = 0;
let failed = 0;
function check(name: string, ok: boolean, detail?: string) {
  if (ok) { passed += 1; console.log(`✓ ${name}`); }
  else { failed += 1; console.log(`✗ ${name}${detail ? ` — ${detail}` : ""}`); }
}
// jsonb sorts object keys (by length, then bytewise), so whole-object
// comparisons with the oracle go through a key-sorted copy.
const canon = (v: unknown): unknown => Array.isArray(v) ? v.map(canon)
  : v && typeof v === "object" ? Object.fromEntries(Object.keys(v as object).sort().map((k) => [k, canon((v as Record<string, unknown>)[k])])) : v;
const same = (x: unknown, y: unknown) => JSON.stringify(canon(x)) === JSON.stringify(canon(y));
// Every path holding a JSON null (D17: an absent value omits its key).
const nullPaths = (v: unknown, path = ""): string[] => v === null ? [path]
  : Array.isArray(v) ? v.flatMap((x, i) => nullPaths(x, `${path}[${i}]`))
  : typeof v === "object" ? Object.entries(v as Record<string, unknown>).flatMap(([k, x]) => nullPaths(x, path ? `${path}.${k}` : k))
  : [];

// Dates in a zone: the local day of an instant, a day plus n, and the UTC
// instant of a local wall time (never at a DST jump's hour here).
const pad = (n: number) => String(n).padStart(2, "0");
const localDate = (instant: string | Date, zone: string) =>
  new Intl.DateTimeFormat("en-CA", { timeZone: zone }).format(new Date(instant));
const addDays = (date: string, n: number) =>
  new Date(Date.UTC(+date.slice(0, 4), +date.slice(5, 7) - 1, +date.slice(8, 10) + n)).toISOString().slice(0, 10);
function zonedToUtc(date: string, hh: number, mm: number, zone: string): Date {
  const guess = new Date(`${date}T${pad(hh)}:${pad(mm)}:00Z`);
  const parts = Object.fromEntries(new Intl.DateTimeFormat("en-US", {
    timeZone: zone, hourCycle: "h23", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit",
  }).formatToParts(guess).map((x) => [x.type, x.value]));
  const asUtc = Date.UTC(+parts.year, +parts.month - 1, +parts.day, +parts.hour, +parts.minute, +parts.second);
  return new Date(2 * guess.getTime() - asUtc);
}
const wholeSecondUtc = (d: Date) => d.toISOString().replace(/\.\d{3}Z$/, "Z");
const WHOLE_SECOND = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/;

const DUMP = Deno.env.get("ACHIEVEMENTS_DUMP") === "1";
const ORACLE_DIR = new URL("./oracles/achievements/", import.meta.url);
// Verbatim: the clients' tests replay the server's bytes, numeric scale and all
// (the standing rule), so the dump re-asks PostgREST and writes its raw body.
async function rpcRaw(token: string, fn: string, args: Record<string, unknown> = {}): Promise<string> {
  const res = await fetch(`${SUPABASE_URL}/rest/v1/rpc/${fn}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, apikey: ANON_KEY!, "Content-Type": "application/json" },
    body: JSON.stringify(args),
  });
  if (!res.ok) throw new Error(`${fn}: ${res.status}`);
  return await res.text();
}
async function dump(name: string, token: string, fn: string, args: Record<string, unknown> = {}) {
  if (!DUMP) return;
  const body = await rpcRaw(token, fn, args);
  await Deno.mkdir(new URL("payloads/", ORACLE_DIR), { recursive: true });
  await Deno.writeTextFile(new URL(`payloads/${name}.json`, ORACLE_DIR), body);
}

const runId = crypto.randomUUID().slice(0, 8);
const password = `Ach-${crypto.randomUUID()}`;
type TestUser = { client: SupabaseClient; id: string; token: string };
type Unlock = { slug: string; karma_granted: number };
type Pull = { stack: string; karma_granted: number };
// get_achievements (spec §3.3), only as far as the checks below read it.
type Stone = { moment_id: string; name?: string; happened_at: string; emotion_id: string; intensity: number; positiveness: number };
type Completion = { completed_at: string; offered_from: string; ready: boolean; pulled_at?: string; pebble?: Stone };
type Ladder = { count: number; levels: { threshold: number; on: string; new: boolean; pebble?: Stone }[];
  next?: { threshold: number; left: number; progress: number }; ladder: number[]; then_every?: number };
type Month = { month: number; done?: { moments: boolean; pebbles: boolean; weeks: boolean } };
type Year = { year: number; current: boolean; days_target: number; months: Month[]; listed: number[] };
type Achievements = {
  since?: string; days_kept?: number; seen: Record<string, string>;
  explorer: {
    first_pebble?: Stone; rings_done: string[]; completion?: Completion;
    first_five: { slug: string; done: boolean; new: boolean }[];
    six_firsts: { kind: string; done: boolean; new: boolean; pebble?: Stone }[];
  };
  emotions: { count: number; of: number; seats: { emotion_id: string; met: boolean; new: boolean; on?: string }[];
    families: { family: string; met: number[] }[]; completion?: Completion };
  domains: { count: number; of: number; coverage: string; entered: unknown[]; not_yet: string[]; completion?: Completion };
  pebbles: Ladder; glyphs_carved: Ladder; glyph_sales: Ladder;
  years: Year[];
  shelf: { slug: string; family: string }[]; shelf_count: number;
};

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
// An unnamed moment needs a photo (D3, a date plus a name or a photo): a snap
// row under the owner's folder, no upload, as verify-pebble-visibility does.
async function pebble(c: SupabaseClient, name: string | null, at: string, emotionId: string,
  intensity: number, positiveness: number, domainIds: string[], owner?: string): Promise<{ momentId: string; pebbleId: string }> {
  const payload = name !== null ? { name, happened_at: at }
    : { happened_at: at, snaps: [{ storage_path: `${owner}/verify-${runId}-${crypto.randomUUID()}.jpg`, sort_order: 0 }] };
  const { data: momentId, error: mErr } = await c.rpc("save_moment", { payload });
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

  // Unnamed, so the dumped payload carries an omitted name (D17).
  await pebble(a, null, "2026-03-03T09:00:00Z", emo.id, 2, 0, [dom.id], alice.id);
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
  const bobFirstAt = "2026-03-05T09:00:00Z";   // section 2's oracle counts it too
  await pebble(bob.client, `bright first ${runId}`, bobFirstAt, emo.id, 2, 1, [dom.id]);
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

  // 2. the read (part 2) ------------------------------------------------------------------
  const tz = "Europe/Paris";
  const got = await a.rpc("get_achievements", { p_tz: tz });
  const pl = got.data as Achievements;
  await dump("get-achievements", alice.token, "get_achievements", { p_tz: tz });
  check("get_achievements answers with every card", !got.error && !!pl
    && ["since", "days_kept", "explorer", "emotions", "domains", "pebbles", "glyphs_carved", "glyph_sales", "years",
      "seen", "shelf", "shelf_count"].every((k) => k in pl),
    got.error?.message ?? Object.keys(pl ?? {}).join(","));
  const stray = (raw: string) => nullPaths(JSON.parse(raw)).filter((path) => !/^shelf\[\d+\]\.threshold$/.test(path));
  const aliceStray = stray(await rpcRaw(alice.token, "get_achievements", { p_tz: tz }));
  check("the raw body holds no null but shelf[].threshold (D17)", aliceStray.length === 0, JSON.stringify(aliceStray));
  const medium = pl.explorer.six_firsts.find((s) => s.kind === "medium");
  check("an unnamed moment's snapshot omits name", !!medium?.pebble && !("name" in medium.pebble), JSON.stringify(medium));
  check("the explorer's centre is the first pebble", pl.explorer.first_pebble?.moment_id === p1.momentId,
    JSON.stringify(pl.explorer.first_pebble));
  check("six_firsts and the first five are listed in order, the six done",
    pl.explorer.six_firsts.map((s) => s.kind).join(",") === "small,medium,large,lowlight,neutral,highlight"
      && pl.explorer.six_firsts.every((s) => s.done)
      && pl.explorer.first_five.map((s) => s.slug).join(",") === "first-moment,first-pebble,first-collection,first-soul,first-glyph",
    JSON.stringify(pl.explorer));
  check("rings_done holds six_firsts, and the completion is not ready before tomorrow 06:00",
    pl.explorer.rings_done.includes("six_firsts") && pl.explorer.completion?.ready === false,
    JSON.stringify([pl.explorer.rings_done, pl.explorer.completion]));
  const done = pl.explorer.completion?.completed_at ?? "";
  const offer = WHOLE_SECOND.test(done) ? wholeSecondUtc(zonedToUtc(addDays(localDate(done, tz), 1), 6, 0, tz)) : "?";
  check("the completion is dated at whole seconds and offered at 06:00 the next local day (D9)",
    WHOLE_SECOND.test(done) && pl.explorer.completion?.offered_from === offer,
    JSON.stringify([done, pl.explorer.completion?.offered_from, offer]));
  check("emotions serve the sheet's order: seven families, the met seat dated",
    pl.emotions.families.length === 7 && pl.emotions.families.flatMap((f) => f.met).length === 1
      && typeof pl.emotions.seats.find((s) => s.met)?.on === "string", JSON.stringify(pl.emotions.families));
  check("the year serves its day target and done flags", pl.years[0].days_target === 14
    && pl.years[0].months.every((m) => typeof m.done?.moments === "boolean"), JSON.stringify(pl.years[0].months[0]));
  check("the owner's shelf leaves out the private families (D18)",
    Array.isArray(pl.shelf) && pl.shelf.length > 0 && pl.shelf.length <= 6
      && pl.shelf.every((s) => !["emotion_first", "domain_first", "first_stone", "set_complete"].includes(s.family))
      && pl.shelf_count >= pl.shelf.length, JSON.stringify([pl.shelf, pl.shelf_count]));
  check("the completion carries the pebble that made it whole", typeof pl.explorer.completion?.pebble?.moment_id === "string",
    JSON.stringify(pl.explorer.completion));
  check("emotions has 38 seats with one met", pl.emotions.seats.length === 38 && pl.emotions.count === 1
    && pl.emotions.seats.filter((s) => s.met).length === 1, JSON.stringify([pl.emotions.count, pl.emotions.of]));
  check("domains report one entered and the 'few' tier", pl.domains.count === 1 && pl.domains.coverage === "few"
    && pl.domains.entered.length === 1 && pl.domains.not_yet.length === pl.domains.of - 1, JSON.stringify(pl.domains));
  check("pebbles: three kept, the next is 10 with 7 left and progress 0.222",
    pl.pebbles.count === 3 && pl.pebbles.next?.threshold === 10 && pl.pebbles.next?.left === 7
      && pl.pebbles.next?.progress === 0.222 && pl.pebbles.then_every === 500, JSON.stringify(pl.pebbles));
  check("nothing is new before the first look (D10)",
    pl.explorer.six_firsts.every((s) => s.new === false) && pl.pebbles.levels.every((l) => l.new === false),
    JSON.stringify(pl.explorer.six_firsts.map((s) => s.new)));
  check("timestamps are whole-second UTC", WHOLE_SECOND.test(pl.explorer.first_pebble?.happened_at ?? ""),
    pl.explorer.first_pebble?.happened_at);

  // The year disc against the oracle, for Bob: a fixed set of edge days, then a
  // seeded random 2025, compared month by month, plus the current year.
  const b = users[1].client;
  // Today as the server sees it: the account's first day plus the days kept. A run
  // straddling local midnight between this read and the year reads can still
  // disagree by a day; re-run it.
  const bobBefore = (await b.rpc("get_achievements", { p_tz: tz })).data as Achievements;
  if (!bobBefore.since || typeof bobBefore.days_kept !== "number") throw new Error("get_achievements: no since/days_kept for Bob");
  const today = addDays(bobBefore.since, bobBefore.days_kept - 1);
  const { data: emos } = await b.from("emotions").select("id, emotion_categories(slug)");
  const fams = (emos ?? []) as unknown as { id: string; emotion_categories: { slug: string } }[];
  const familyOf = (id: string) => fams.find((e) => e.id === id)?.emotion_categories.slug ?? "";
  const items: { date: string; pebble: boolean; family: string; intensity: number }[] =
    [{ date: localDate(bobFirstAt, tz), pebble: true, family: familyOf(emo.id), intensity: 2 }];
  const seedMoment = async (at: string, isPebble: boolean, emotionId: string, intensity: number) => {
    const { data: mid, error: mErr } = await b.rpc("save_moment", { payload: { name: `y ${at} ${runId}`, happened_at: at } });
    if (mErr) throw new Error(`save_moment: ${mErr.message}`);
    if (isPebble) {
      const { error: pErr } = await b.rpc("complete_pebble", { payload: { moment_id: mid, emotion_id: emotionId, intensity, positiveness: 1, domain_ids: [dom.id], soul_ids: [] } });
      if (pErr) throw new Error(`complete_pebble: ${pErr.message}`);
    }
    items.push({ date: localDate(at, tz), pebble: isPebble, family: familyOf(emotionId), intensity });
  };
  // Fixed edges: a UTC evening that is the next day (and month) in Paris, a
  // Sunday night either side of a week boundary, the DST day, days 29–31 and a
  // week across a month end, then the current month around today.
  const fixed: [string, boolean][] = [
    ["2025-05-31T22:30:00Z", true],    // 1 June in Paris
    ["2025-06-15T21:30:00Z", true],    // Sunday 23:30 in Paris
    ["2025-06-15T22:30:00Z", false],   // Monday 00:30 in Paris: the next week
    ["2025-03-30T00:30:00Z", false],   // the DST day, before the jump
    ["2025-03-30T22:30:00Z", true],    // that evening is 31 March in Paris
    ["2025-03-31T10:00:00Z", true],    // the week of Monday 31 March ends in April
    ["2025-04-01T10:00:00Z", false],
    ["2025-08-29T10:00:00Z", true], ["2025-08-30T10:00:00Z", false], ["2025-08-31T10:00:00Z", true],
  ];
  const monthStart = `${today.slice(0, 8)}01`;
  for (const d of new Set([monthStart, addDays(today, -8), addDays(today, -1), today])) {
    if (d < monthStart) continue;
    fixed.push([zonedToUtc(d, 0, 30, tz).toISOString(), d !== today]);   // 00:30 local: already past today
  }
  for (const [at, isPebble] of fixed) await seedMoment(at, isPebble, emo.id, 2);
  // Seeded random months (March to June 2025) keep the harness to ~120
  // round-trips while still covering kept and quiet; May and June are denser,
  // so most runs reach the inner circle and the rim as well.
  const seed0 = BigInt(Deno.env.get("ACHIEVEMENTS_SEED") ?? (parseInt(runId, 16) || 1));
  console.log(`  year disc seed=${seed0} (rerun with ACHIEVEMENTS_SEED=${seed0})`);
  // Exact in BigInt: the LCG's product overflows a double's 53 bits.
  let seed = seed0;
  const rand = () => Number(seed = (seed * 1103515245n + 12345n) % 2147483648n) / 2147483648;
  for (let m = 3; m <= 6; m++) {
    for (let d = 1; d <= 28; d++) {
      if (rand() > ({ 5: 0.65, 6: 0.85 }[m] ?? 0.35)) continue;
      const isPebble = rand() > 0.3;
      const e = fams[Math.floor(rand() * fams.length)];
      const intensity = 1 + Math.floor(rand() * 3);
      await seedMoment(`2025-${pad(m)}-${pad(d)}T10:00:00Z`, isPebble, e.id, intensity);
    }
  }
  const firstMoment = items.map((i) => i.date).sort()[0] ?? null;
  const y2025 = await b.rpc("achievements_year", { p_year: 2025, p_tz: tz });
  const got2025 = y2025.data as Year | null;
  const expected = yearPayload(2025, items, today, firstMoment);
  const mismatches = expected.months.filter((e, k) => !same(e, got2025?.months[k]));
  check("achievements_year(2025) equals the oracle month by month", !y2025.error && mismatches.length === 0,
    y2025.error?.message ?? JSON.stringify(mismatches.slice(0, 2)) + " vs " + JSON.stringify(got2025?.months.slice(0, 2)));
  check("the listed months equal the oracle's", same(got2025?.listed, expected.listed),
    JSON.stringify([got2025?.listed, expected.listed]));
  const yNow = await b.rpc("achievements_year", { p_year: Number(today.slice(0, 4)), p_tz: tz });
  const expNow = yearPayload(Number(today.slice(0, 4)), items, today, firstMoment);
  check("the current year equals the oracle, next step included", same(yNow.data, expNow),
    JSON.stringify([yNow.data?.next_step, expNow.next_step]) + " " + JSON.stringify(yNow.data).slice(0, 300));
  const bobRaw = await rpcRaw(users[1].token, "get_achievements", { p_tz: tz });
  const pb = JSON.parse(bobRaw) as Achievements;
  check("get_achievements serves the current year and each past year with a moment, newest first",
    same(pb.years.map((y) => y.year), yearsServed(items, today)),
    JSON.stringify([pb.years.map((y) => y.year), yearsServed(items, today)]));
  const bobStray = stray(bobRaw);
  check("Bob's raw body holds no null but shelf[].threshold (D17)", bobStray.length === 0, JSON.stringify(bobStray));
  await dump("get-achievements-year", users[1].token, "get_achievements", { p_tz: tz });

  // The first month (D12): a fresh user who began a few days ago, in this month,
  // and has kept every day since. Only the full weeks starting on or after the
  // first moment's day count, so no week from before it holds the month back.
  const erin = await signUp("e"); users.push(erin);
  const ePl = (await erin.client.rpc("get_achievements", { p_tz: tz })).data as Achievements;
  if (!ePl.since || typeof ePl.days_kept !== "number") throw new Error("get_achievements: no since/days_kept for Erin");
  const eToday = addDays(ePl.since, ePl.days_kept - 1);
  const eMonthStart = `${eToday.slice(0, 8)}01`;
  const eFrom = addDays(eToday, -9) < eMonthStart ? eMonthStart : addDays(eToday, -9);
  const eItems: { date: string; pebble: boolean; family: string; intensity: number }[] = [];
  for (let d = eFrom; d <= eToday; d = addDays(d, 1)) {
    // today's moment a minute ago at the latest, so it is never in the future
    const at = d < eToday ? zonedToUtc(d, 10, 0, tz) : new Date(Math.min(Date.now() - 60_000, zonedToUtc(d, 0, 30, tz).getTime()));
    await pebble(erin.client, `first month ${d} ${runId}`, at.toISOString(), emo.id, 2, 1, [dom.id]);
    eItems.push({ date: localDate(at, tz), pebble: true, family: familyOf(emo.id), intensity: 2 });
  }
  const eFirst = eItems.map((i) => i.date).sort()[0];
  // The weeks that count, worked out by hand: Mondays on or after the first day
  // whose Sunday is inside the month and before today.
  const eMonthEnd = addDays(`${addDays(eMonthStart, 31).slice(0, 8)}01`, -1);
  let eWeeks = 0;
  for (let d = eMonthStart; d <= eMonthEnd; d = addDays(d, 1)) {
    const monday = new Date(`${d}T00:00:00Z`).getUTCDay() === 1;
    if (monday && d >= eFirst && addDays(d, 6) <= eMonthEnd && addDays(d, 6) < eToday) eWeeks += 1;
  }
  const eYear = await erin.client.rpc("achievements_year", { p_year: Number(eToday.slice(0, 4)), p_tz: tz });
  const eGot = eYear.data as (Year & { months: (Month & { full_weeks: number; weeks_filled: number })[]; next_step?: unknown }) | null;
  const eCur = eGot?.months[Number(eToday.slice(5, 7)) - 1];
  check("in the first month, full_weeks counts only the weeks from the first moment's day on",
    !eYear.error && eCur?.full_weeks === eWeeks && eCur?.weeks_filled === eWeeks,
    eYear.error?.message ?? JSON.stringify([eFirst, eToday, eWeeks, eCur]));
  const eExp = yearPayload(Number(eToday.slice(0, 4)), eItems, eToday, eFirst);
  check("…and the fresh user's current year equals the oracle, next step included", same(eGot, eExp),
    JSON.stringify([eGot?.next_step, eExp.next_step, eCur, eExp.months[Number(eToday.slice(5, 7)) - 1]]));

  // Coverage tiers at their boundaries (D11): one pebble per step, on a fresh user.
  const carol = await signUp("c"); users.push(carol);
  const { data: allDomains } = await carol.client.from("domains").select("id").order("slug");
  const domIds = (allDomains ?? []).map((d: { id: string }) => d.id);
  const total = domIds.length;
  const tierAt = async (n: number) => {
    await pebble(carol.client, `cover ${n} ${runId}`, "2026-03-10T09:00:00Z", emo.id, 2, 1, domIds.slice(0, n));
    await carol.client.rpc("check_achievements");
    return ((await carol.client.rpc("get_achievements", { p_tz: tz })).data as Achievements).domains.coverage;
  };
  const expectTier = (n: number) => n === 0 ? "none" : n < total / 3 ? "few" : n < total / 2 ? "third" : n < total * 0.75 ? "half" : n < total ? "most" : "all";
  const steps = [Math.ceil(total / 3) - 1, Math.ceil(total / 3), Math.ceil(total / 2), Math.ceil(total * 0.75), total];
  const tiers: string[] = [];
  for (const n of steps) tiers.push(await tierAt(n));
  check("coverage stays few just below a third, then crosses third, half, most and all at their boundaries",
    JSON.stringify(tiers) === JSON.stringify(steps.map(expectTier)) && tiers.join(",") === "few,third,half,most,all",
    JSON.stringify([steps, tiers]));

  // The ladder after a deletion (D4: badges are permanent): a fresh user's only
  // pebble earns level 1, then goes.
  const dave = await signUp("d"); users.push(dave);
  const only = await pebble(dave.client, `only ${runId}`, "2026-03-11T09:00:00Z", emo.id, 2, 0, [dom.id]);
  await dave.client.rpc("check_achievements");
  const delOnly = await dave.client.rpc("delete_pebble", { p_pebble_id: only.pebbleId });
  if (delOnly.error) throw new Error(`delete_pebble: ${delOnly.error.message}`);
  const pd = ((await dave.client.rpc("get_achievements", { p_tz: tz })).data as Achievements);
  const one = pd.pebbles.levels.find((l) => l.threshold === 1);
  check("after its pebble goes, the count is 0, the next is 10 from scratch, and level 1 stays dated without a pebble",
    pd.pebbles.count === 0 && same(pd.pebbles.next, { threshold: 10, left: 10, progress: 0 })
      && !!one && !("pebble" in one) && typeof one.on === "string",
    JSON.stringify(pd.pebbles));
  check("with no glyph, the carved and sales ladders start at their first rung",
    same(pd.glyphs_carved.next, { threshold: 10, left: 10, progress: 0 }) && same(pd.glyph_sales.next, { threshold: 1, left: 1, progress: 0 })
      && pd.glyphs_carved.count === 0 && pd.glyph_sales.count === 0,
    JSON.stringify([pd.glyphs_carved, pd.glyph_sales]));

  const badTz = await b.rpc("get_achievements", { p_tz: "Mars/Olympus" });
  check("get_achievements refuses an unknown zone", !!badTz.error && badTz.error.message.includes("invalid_tz"), badTz.error?.message ?? "accepted");
  const anonRead = await anon.rpc("get_achievements", { p_tz: tz });
  check("get_achievements is refused to anon", !!anonRead.error, JSON.stringify(anonRead.data));

  // 3. the writes (part 3) ---------------------------------------------------------------
  const read = async (c: SupabaseClient) => {
    const r = await c.rpc("get_achievements", { p_tz: tz });
    if (r.error) throw new Error(`get_achievements: ${r.error.message}`);
    return r.data as Achievements;
  };
  const refused = (r: { error: { message: string } | null }, code: string) => !!r.error && r.error.message.includes(code);
  const markBad = await a.rpc("mark_achievements_seen", { p_card: "nope" });
  check("mark_achievements_seen refuses an unknown card", refused(markBad, "invalid_card"), markBad.error?.message ?? "accepted");
  const look1 = await a.rpc("mark_achievements_seen", { p_card: "explorer" });
  check("mark_achievements_seen records the card", !look1.error, look1.error?.message);
  // A first soul after the look: new on the explorer, while everything held before stays old.
  const { error: soulErr } = await a.from("souls").insert({ user_id: alice.id, name: `Mara ${runId}` });
  if (soulErr) throw new Error(`soul: ${soulErr.message}`);
  await a.rpc("check_achievements");
  const afterSoul = await read(a);
  const soul = afterSoul.explorer.first_five.find((s) => s.slug === "first-soul");
  check("an explorer item unlocked after the look is new, and those unlocked before it are not (D10)",
    soul?.done === true && soul.new === true
      && afterSoul.explorer.first_five.filter((s) => s.slug !== "first-soul").every((s) => s.new === false)
      && afterSoul.explorer.six_firsts.every((s) => s.done && s.new === false),
    JSON.stringify(afterSoul.explorer.first_five) + " " + JSON.stringify(afterSoul.explorer.six_firsts.map((s) => s.new)));
  await a.rpc("mark_achievements_seen", { p_card: "explorer" });
  const afterLook = await read(a);
  check("after the next look nothing on the explorer is new, and seen.explorer is whole-second UTC",
    afterLook.explorer.first_five.every((s) => s.new === false) && afterLook.explorer.six_firsts.every((s) => s.new === false)
      && WHOLE_SECOND.test(afterLook.seen.explorer ?? ""),
    JSON.stringify([afterLook.explorer.first_five.map((s) => s.new), afterLook.seen]));
  // The emotions card keeps its own look: a feeling met after it is new, the earlier one is not.
  await a.rpc("mark_achievements_seen", { p_card: "emotions" });
  const { data: other } = await a.from("emotions").select("id").neq("id", emo.id).order("slug").limit(1).single();
  if (!other) throw new Error("a second emotion is missing");
  await pebble(a, `a new feeling ${runId}`, "2026-03-06T09:00:00Z", other.id, 2, 1, [dom.id]);
  await a.rpc("check_achievements");
  const felt = await read(a);
  const newSeats = felt.emotions.seats.filter((s) => s.new).map((s) => s.emotion_id);
  check("an emotion met after the emotions card was seen is new, the one met before is not",
    newSeats.length === 1 && newSeats[0] === other.id && felt.emotions.seats.find((s) => s.emotion_id === emo.id)?.new === false,
    JSON.stringify(newSeats));

  const { data: sets } = await a.from("achievements").select("id, slug, is_active").eq("family", "set_complete").order("slug");
  // Not asserted: complete-six-firsts' is_active, the maintainer's switch in the admin;
  // this must hold either way.
  const heldNow = (await unlocks(a)).map((u) => u.slug);
  check("the check never unlocks a set_complete row (only pull_ceremony does)",
    (sets ?? []).length === 3 && !heldNow.some((s) => s.startsWith("complete-")), JSON.stringify([sets, heldNow]));
  check("complete-emotions and complete-domains are inactive while their ceremony is deferred (#1458)",
    ["complete-emotions", "complete-domains"].every((slug) =>
      (sets ?? []).find((r: { slug: string }) => r.slug === slug)?.is_active === false),
    JSON.stringify(sets));

  const badStack = await a.rpc("pull_ceremony", { p_stack: "nope", p_tz: tz });
  check("pull_ceremony refuses an unknown stack", refused(badStack, "invalid_stack"), badStack.error?.message ?? "accepted");
  const badZone = await a.rpc("pull_ceremony", { p_stack: "six_firsts", p_tz: "Mars/Olympus" });
  const nullZone = await a.rpc("pull_ceremony", { p_stack: "six_firsts", p_tz: null });
  check("pull_ceremony refuses an unknown and a null zone", refused(badZone, "invalid_tz") && refused(nullZone, "invalid_tz"),
    [badZone.error?.message, nullZone.error?.message].join(" | "));
  // Completed seconds ago, and the offer is never less than 5 hours after completion.
  const zones = [tz, "Pacific/Kiritimati"];
  const early = await Promise.all(zones.map((z) => a.rpc("pull_ceremony", { p_stack: "six_firsts", p_tz: z })));
  const readyIn = await Promise.all(zones.map(async (z) =>
    ((await a.rpc("get_achievements", { p_tz: z })).data as Achievements | null)?.explorer.completion?.ready));
  check("pull_ceremony raises not_ready before 06:00 the next local day, in two zones, as the payload's ready says",
    early.every((r) => refused(r, "not_ready")) && readyIn.every((r) => r === false),
    JSON.stringify([early.map((r) => r.error?.message ?? r.data), readyIn]));
  const none = await a.rpc("pull_ceremony", { p_stack: "domains", p_tz: tz });
  check("pull_ceremony raises not_ready for a stack never completed", refused(none, "not_ready"),
    none.error?.message ?? JSON.stringify(none.data));
  // Carol made every domain whole above: recorded, so it can be offered once the
  // ceremony comes back, but deferred past V1 (#1458).
  const { data: carolDone } = await carol.client.from("achievement_completions").select("stack");
  const carolDomains = (await read(carol.client)).domains;
  const carolPull = await carol.client.rpc("pull_ceremony", { p_stack: "domains", p_tz: tz });
  check("a domains set made whole is recorded, but its card carries no completion and its pull says not_ready (#1458)",
    (carolDone ?? []).some((c: { stack: string }) => c.stack === "domains") && carolDomains.completion === undefined
      && refused(carolPull, "not_ready"),
    JSON.stringify([carolDone, carolDomains.completion, carolPull.error?.message ?? carolPull.data]));
  const anonPull = await anon.rpc("pull_ceremony", { p_stack: "six_firsts", p_tz: tz });
  const anonSeen = await anon.rpc("mark_achievements_seen", { p_card: "explorer" });
  check("pull_ceremony and mark_achievements_seen are refused to anon", !!anonPull.error && !!anonSeen.error,
    JSON.stringify([anonPull.data, anonSeen.data]));
  await dump("get-achievements-seen", alice.token, "get_achievements", { p_tz: tz });

  // The ready path needs a completion older than its offer, which only the
  // service role can produce: it runs when the key is present (by hand), never in CI.
  const SERVICE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (SERVICE) {
    const admin = createClient(SUPABASE_URL!, SERVICE, { auth: { persistSession: false } });
    const { error: backErr } = await admin.from("achievement_completions").update({ completed_at: "2026-01-01T00:00:00Z" })
      .eq("user_id", alice.id).eq("stack", "six_firsts");
    if (backErr) throw new Error(`back-date: ${backErr.message}`);
    const backDated = (await read(a)).explorer.completion;
    check("a completion past its offer is ready", backDated?.ready === true, JSON.stringify(backDated));
    const pulls = await Promise.all([1, 2].map(() => a.rpc("pull_ceremony", { p_stack: "six_firsts", p_tz: tz })));
    const pulled = pulls.flatMap((r) => (r.data ?? []) as Pull[]);
    check("two concurrent pulls of a ready ceremony return one row between them",
      pulls.every((r) => !r.error) && pulled.length === 1,
      [...pulls.map((r) => r.error?.message), JSON.stringify(pulled)].filter(Boolean).join(" | "));
    const again = await a.rpc("pull_ceremony", { p_stack: "six_firsts", p_tz: tz });
    check("a third pull returns no row", !again.error && ((again.data ?? []) as unknown[]).length === 0,
      again.error?.message ?? JSON.stringify(again.data));
    const six: { id: string; is_active: boolean } | undefined =
      (sets ?? []).find((r: { slug: string }) => r.slug === "complete-six-firsts");
    const { data: paid } = await a.from("karma_events").select("delta").eq("reason", "grant").eq("ref_id", six?.id ?? "");
    const granted = pulled[0]?.karma_granted ?? -1;
    const sixHeld = (await unlocks(a)).some((u) => u.slug === "complete-six-firsts");
    if (six?.is_active) {
      check("an active complete-six-firsts is unlocked by the pull, its karma paid at most once and as returned",
        sixHeld && granted >= 0 && (paid ?? []).length === (granted > 0 ? 1 : 0)
          && (paid ?? []).every((k: { delta: number }) => k.delta === granted),
        JSON.stringify([granted, paid, sixHeld]));
    } else {
      check("an inactive complete-six-firsts: the pull returns its row with 0 karma, unlocks and pays nothing",
        granted === 0 && (paid ?? []).length === 0 && !sixHeld, JSON.stringify([granted, paid, sixHeld]));
    }
    const after = await read(a);
    check("the pulled completion carries pulled_at at whole seconds and is no longer ready",
      WHOLE_SECOND.test(after.explorer.completion?.pulled_at ?? "") && after.explorer.completion?.ready === false,
      JSON.stringify(after.explorer.completion));
    check("a set_complete row never reaches the shelf", after.shelf.every((s) => s.family !== "set_complete"),
      JSON.stringify(after.shelf));
    // #1458: Carol's domains completion back-dated, and an emotions one written
    // beside it, so both are past their offer and only the deferral holds them.
    const { error: domErr } = await admin.from("achievement_completions").update({ completed_at: "2026-01-01T00:00:00Z" })
      .eq("user_id", carol.id).eq("stack", "domains");
    const { error: emoErr } = await admin.from("achievement_completions")
      .insert({ user_id: carol.id, stack: "emotions", completed_at: "2026-01-01T00:00:00Z" });
    if (domErr || emoErr) throw new Error(`deferred completions: ${(domErr ?? emoErr)?.message}`);
    const deferredPulls = await Promise.all(["emotions", "domains"].map((stack) =>
      carol.client.rpc("pull_ceremony", { p_stack: stack, p_tz: tz })));
    const deferredCards = await read(carol.client);
    const carolHeld = (await unlocks(carol.client)).map((u) => u.slug);
    const { data: carolOpen } = await carol.client.from("achievement_completions").select("stack, pulled_at");
    check("past their offer, the emotions and domains completions stay off their cards, refuse the pull and stay unpulled (#1458)",
      deferredPulls.every((r) => refused(r, "not_ready"))
        && deferredCards.emotions.completion === undefined && deferredCards.domains.completion === undefined
        && !carolHeld.some((slug) => slug.startsWith("complete-"))
        && ["emotions", "domains"].every((stack) => (carolOpen ?? [])
          .some((c: { stack: string; pulled_at: string | null }) => c.stack === stack && c.pulled_at === null)),
      JSON.stringify([deferredPulls.map((r) => r.error?.message ?? r.data), deferredCards.emotions.completion,
        deferredCards.domains.completion, carolHeld, carolOpen]));
    // Run by hand with the key and ACHIEVEMENTS_DUMP=1 to refresh this one: the clients need a pulled completion.
    await dump("get-achievements-pulled", alice.token, "get_achievements", { p_tz: tz });
  } else {
    console.log("· pull_ceremony's ready path skipped (no SUPABASE_SERVICE_ROLE_KEY)");
  }
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
