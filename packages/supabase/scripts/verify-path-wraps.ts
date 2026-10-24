#!/usr/bin/env -S deno run --allow-env --allow-net --allow-read
/**
 * Acceptance test for the Path wraps (#1207) — runs against the REMOTE project.
 *
 *   1. gem_allocate is the gem method, exactly: for fixed months (the round-2
 *      fixture: October, the thin, quiet, heavy and one-family months, an equal
 *      month, ties, one pebble, an empty month) and for seeded random months,
 *      the facets, the main family, the rotation and the shares returned by the
 *      SQL function equal those of the oracle, scripts/oracles/path-wrap/gem-method.js.
 *   1b. gem_allocate is refused to anon.
 *   2. path_strip: a reader with no moments gets only the live week; a seeded
 *      reader gets every week from the first moment to the live week, oldest
 *      first, with cairn day weights ([moments, strongest intensity]), empty
 *      past weeks as non-focusable spacers, and a month item after the week
 *      holding each completed month's last day. The months and the days move
 *      with the reader's zone (a moment at 23:30 UTC on 31 July is 1 August in
 *      Europe/Paris and 31 July in America/New_York). An unknown zone and anon
 *      are refused.
 *
 * Later parts of #1207 add path_week_wrap and path_month_wrap here.
 *
 * Run: set -a; . ./.env; set +a; npm run db:verify:path-wraps --workspace=packages/supabase
 * Expected: `Summary: passed=23 failed=0` (gem: 10 fixed months, 1 random batch, 1 empty-input shape, 1 anon refusal;
 *   strip: 10).
 * Needs NO service-role key: it signs up two throwaway users (path-wraps-verify-*@example.test)
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

// ---------------------------------------------------------------- the oracle
type Pebble = { emotion: string; intensity: number; valence: string; date?: number };
type Allocation = { main: string | null; rotation: number; shares: Record<string, number>; facets: Record<string, string | null> };
type Gem = {
  ORDER: string[];
  CYCLE: string[];
  worths(P: Pebble[]): Record<string, number>;
  allocate(P: Pebble[]): Allocation;
};

const ORACLE_DIR = new URL("./oracles/path-wrap/", import.meta.url);
// gem-method.js is a dependency-free UMD-style file written for the browser and node; evaluate it with a
// CommonJS shim so the oracle is the committed file itself, unmodified.
const oracleSource = await Deno.readTextFile(new URL("gem-method.js", ORACLE_DIR));
const shim: { exports: unknown } = { exports: {} };
new Function("module", oracleSource)(shim);
const GEM = shim.exports as Gem;
const FIXTURE = JSON.parse(await Deno.readTextFile(new URL("fixture-r2.json", ORACLE_DIR)));

const OCT: Pebble[] = FIXTURE.states.month_glimpse.pebbles;
const FEW: Pebble[] = FIXTURE.states.few.pebbles;
const ONE_SOUL: Pebble[] = FIXTURE.states.few_with_one_soul.pebbles;
const FIXED: [string, Pebble[]][] = [
  ["October (24 pebbles)", OCT],
  ["the thin month (7)", [...FEW, ...ONE_SOUL]],
  ["the quiet month (2)", FEW.filter((p) => p.date === 13 || p.date === 15)],
  ["the heavy month (October without bright)", OCT.filter((p) => p.valence !== "high")],
  ["the one-family month (October's peace)", OCT.filter((p) => p.emotion === "peace")],
  ["an equal month (seven families)", GEM.ORDER.map((k) => ({ emotion: k, intensity: 2, valence: "high" }))],
  ["one pebble", [OCT[7]]],
  ["a two-way tie (anger, sadness)", [
    { emotion: "anger", intensity: 2, valence: "low" },
    { emotion: "sadness", intensity: 2, valence: "low" },
  ]],
  ["October plus one small even shame", [...OCT, { emotion: "shame", intensity: 1, valence: "neutral" }]],
  ["an empty month", []],
];

/** What the server is given: worth and pebble count per present family, as path_month_wrap will pass it. */
function input(P: Pebble[]): Record<string, { worth: number; count: number }> {
  const w = GEM.worths(P);
  const out: Record<string, { worth: number; count: number }> = {};
  for (const k of GEM.ORDER) {
    const count = P.filter((p) => p.emotion === k).length;
    if (count) out[k] = { worth: w[k], count };
  }
  return out;
}

function compare(got: Allocation, expected: Allocation): string | null {
  const facets = GEM.CYCLE.filter((id) => (got.facets?.[id] ?? null) !== (expected.facets[id] ?? null));
  if (facets.length) return `facets differ: ${facets.join(", ")}`;
  if ((got.main ?? null) !== (expected.main ?? null)) return `main ${got.main} ≠ ${expected.main}`;
  if (expected.main && got.rotation !== expected.rotation) return `rotation ${got.rotation} ≠ ${expected.rotation}`;
  const share = GEM.ORDER.find((k) => Math.abs((got.shares?.[k] ?? NaN) - expected.shares[k]) > 1e-12);
  if (share) return `share of ${share}: ${got.shares?.[share]} ≠ ${expected.shares[share]}`;
  return null;
}

/** Seeded months (a 31-bit LCG), so a failure names a reproducible case. */
function randomMonths(count: number): Pebble[][] {
  let seed = 20261023;
  const r = () => (seed = (seed * 1103515245 + 12345) % 2147483648) / 2147483648;
  const valences = ["high", "neutral", "low"];
  return Array.from({ length: count }, () => {
    const n = 1 + Math.floor(r() * (r() < 0.3 ? 6 : 60));
    const families = GEM.ORDER.filter(() => r() < 0.6);
    const pool = families.length ? families : [GEM.ORDER[Math.floor(r() * 7)]];
    return Array.from({ length: n }, () => ({
      emotion: pool[Math.floor(r() * pool.length)],
      intensity: 1 + Math.floor(r() * 3),
      valence: valences[Math.floor(r() * 3)],
    }));
  });
}

// ---------------------------------------------------------------- the strip
type StripItem = {
  kind: "week" | "month";
  week_start?: string;
  iso_week?: number;
  count: number;
  focusable: boolean;
  live?: boolean;
  days?: [number, number][];
  month?: string;
};

/** The ISO Monday of today in a zone, as YYYY-MM-DD. */
function liveWeek(tz: string): string {
  const today = new Intl.DateTimeFormat("en-CA", { timeZone: tz }).format(new Date());
  const d = new Date(`${today}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() - ((d.getUTCDay() + 6) % 7));
  return d.toISOString().slice(0, 10);
}

/** The strip reader's moments: [instant, intensity or null for a moment with no pebble]. */
const STRIP_SEED: [string, number | null][] = [
  ["2026-07-08T08:00:00Z", 2],
  ["2026-07-31T23:30:00Z", 1], // Saturday 1 August in Paris, Friday 31 July in New York
  ["2026-08-12T08:00:00Z", 1],
  ["2026-08-12T15:00:00Z", 3],
  ["2026-08-14T09:00:00Z", null],
  ["2026-09-30T10:00:00Z", 1],
];

// ---------------------------------------------------------------- the run
const runId = crypto.randomUUID().slice(0, 8);
const password = `PathWraps-${crypto.randomUUID()}`;
type TestUser = { client: SupabaseClient; id: string; token: string };

async function signUp(label: string): Promise<TestUser> {
  const client = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
  const email = `path-wraps-verify-${label}-${runId}@example.test`;
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

let reader: TestUser | null = null;
let seeded: TestUser | null = null;

try {
  reader = await signUp("a");
  const a = reader.client;
  console.log(`reader=${reader.id}\n`);

  // 1. gem_allocate against the oracle ------------------------------------------------
  for (const [label, P] of FIXED) {
    const { data, error } = await a.rpc("gem_allocate", { p_families: input(P) });
    const diff = error ? error.message : compare(data as Allocation, GEM.allocate(P));
    check(`gem_allocate matches the oracle: ${label}`, diff === null, diff ?? undefined);
  }

  const months = randomMonths(400);
  const mismatches: string[] = [];
  for (let i = 0; i < months.length; i += 20) {
    const batch = months.slice(i, i + 20);
    const results = await Promise.all(batch.map((P) => a.rpc("gem_allocate", { p_families: input(P) })));
    results.forEach(({ data, error }, j) => {
      const diff = error ? error.message : compare(data as Allocation, GEM.allocate(batch[j]));
      if (diff) mismatches.push(`#${i + j}: ${diff}`);
    });
  }
  check(`gem_allocate matches the oracle on ${months.length} seeded random months`,
    mismatches.length === 0, mismatches.slice(0, 3).join(" | "));

  const { data: emptyShape } = await a.rpc("gem_allocate", { p_families: {} });
  const facets = (emptyShape as Allocation | null)?.facets ?? {};
  check("an empty month lists all 28 data facets, uncoloured, and no main family",
    !!emptyShape && (emptyShape as Allocation).main === null
      && GEM.CYCLE.every((id) => id in facets && facets[id] === null) && Object.keys(facets).length === 28,
    JSON.stringify(emptyShape));

  // 1b. not callable by anon -------------------------------------------------------------
  const anon = createClient(SUPABASE_URL!, ANON_KEY!, { auth: { persistSession: false } });
  const refused = await anon.rpc("gem_allocate", { p_families: input(OCT) });
  check("gem_allocate is refused to anon", !!refused.error, JSON.stringify(refused.data));

  // 2. path_strip ---------------------------------------------------------------------------
  const fresh = await a.rpc("path_strip", { p_tz: "Europe/Paris" });
  const freshItems = (fresh.data as { items: StripItem[] } | null)?.items ?? [];
  check("path_strip: a reader with no moments gets only the live week",
    !fresh.error && freshItems.length === 1 && freshItems[0].kind === "week" && freshItems[0].live === true
      && freshItems[0].focusable && freshItems[0].count === 0 && freshItems[0].week_start === liveWeek("Europe/Paris"),
    fresh.error?.message ?? JSON.stringify(freshItems));

  seeded = await signUp("b");
  const b = seeded.client;
  const { data: emotion } = await b.from("emotions").select("id").limit(1).single();
  const { data: domain } = await b.from("domains").select("id").limit(1).single();
  if (!emotion || !domain) throw new Error("reference data missing (emotion / domain)");
  for (const [at, intensity] of STRIP_SEED) {
    const saved = await b.rpc("save_moment", { payload: { name: `strip ${runId}`, happened_at: at } });
    if (saved.error || typeof saved.data !== "string") throw new Error(`save_moment ${at}: ${saved.error?.message}`);
    if (intensity === null) continue;
    const done = await b.rpc("complete_pebble", {
      payload: { moment_id: saved.data, emotion_id: emotion.id, intensity, positiveness: 1, domain_ids: [domain.id] },
    });
    if (done.error) throw new Error(`complete_pebble ${at}: ${done.error.message}`);
  }

  const paris = await b.rpc("path_strip", { p_tz: "Europe/Paris" });
  const items = (paris.data as { items: StripItem[] } | null)?.items ?? [];
  const weeks = items.filter((i) => i.kind === "week");
  const week = (start: string) => weeks.find((w) => w.week_start === start);
  check("path_strip: weeks run from the first moment's week, oldest first",
    !paris.error && weeks[0]?.week_start === "2026-07-06"
      && weeks.every((w, i) => i === 0 || w.week_start! > weeks[i - 1].week_start!),
    paris.error?.message ?? weeks.slice(0, 2).map((w) => w.week_start).join(", "));
  const last = items[items.length - 1];
  check("path_strip: the strip ends on the live week, focusable",
    last?.kind === "week" && last.week_start === liveWeek("Europe/Paris") && last.live === true && last.focusable,
    JSON.stringify(last));
  const aug10 = week("2026-08-10");
  check("path_strip: a week's cairn counts moments per day with the strongest intensity (0 without a pebble)",
    aug10?.iso_week === 33 && aug10.count === 3 && aug10.focusable
      && JSON.stringify(aug10.days) === JSON.stringify([[0, 0], [0, 0], [2, 3], [0, 0], [1, 0], [0, 0], [0, 0]]),
    JSON.stringify(aug10));
  const aug24 = week("2026-08-24");
  check("path_strip: an empty past week is a dated, non-focusable spacer",
    aug24?.count === 0 && aug24.focusable === false && aug24.live === false
      && aug24.days!.every(([n, top]) => n === 0 && top === 0),
    JSON.stringify(aug24));
  const monthPlacement = items.flatMap((item, i) => item.kind === "month" ? [`${item.month}:${item.count}@${items[i - 1]?.week_start}`] : []);
  check("path_strip: completed months follow the week holding their last day, counting pebbles (Paris)",
    JSON.stringify(monthPlacement) === JSON.stringify(["2026-07-01:1@2026-07-27", "2026-08-01:3@2026-08-31", "2026-09-01:1@2026-09-28"]),
    JSON.stringify(monthPlacement));
  check("path_strip: the Paris boundary moment marks Saturday 1 August",
    JSON.stringify(week("2026-07-27")?.days?.[5]) === JSON.stringify([1, 1]), JSON.stringify(week("2026-07-27")?.days));

  const ny = await b.rpc("path_strip", { p_tz: "America/New_York" });
  const nyItems = (ny.data as { items: StripItem[] } | null)?.items ?? [];
  const nyMonths = nyItems.filter((i) => i.kind === "month").map((i) => `${i.month}:${i.count}`);
  const nyJul27 = nyItems.find((i) => i.week_start === "2026-07-27");
  check("path_strip: in New York the same moment is Friday 31 July, so July holds two pebbles and August two",
    !ny.error && JSON.stringify(nyMonths) === JSON.stringify(["2026-07-01:2", "2026-08-01:2", "2026-09-01:1"])
      && JSON.stringify(nyJul27?.days?.[4]) === JSON.stringify([1, 1]),
    ny.error?.message ?? `${JSON.stringify(nyMonths)} ${JSON.stringify(nyJul27?.days)}`);

  const badZone = await b.rpc("path_strip", { p_tz: "Mars/Olympus" });
  check("path_strip: an unknown zone is refused as invalid_tz",
    !!badZone.error && badZone.error.message.includes("invalid_tz"), badZone.error?.message);
  const anonStrip = await anon.rpc("path_strip", { p_tz: "Europe/Paris" });
  check("path_strip is refused to anon", !!anonStrip.error, JSON.stringify(anonStrip.data));
} catch (err) {
  failed += 1;
  console.error(`✗ aborted: ${err instanceof Error ? err.message : String(err)}`);
} finally {
  for (const [user, label] of [[reader, "reader"], [seeded, "seeded"]] as const) {
    if (!user) continue;
    const res = await deleteAccount(user.token).catch(() => null);
    console.log(`… cleanup ${label}: ${res ? res.status : "FAILED — remove path-wraps-verify-* manually"}`);
  }
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed > 0 ? 1 : 0);
