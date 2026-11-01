#!/usr/bin/env node
// Turn the committed Android screenshot references into the manifest the arkaik
// screenshot door takes (spec docs/superpowers/specs/2026-11-01-android-arkaik-screenshots-design.md).
//
//   node scripts/arkaik/android-shots-manifest.mjs --check
//   node scripts/arkaik/android-shots-manifest.mjs --sha <sha> --out shots.json
//
// The mapping (apps/android/arkaik-shots.json) goes from an Arkaik node id to a
// reference STEM: `<File>Kt/<Function>[_<variant>]`, the file name without the
// `_<8 hex>_<index>.png` the plugin appends. A stem must resolve to exactly one
// file: a renamed preview or a new variant fails `--check` on the pull request.
// The URL is the raw GitHub file at the sha — immutable, no upload, no bucket.
// This script never posts: the arkaik `record-screenshots` workflow does.
import { existsSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { parseArgs } from "node:util";
import { fileURLToPath } from "node:url";

export const REFERENCE_DIR = "apps/android/app/src/screenshotTestDebug/reference/app/pbbls/android";
export const MAPPING_FILE = "apps/android/arkaik-shots.json";
const RAW_ORIGIN = "https://raw.githubusercontent.com/alexisbohns/pbbls";
const NODE_ID = /^(V|AC)-[A-Za-z0-9._-]+$/;
const SUFFIX = /_[0-9a-f]{8}_\d+\.png$/;

/** The files in `root` whose name is `<stem>_<8 hex>_<index>.png`, as `<dir>/<file>` paths. */
function matches(root, stem) {
  const slash = stem.lastIndexOf("/");
  if (slash === -1) return [];
  const dir = stem.slice(0, slash);
  const fn = stem.slice(slash + 1);
  const full = join(root, dir);
  if (!existsSync(full)) return [];
  return readdirSync(full)
    .filter((name) => name.startsWith(`${fn}_`) && SUFFIX.test(name) && name.slice(0, -name.match(SUFFIX)[0].length) === fn)
    .sort()
    .map((name) => `${dir}/${name}`);
}

/** The one reference path for a stem, or a thrown Error naming why there is not exactly one. */
export function resolveStem(root, stem) {
  const found = matches(root, stem);
  if (found.length === 1) return found[0];
  if (found.length === 0) throw new Error(`${stem}: no file matches (renamed or removed preview?)`);
  throw new Error(`${stem}: ${found.length} files match (${found.join(", ")}); a stem must name one render`);
}

/** Every mapping entry that is wrong, as `{ key, message }`; empty when the mapping is clean. */
export function checkMapping(root, mapping) {
  const failures = [];
  for (const [key, stem] of Object.entries(mapping)) {
    if (!NODE_ID.test(key)) failures.push({ key, message: `${key}: not a node id (expected V-… or AC-…)` });
    if (typeof stem !== "string" || stem === "") { failures.push({ key, message: `${key}: the stem must be a non-empty string` }); continue; }
    try { resolveStem(root, stem); } catch (e) { failures.push({ key, message: e.message }); }
  }
  return failures;
}

/** The door's entries, keys sorted so the output is byte-stable. Throws on the first unresolvable stem. */
export function buildManifest(root, mapping, sha, referenceDir) {
  return Object.keys(mapping).sort().map((node_id) => {
    let file;
    try { file = resolveStem(root, mapping[node_id]); } catch (e) { throw new Error(`${node_id} → ${e.message}`); }
    return { node_id, platform: "android", url: `${RAW_ORIGIN}/${sha}/${referenceDir}/${file}`, detail: mapping[node_id] };
  });
}

function main() {
  const { values } = parseArgs({ options: { check: { type: "boolean" }, sha: { type: "string" }, out: { type: "string" }, root: { type: "string", default: "." } } });
  const root = join(values.root, REFERENCE_DIR);
  const mapping = JSON.parse(readFileSync(join(values.root, MAPPING_FILE), "utf8"));
  if (values.check) {
    const failures = checkMapping(root, mapping);
    for (const f of failures) console.error(`arkaik-shots: ${f.message}`);
    if (failures.length > 0) process.exit(1);
    console.log(`arkaik-shots: ${Object.keys(mapping).length} mapping(s) resolve.`);
    return;
  }
  if (!values.sha || !/^[0-9a-f]{7,40}$/.test(values.sha)) { console.error("arkaik-shots: --sha <sha> is required (with --out <file>)"); process.exit(1); }
  if (!values.out) { console.error("arkaik-shots: --out <file> is required"); process.exit(1); }
  const manifest = buildManifest(root, mapping, values.sha, REFERENCE_DIR);
  writeFileSync(values.out, `${JSON.stringify(manifest, null, 2)}\n`);
  console.log(`arkaik-shots: wrote ${manifest.length} entr${manifest.length === 1 ? "y" : "ies"} to ${values.out}`);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) main();
