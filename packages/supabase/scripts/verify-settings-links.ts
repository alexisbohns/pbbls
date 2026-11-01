#!/usr/bin/env -S deno run --allow-env --allow-net
/**
 * Acceptance test for settings_links (#1489) — runs against the REMOTE project, anon only.
 *   1. anon reads the published rows, ordered by section then sort_order; the five seeded keys are there.
 *   2. anon cannot insert, update or delete (admin-only writes); each refusal is proven by re-reading.
 *   3. every published url starts with https:// or mailto:.
 * Run: set -a; . ./.env; set +a; npm run db:verify:settings-links --workspace=packages/supabase
 * Expected: `Summary: passed=6 failed=0`
 */
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY");
if (!SUPABASE_URL || !ANON_KEY) { console.error("SUPABASE_URL and SUPABASE_ANON_KEY must be set"); Deno.exit(2); }

let passed = 0; let failed = 0;
function check(name: string, ok: boolean, detail?: string) {
  if (ok) { passed += 1; console.log(`✓ ${name}`); } else { failed += 1; console.log(`✗ ${name}${detail ? ` — ${detail}` : ""}`); }
}

type Row = { key: string; section: string; sort_order: number; title_en: string; title_fr: string | null; url: string };
const SEEDED = ["terms", "privacy", "legal-notice", "credits", "support"];
const anon = createClient(SUPABASE_URL, ANON_KEY, { auth: { persistSession: false } });
const COLS = "key,section,sort_order,title_en,title_fr,url";

const read = await anon.from("settings_links").select(COLS).order("section").order("sort_order");
const rows = (read.data ?? []) as Row[];
check("anon reads the published links", !read.error && rows.length >= SEEDED.length, read.error?.message ?? `${rows.length} rows`);
check("the five seeded keys are published", SEEDED.every((k) => rows.some((r) => r.key === k)),
  SEEDED.filter((k) => !rows.some((r) => r.key === k)).join(", "));
check("every url is https or mailto", rows.every((r) => /^(https:\/\/|mailto:)/.test(r.url)),
  rows.filter((r) => !/^(https:\/\/|mailto:)/.test(r.url)).map((r) => r.key).join(", "));

const ins = await anon.from("settings_links").insert({ key: `verify-${crypto.randomUUID().slice(0, 8)}`, section: "about", title_en: "x", url: "https://example.test", published: true });
const after = await anon.from("settings_links").select("key").like("key", "verify-%");
check("anon insert is refused", ins.error !== null && (after.data ?? []).length === 0, ins.error?.message);

const before = rows.find((r) => r.key === "terms");
await anon.from("settings_links").update({ title_en: "hijacked" }).eq("key", "terms");
const terms = await anon.from("settings_links").select(COLS).eq("key", "terms").single();
check("anon update is refused", terms.data?.title_en === before?.title_en, terms.data?.title_en);

await anon.from("settings_links").delete().eq("key", "terms");
const still = await anon.from("settings_links").select("key").eq("key", "terms");
check("anon delete is refused", (still.data ?? []).length === 1);

console.log(`\nSummary: passed=${passed} failed=${failed}`);
Deno.exit(failed === 0 ? 0 : 1);
