#!/usr/bin/env -S deno run --allow-env --allow-net
/**
 * Backfill script — composes renders for every pebble with render_svg IS NULL.
 *
 * Run:
 *   SUPABASE_URL=... SUPABASE_SERVICE_ROLE_KEY=... \
 *     deno run --allow-env --allow-net packages/supabase/scripts/backfill-renders.ts
 *
 * Sequential. Idempotent (only touches null rows). Logs ✓/✗ per pebble and
 * a final summary. Exits 0 even on partial failures — re-run to retry.
 */

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

if (!SUPABASE_URL || !SERVICE_ROLE) {
  console.error("SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY must be set");
  Deno.exit(2);
}

const admin = createClient(SUPABASE_URL, SERVICE_ROLE, {
  auth: { persistSession: false },
});

const { data: rows, error } = await admin
  .from("pebbles")
  .select("id")
  .is("render_svg", null)
  .order("created_at", { ascending: true });

if (error) {
  console.error("Query failed:", error);
  Deno.exit(1);
}

const ids = (rows ?? []).map((r) => r.id as string);
console.log(`Found ${ids.length} pebble(s) with render_svg=null`);

let rendered = 0;
let failed = 0;

for (const id of ids) {
  const res = await fetch(`${SUPABASE_URL}/functions/v1/backfill-pebble-render`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${SERVICE_ROLE}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ pebble_id: id }),
  });

  if (res.ok) {
    rendered += 1;
    console.log(`✓ ${id}`);
  } else {
    failed += 1;
    const text = await res.text();
    console.log(`✗ ${id} [${res.status}] ${text}`);
  }
}

console.log(`\nSummary: rendered=${rendered} failed=${failed} total=${ids.length}`);
Deno.exit(failed > 0 && rendered === 0 ? 1 : 0);
