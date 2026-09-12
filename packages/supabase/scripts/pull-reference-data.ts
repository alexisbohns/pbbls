#!/usr/bin/env -S deno run --allow-env --allow-net --allow-write
/**
 * Refreshes packages/supabase/reference/*.json from the linked project (#796).
 *
 * The companion to verify-reference-data.ts. That harness tells you the snapshot
 * has fallen behind after a Supabase Studio edit; this is how you catch it up:
 *
 *   set -a; . ./.env; set +a
 *   npm run db:reference:pull --workspace=packages/supabase
 *   git diff packages/supabase/reference/
 *
 * Read-only against the database. It writes only the two JSON files, sorted by
 * slug and with stable key order, so the diff shows the palette that changed and
 * nothing else.
 *
 * This does NOT update 20260912120000_seed_emotion_reference_data.sql, and must
 * not: that migration is already applied and will never re-run. If a Studio edit
 * should also reach fresh databases, add an update migration for it.
 */

const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY");

if (!SUPABASE_URL || !ANON_KEY) {
  console.error("SUPABASE_URL and SUPABASE_ANON_KEY must be set");
  Deno.exit(2);
}

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const db = createClient(SUPABASE_URL, ANON_KEY);

async function write(name: string, rows: unknown[]) {
  const url = new URL(`../reference/${name}`, import.meta.url);
  await Deno.writeTextFile(url, JSON.stringify(rows, null, 2) + "\n");
  console.log(`✓ ${name} — ${rows.length} rows`);
}

const { data: categories, error: catError } = await db
  .from("emotion_categories")
  .select("id, slug, name, primary_color, secondary_color, light_color, surface_color, shaded_color, dark_color")
  .order("slug");

if (catError) {
  console.error(`reading emotion_categories: ${catError.message}`);
  Deno.exit(1);
}

const { data: emotions, error: emoError } = await db
  .from("v_emotions_with_palette")
  .select("id, slug, name, color, emoji, category_slug")
  .order("slug");

if (emoError) {
  console.error(`reading v_emotions_with_palette: ${emoError.message}`);
  Deno.exit(1);
}

await write("emotion-categories.json", categories ?? []);
await write("emotions.json", emotions ?? []);
