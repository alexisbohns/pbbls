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
 *
 * Later parts of #1207 add path_strip, path_week_wrap and path_month_wrap here.
 *
 * Run: set -a; . ./.env; set +a; npm run db:verify:path-wraps --workspace=packages/supabase
 * Expected: `Summary: passed=13 failed=0` (10 fixed months, 1 random batch, 1 anon refusal, 1 empty-input shape).
 * Needs NO service-role key: it signs up one throwaway user (path-wraps-verify-*@example.test)
 * and deletes it through the real `delete-account` edge function, even on failure.
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
} catch (err) {
  failed += 1;
  console.error(`✗ aborted: ${err instanceof Error ? err.message : String(err)}`);
} finally {
  if (reader) {
    const res = await deleteAccount(reader.token).catch(() => null);
    console.log(`… cleanup reader: ${res ? res.status : "FAILED — remove path-wraps-verify-* manually"}`);
  }
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed > 0 ? 1 : 0);
