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
 *   3. path_week_wrap on a reader seeded with the round-2 fixture's weeks 40–42
 *      (Europe/Paris times, real souls, domains, cards and photo rows) and five
 *      pebbles in the live week: the tiles each threshold allows, persons in
 *      order with their days, families with worth and share and the lead, the
 *      domains, the weight grid, hours, kept, the invitation for a week with
 *      nobody named, no wrap for one pebble, no stats for the live week, and
 *      the invalid_week_start and anon refusals. Expected values are worked
 *      out by hand from the fixture, never recomputed here.
 *
 * Part 4 of #1207 adds path_month_wrap here.
 *
 * Run: set -a; . ./.env; set +a; npm run db:verify:path-wraps --workspace=packages/supabase
 * Expected: `Summary: passed=35 failed=0` (gem: 10 fixed months, 1 random batch, 1 empty-input shape, 1 anon refusal;
 *   strip: 10; week: 12).
 * Needs NO service-role key: it signs up three throwaway users (path-wraps-verify-*@example.test)
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

// ---------------------------------------------------------------- the week
type FixturePebble = Pebble & { day: string; date: number; time: string; title: string; souls: string[]; domains: string[]; photo: boolean; cards: number };
type WeekWrap = {
  week_start: string; live: boolean; count: number; people_count: number; tiles: string[];
  persons?: { soul_id: string; count: number; days: number[] }[];
  invite_moment_ids?: string[];
  families?: { slug: string; count: number; worth: number; share: number; days: number[] }[];
  lead?: { order: string[]; zero: string[] };
  domains?: { domain_id: string; count: number; days: number[] }[];
  weight?: Record<"high" | "neutral" | "low", number[]>;
  hours?: { moment_id: string; happened: number; collected: number }[];
  kept?: { photo_moment_ids: string[]; cards: { moment_id: string; count: number }[] };
};
const POSITIVENESS: Record<string, number> = { high: 1, neutral: 0, low: -1 };

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
let weekly: TestUser | null = null;

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

  // 3. path_week_wrap -----------------------------------------------------------------------
  weekly = await signUp("c");
  const c = weekly.client;
  const { data: emotionRows } = await c.from("v_emotions_with_palette").select("id, category_slug");
  const emotionFor: Record<string, string> = {};
  for (const r of (emotionRows ?? []) as { id: string; category_slug: string }[]) emotionFor[r.category_slug] ??= r.id;
  const { data: domainRows } = await c.from("domains").select("id, name").order("name");
  const { data: cardType } = await c.from("card_types").select("id").limit(1).single();
  if (!cardType || !domainRows?.length || GEM.ORDER.some((k) => !emotionFor[k])) {
    throw new Error("reference data missing (an emotion per family, domains, a card type)");
  }
  // each fixture domain name maps to the real domain of that name, else to an unused one, so counts survive
  const fixtureDomains = [...new Set([OCT, FEW, ONE_SOUL].flat().flatMap((p) => (p as FixturePebble).domains))];
  const domainFor: Record<string, string> = {};
  const unused = (domainRows as { id: string; name: string }[]).filter((d) => !fixtureDomains.some((n) => n.toLowerCase() === d.name.toLowerCase()));
  for (const name of fixtureDomains) {
    domainFor[name] = (domainRows as { id: string; name: string }[]).find((d) => d.name.toLowerCase() === name.toLowerCase())?.id
      ?? unused.shift()?.id ?? "";
    if (!domainFor[name]) throw new Error(`not enough domains to map ${name}`);
  }
  const soulFor: Record<string, string> = {};
  let n = 0;
  async function seed(p: FixturePebble, at: string): Promise<string> {
    n += 1;
    const saved = await c.rpc("save_moment", {
      payload: { name: p.title, happened_at: at, ...(p.photo ? { snaps: [{ storage_path: `${weekly!.id}/verify-${runId}-${n}.jpg` }] } : {}) },
    });
    if (saved.error || typeof saved.data !== "string") throw new Error(`save_moment ${p.title}: ${saved.error?.message}`);
    const known = p.souls.filter((k) => soulFor[k]);
    const fresh = p.souls.filter((k) => !soulFor[k]);
    const done = await c.rpc("complete_pebble", {
      payload: {
        moment_id: saved.data, emotion_id: emotionFor[p.emotion], intensity: p.intensity,
        positiveness: POSITIVENESS[p.valence], domain_ids: p.domains.map((d) => domainFor[d]),
        soul_ids: known.map((k) => soulFor[k]), new_souls: fresh.map((k) => ({ name: FIXTURE.souls[k].name })),
        cards: Array.from({ length: p.cards }, (_, i) => ({ species_id: cardType!.id, value: `card ${i + 1}`, sort_order: i })),
      },
    });
    if (done.error) throw new Error(`complete_pebble ${p.title}: ${done.error.message}`);
    if (fresh.length) {
      const { data: souls } = await c.from("souls").select("id, name");
      for (const k of fresh) soulFor[k] = (souls as { id: string; name: string }[]).find((s) => s.name === FIXTURE.souls[k].name)!.id;
    }
    return saved.data;
  }
  const parisAt = (p: FixturePebble) => `2026-10-${String(p.date).padStart(2, "0")}T${p.time}:00+02:00`;
  const momentOf: Record<string, string> = {};
  for (const st of [FIXTURE.states.one, FIXTURE.states.rich, FIXTURE.states.few]) {
    for (const p of st.pebbles as FixturePebble[]) momentOf[p.title] = await seed(p, parisAt(p));
  }
  const liveFamilies = ["joy", "joy", "peace", "pride", "peace"];
  for (const [i, family] of liveFamilies.entries()) {
    const at = new Date(Date.now() - (liveFamilies.length - i) * 1000).toISOString();
    await seed({ emotion: family, intensity: 2, valence: "high", day: "", date: 0, time: "", title: `live ${i}`,
      souls: i === 0 ? ["mara"] : [], domains: ["Family"], photo: false, cards: 0 }, at);
  }

  const wk = async (start: string) => {
    const r = await c.rpc("path_week_wrap", { p_week_start: start, p_tz: "Europe/Paris" });
    if (r.error) throw new Error(`path_week_wrap ${start}: ${r.error.message}`);
    return r.data as WeekWrap;
  };
  const rich = await wk("2026-10-05");
  check("path_week_wrap: a full past week shows every tile, in order",
    rich.count === 11 && rich.people_count === 5 && rich.live === false
      && JSON.stringify(rich.tiles) === JSON.stringify(["contents", "persons", "emotions", "domains", "weight", "hours", "kept"]),
    JSON.stringify({ count: rich.count, people: rich.people_count, tiles: rich.tiles }));
  const personNames = (rich.persons ?? []).map((p) => Object.keys(soulFor).find((k) => soulFor[k] === p.soul_id));
  check("path_week_wrap: persons ranked by pebbles, then first appearance, with their days",
    JSON.stringify(personNames) === JSON.stringify(["mara", "nadia", "elio", "bruno", "jules"])
      && JSON.stringify((rich.persons ?? []).map((p) => p.count)) === JSON.stringify([4, 2, 2, 2, 2])
      && JSON.stringify(rich.persons?.[0].days) === JSON.stringify([0, 1, 3, 5]),
    JSON.stringify(rich.persons));
  check("path_week_wrap: families by worth (even pebbles half), shares and the lead",
    JSON.stringify((rich.families ?? []).map((f) => `${f.slug}:${f.worth}`))
        === JSON.stringify(["joy:7", "peace:4", "pride:4", "anger:2", "sadness:2", "fear:1"])
      && Math.abs((rich.families?.[0].share ?? 0) - 0.35) < 1e-9
      && JSON.stringify(rich.lead?.order) === JSON.stringify(["joy", "peace", "pride", "anger", "sadness", "fear"])
      && JSON.stringify(rich.lead?.zero) === JSON.stringify(["shame"]),
    JSON.stringify({ families: rich.families, lead: rich.lead }));
  check("path_week_wrap: domains ranked, family first with its days",
    rich.domains?.length === 9 && rich.domains[0].domain_id === domainFor["Family"] && rich.domains[0].count === 4
      && JSON.stringify(rich.domains[0].days) === JSON.stringify([0, 1, 6]),
    JSON.stringify(rich.domains?.slice(0, 2)));
  check("path_week_wrap: the weight grid by valence and intensity",
    JSON.stringify([rich.weight?.high, rich.weight?.neutral, rich.weight?.low]) === JSON.stringify([[0, 2, 3], [2, 1, 0], [1, 2, 0]]),
    JSON.stringify(rich.weight));
  check("path_week_wrap: hours list every pebble in time order, in local decimal hours",
    rich.hours?.length === 11 && rich.hours[0].moment_id === momentOf["Flag march with Nadia"] && rich.hours[0].happened === 10.25
      && rich.hours.every((h, i) => h.collected >= 0 && h.collected < 24 && (i === 0 || h.happened >= 0)),
    JSON.stringify(rich.hours?.[0]));
  check("path_week_wrap: kept lists the photos and the cards per moment, in time order",
    rich.kept?.photo_moment_ids.length === 5
      && JSON.stringify(rich.kept.cards.map((x) => x.count)) === JSON.stringify([2, 1, 1, 3, 1, 2, 1]),
    JSON.stringify(rich.kept));
  const few = await wk("2026-10-12");
  check("path_week_wrap: three pebbles and nobody named give contents, persons and the invitation's moments",
    JSON.stringify(few.tiles) === JSON.stringify(["contents", "persons"]) && JSON.stringify(few.persons) === "[]"
      && JSON.stringify(few.invite_moment_ids) === JSON.stringify((FIXTURE.states.few.pebbles as FixturePebble[]).map((p) => momentOf[p.title]))
      && few.families === undefined,
    JSON.stringify(few));
  const one = await wk("2026-09-28");
  check("path_week_wrap: one pebble gets no wrap",
    one.count === 1 && JSON.stringify(one.tiles) === "[]" && one.persons === undefined, JSON.stringify(one));
  const live = await wk(liveWeek("Europe/Paris"));
  check("path_week_wrap: the live week shows contents and persons only, even with five pebbles in two families",
    live.live === true && live.count === 5 && JSON.stringify(live.tiles) === JSON.stringify(["contents", "persons"])
      && live.persons?.length === 1 && live.families === undefined && live.invite_moment_ids === undefined,
    JSON.stringify(live));
  const tuesday = await c.rpc("path_week_wrap", { p_week_start: "2026-10-06", p_tz: "Europe/Paris" });
  check("path_week_wrap: a week start that is not a Monday is refused",
    !!tuesday.error && tuesday.error.message.includes("invalid_week_start"), tuesday.error?.message);
  const anonWeek = await anon.rpc("path_week_wrap", { p_week_start: "2026-10-05", p_tz: "Europe/Paris" });
  check("path_week_wrap is refused to anon", !!anonWeek.error, JSON.stringify(anonWeek.data));
} catch (err) {
  failed += 1;
  console.error(`✗ aborted: ${err instanceof Error ? err.message : String(err)}`);
} finally {
  for (const [user, label] of [[reader, "reader"], [seeded, "seeded"], [weekly, "weekly"]] as const) {
    if (!user) continue;
    const res = await deleteAccount(user.token).catch(() => null);
    console.log(`… cleanup ${label}: ${res ? res.status : "FAILED — remove path-wraps-verify-* manually"}`);
  }
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed > 0 ? 1 : 0);
