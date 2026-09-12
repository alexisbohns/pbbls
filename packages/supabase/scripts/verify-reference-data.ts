#!/usr/bin/env -S deno run --allow-env --allow-net --allow-read
/**
 * Drift check for the emotion reference data (#796) — runs against the REMOTE
 * project.
 *
 * The lightest harness in the set, and the only read-only one: it signs nothing
 * up, writes nothing, and cleans nothing up. Every table it reads is
 * anon-selectable by policy, because every client reads it the same way.
 *
 * WHY IT EXISTS. `emotion_categories` and the 38-row `emotions` roster were
 * built by hand in Supabase Studio and never written back as migrations. The
 * `schema` CI job found the consequence — the migration chain could not be
 * replayed from an empty database — and #796 repaired it by committing the data
 * to packages/supabase/reference/*.json and seeding it from there.
 *
 * That repair creates a second copy, and a second copy can drift. Studio stays
 * the editing surface (it is the fast loop, and nothing here asks anyone to give
 * it up), so an edit there is expected to make the committed JSON stale. What is
 * NOT acceptable is for it to go unnoticed: a stale snapshot is a restore path
 * that silently restores the wrong colours, and a fresh database that disagrees
 * with production about what the emotion picker contains.
 *
 * So this harness does not defend production against the repo. It reports the
 * day the repo falls behind production.
 *
 * WHEN IT GOES RED, the fix is almost always to refresh the snapshot, not to
 * change the database:
 *
 *   npm run db:reference:pull --workspace=packages/supabase
 *
 * then commit the JSON. If the change should also reach fresh databases, add an
 * update migration — 20260912120000 is already applied and will not re-run.
 *
 * Run:
 *   SUPABASE_URL=... SUPABASE_ANON_KEY=... \
 *     deno run --allow-env --allow-net --allow-read \
 *       packages/supabase/scripts/verify-reference-data.ts
 */

const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY");

if (!SUPABASE_URL || !ANON_KEY) {
  console.error("SUPABASE_URL and SUPABASE_ANON_KEY must be set");
  Deno.exit(2);
}

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

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

type Category = {
  id: string;
  slug: string;
  name: string;
  primary_color: string;
  secondary_color: string;
  light_color: string;
  surface_color: string;
  shaded_color: string;
  dark_color: string;
};

type Emotion = {
  id: string;
  slug: string;
  name: string;
  color: string;
  emoji: string;
  category_slug: string;
};

/** Read relative to this file, so the harness runs from any working directory. */
async function readSnapshot<T>(name: string): Promise<T[]> {
  const url = new URL(`../reference/${name}`, import.meta.url);
  return JSON.parse(await Deno.readTextFile(url)) as T[];
}

/**
 * Compares two rows field by field and returns the fields that differ, so a red
 * run names the colour that moved rather than just the row.
 */
function diffFields<T extends Record<string, unknown>>(
  expected: T,
  actual: T,
  fields: readonly (keyof T)[],
): string[] {
  return fields
    .filter((f) => expected[f] !== actual[f])
    .map((f) => `${String(f)}: committed ${JSON.stringify(expected[f])} ≠ live ${JSON.stringify(actual[f])}`);
}

/**
 * One pass over a reference table: same row count, same slugs, same field values.
 * Missing and unexpected slugs are reported separately — "someone added an
 * emotion in Studio" and "someone deleted one" are different problems.
 */
function compare<T extends { slug: string }>(
  label: string,
  committed: T[],
  live: T[],
  fields: readonly (keyof T)[],
): void {
  check(
    `${label}: row count matches`,
    committed.length === live.length,
    `committed ${committed.length}, live ${live.length}`,
  );

  const liveBySlug = new Map(live.map((r) => [r.slug, r]));
  const committedSlugs = new Set(committed.map((r) => r.slug));

  const missing = committed.filter((r) => !liveBySlug.has(r.slug)).map((r) => r.slug);
  check(
    `${label}: every committed slug exists live`,
    missing.length === 0,
    missing.length ? `absent from the project: ${missing.join(", ")}` : undefined,
  );

  const extra = live.filter((r) => !committedSlugs.has(r.slug)).map((r) => r.slug);
  check(
    `${label}: no live slug is missing from the snapshot`,
    extra.length === 0,
    extra.length ? `live but uncommitted: ${extra.join(", ")}` : undefined,
  );

  for (const row of committed) {
    const liveRow = liveBySlug.get(row.slug);
    if (!liveRow) continue;
    const diffs = diffFields(row, liveRow, fields);
    check(`${label}: ${row.slug} matches`, diffs.length === 0, diffs.join("; "));
  }
}

const db = createClient(SUPABASE_URL, ANON_KEY);

try {
  const committedCategories = await readSnapshot<Category>("emotion-categories.json");
  const committedEmotions = await readSnapshot<Emotion>("emotions.json");

  // ---------------------------------------------------------------------------
  // 1. Categories, including the two palette slots added live in Studio ahead of
  //    20260717000000 (shaded_color, dark_color) — the exact pair the original
  //    migration declined to seed.
  // ---------------------------------------------------------------------------
  const { data: liveCategories, error: catError } = await db
    .from("emotion_categories")
    .select("id, slug, name, primary_color, secondary_color, light_color, surface_color, shaded_color, dark_color")
    .order("slug");

  if (catError) throw new Error(`reading emotion_categories: ${catError.message}`);

  compare("emotion_categories", committedCategories, liveCategories ?? [], [
    "id",
    "name",
    "primary_color",
    "secondary_color",
    "light_color",
    "surface_color",
    "shaded_color",
    "dark_color",
  ] as const);

  // ---------------------------------------------------------------------------
  // 2. Emotions, read through v_emotions_with_palette so the category is compared
  //    by slug. Comparing category_id would only prove the two snapshots agree
  //    about a UUID; the slug is what every client actually groups by.
  // ---------------------------------------------------------------------------
  const { data: liveEmotions, error: emoError } = await db
    .from("v_emotions_with_palette")
    .select("id, slug, name, color, emoji, category_slug")
    .order("slug");

  if (emoError) throw new Error(`reading v_emotions_with_palette: ${emoError.message}`);

  compare("emotions", committedEmotions, liveEmotions ?? [], [
    "id",
    "name",
    "color",
    "emoji",
    "category_slug",
  ] as const);

  // ---------------------------------------------------------------------------
  // 3. The invariant 20260506000001 asserts in DDL, checked as data. The view is
  //    an INNER JOIN, so an uncategorised emotion vanishes from it rather than
  //    appearing with a null — which is why this compares counts against the
  //    table rather than looking for nulls in the view.
  // ---------------------------------------------------------------------------
  const { count: emotionCount, error: countError } = await db
    .from("emotions")
    .select("id", { count: "exact", head: true });

  if (countError) throw new Error(`counting emotions: ${countError.message}`);

  check(
    "every live emotion has a category",
    emotionCount === (liveEmotions ?? []).length,
    `emotions table has ${emotionCount}, the palette view returns ${(liveEmotions ?? []).length}`,
  );
} catch (err) {
  failed += 1;
  console.error(`✗ aborted: ${err instanceof Error ? err.message : String(err)}`);
}

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed > 0 ? 1 : 0);
