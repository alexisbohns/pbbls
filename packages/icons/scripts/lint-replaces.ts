/**
 * `npm run lint:replaces --workspace=@pbbls/icons`: fails when a migrated
 * surface still names a symbol a pebbles icon replaces. Fix the call site (or,
 * for a string that is not a symbol, rename the string); never the pattern.
 */
import { fileURLToPath } from "node:url"
import { loadManifest } from "./lib/manifest"
import { coverage, lintReplaces } from "./lib/replaces"

/** The repository root: every surface's tree is read from here. */
const REPO_ROOT = fileURLToPath(new URL("../../..", import.meta.url))
/** The package root: `icons.json`. */
const PACKAGE_ROOT = fileURLToPath(new URL("..", import.meta.url))

const manifest = loadManifest(PACKAGE_ROOT)
const hits = lintReplaces(REPO_ROOT, manifest)
for (const h of hits) console.error(`${h.path}:${h.line}  ${h.surface} ${h.name} → ${h.icon}  ${h.text}`)
const { rows, names } = coverage(manifest)
console.log(`replaces: ${rows} rows, ${names} names, ${hits.length} hits`)
process.exitCode = hits.length > 0 ? 1 : 0
