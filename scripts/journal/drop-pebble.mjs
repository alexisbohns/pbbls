#!/usr/bin/env node
// Drop one build-journal pebble on the maintainer's journal account (their public blog), a NON-admin account.
// The script refuses to run as an admin: its credentials sit on disk for every agent session.
// See the "Build journal" rule in the root CLAUDE.md for when and how agents call it.
//
//   node scripts/journal/drop-pebble.mjs \
//     --title "Quick actions as a split stone" \
//     --journal "First-person, 1–3 sentences, plain words, no PR numbers." \
//     --emotion proud --valence highlight [--size small] \
//     [--domain work] [--collection iOS] [--image path/to/screenshot.png] [--dry-run]
//
// Auth: `.env.journal` at the MAIN checkout root (gitignored by `.env*`, shared by every worktree) holds
// NEXT_PUBLIC_SUPABASE_URL, NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY, JOURNAL_EMAIL and JOURNAL_PASSWORD
// of the journal account. Each run signs in with them and signs out locally at the end.
// Pictures go through macOS `sips` (original ≤1024 px, thumb ≤420 px, JPEG), like the apps' pipeline.
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { mkdtempSync, readFileSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { parseArgs } from "node:util";
import { createRequire } from "node:module";

const SOUL_NAME = "Alexis";
const VISIBILITY = "public";
const SIZES = { small: 1, medium: 2, large: 3 };
const VALENCES = { lowlight: -1, neutral: 0, highlight: 1 };

const { values: a } = parseArgs({
  options: {
    title: { type: "string" }, journal: { type: "string" }, emotion: { type: "string" },
    valence: { type: "string" }, size: { type: "string", default: "small" },
    domain: { type: "string", multiple: true }, collection: { type: "string", multiple: true },
    image: { type: "string" }, "dry-run": { type: "boolean" },
  },
});

const fail = (msg) => { console.error(`drop-pebble: ${msg}`); process.exit(1); };
const must = (r, what) => { if (r.error) fail(`${what}: ${r.error.message}`); return r.data; };

// The env file lives in the main checkout so every worktree shares one set of credentials.
const commonDir = execFileSync("git", ["rev-parse", "--path-format=absolute", "--git-common-dir"], { encoding: "utf8" }).trim();
const MAIN_ROOT = dirname(commonDir);
const ENV_FILE = join(MAIN_ROOT, ".env.journal");
// Worktrees carry no node_modules: resolve supabase-js from the main checkout.
const { createClient } = createRequire(join(MAIN_ROOT, "package.json"))("@supabase/supabase-js");
if (!existsSync(ENV_FILE)) fail(`missing ${ENV_FILE} (see the header of this script)`);
const env = Object.fromEntries(readFileSync(ENV_FILE, "utf8").split("\n").filter((l) => /^[A-Z0-9_]+=/.test(l))
  .map((l) => [l.slice(0, l.indexOf("=")), l.slice(l.indexOf("=") + 1).trim().replace(/^["']|["']$/g, "")]));

if (!env.JOURNAL_EMAIL || !env.JOURNAL_PASSWORD) fail(`JOURNAL_EMAIL and JOURNAL_PASSWORD must be set in ${ENV_FILE}`);
const sb = createClient(env.NEXT_PUBLIC_SUPABASE_URL, env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY, {
  auth: { persistSession: false, autoRefreshToken: false },
});
const { session } = must(await sb.auth.signInWithPassword({ email: env.JOURNAL_EMAIL, password: env.JOURNAL_PASSWORD }), "sign-in");
const uid = session.user.id;
// The journal account must be a plain user: never keep admin credentials on disk.
if (must(await sb.rpc("is_admin", { p_user_id: uid }), "admin check")) {
  await sb.auth.signOut({ scope: "local" });
  fail(`${session.user.email} is an admin account; put a non-admin journal account in ${ENV_FILE}`);
}

for (const k of ["title", "journal", "emotion", "valence"]) if (!a[k]) fail(`--${k} is required`);
if (!(a.valence in VALENCES)) fail(`--valence must be one of ${Object.keys(VALENCES).join(", ")}`);
if (!(a.size in SIZES)) fail(`--size must be one of ${Object.keys(SIZES).join(", ")}`);

const emotions = Object.fromEntries(must(await sb.from("emotions").select("id, slug"), "emotions").map((e) => [e.slug, e.id]));
const domains = Object.fromEntries(must(await sb.from("domains").select("id, slug"), "domains").map((d) => [d.slug, d.id]));
const cols = must(await sb.from("collections").select("id, name").eq("user_id", uid), "collections");
const soul = must(await sb.from("souls").select("id, name").eq("user_id", uid), "souls")
  .find((s) => s.name.trim().toLowerCase() === SOUL_NAME.toLowerCase());

if (!emotions[a.emotion]) fail(`unknown emotion "${a.emotion}"; one of: ${Object.keys(emotions).sort().join(" ")}`);
const domainSlugs = a.domain?.length ? a.domain : ["work"];
const badDomain = domainSlugs.find((d) => !domains[d]);
if (badDomain) fail(`unknown domain "${badDomain}"; one of: ${Object.keys(domains).sort().join(" ")}`);
const collectionIds = (a.collection ?? []).map((n) => {
  const c = cols.find((x) => x.name.toLowerCase() === n.toLowerCase());
  if (!c) fail(`no collection "${n}" on the account; one of: ${cols.map((x) => x.name).join(", ")}`);
  return c.id;
});
if (!soul) fail(`no soul named "${SOUL_NAME}" on the account`);

const happenedAt = new Date(Math.floor(Date.now() / 1000) * 1000).toISOString().replace(".000Z", "Z");
const plan = { name: a.title, description: a.journal, happened_at: happenedAt, emotion: a.emotion, size: a.size,
  valence: a.valence, domains: domainSlugs, collections: a.collection ?? [], image: a.image ?? null };
if (a["dry-run"]) {
  console.log(`drop-pebble: dry run as ${session.user.email}, nothing written\n` + JSON.stringify(plan, null, 2));
  await sb.auth.signOut({ scope: "local" });
  process.exit(0);
}

const snaps = [];
if (a.image) {
  if (!existsSync(a.image)) fail(`no image at ${a.image}`);
  const dir = mkdtempSync(join(tmpdir(), "drop-pebble-"));
  const snapId = randomUUID();
  for (const [kind, px, q] of [["original", 1024, 85], ["thumb", 420, 75]]) {
    const out = join(dir, `${kind}.jpg`);
    execFileSync("sips", ["-s", "format", "jpeg", "-s", "formatOptions", String(q), "-Z", String(px), a.image, "--out", out], { stdio: "ignore" });
    must(await sb.storage.from("pebbles-media").upload(`${uid}/${snapId}/${kind}.jpg`, readFileSync(out), { contentType: "image/jpeg" }), `upload ${kind}`);
  }
  snaps.push({ id: snapId, storage_path: `${uid}/${snapId}`, sort_order: 0 });
}

const momentId = must(await sb.rpc("save_moment", { payload: {
  name: a.title, description: a.journal, happened_at: happenedAt, ...(snaps.length ? { snaps } : {}),
} }), "save_moment");
const pebbleId = must(await sb.rpc("complete_pebble", { payload: {
  moment_id: momentId, emotion_id: emotions[a.emotion], intensity: SIZES[a.size], positiveness: VALENCES[a.valence],
  domain_ids: domainSlugs.map((d) => domains[d]), soul_ids: [soul.id], collection_ids: collectionIds, visibility: VISIBILITY,
} }), "complete_pebble");
console.log(`drop-pebble: dropped "${a.title}" (${a.size} ${a.valence}, ${a.emotion}) as pebble ${pebbleId}`);
await sb.auth.signOut({ scope: "local" });
