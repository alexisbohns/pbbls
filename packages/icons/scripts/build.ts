/**
 * The icons CLI: `--lint` (sources and manifest), `--format` (rewrite `src/` to
 * the canonical form), `--check` (freshness, spec D8) and the default generate
 * (lint, then every registered emitter, then write the owned paths).
 * Run through the package scripts: `npm run lint --workspace=@pbbls/icons`.
 */
import { readdirSync, readFileSync, writeFileSync } from "node:fs"
import { join } from "node:path"
import { fileURLToPath } from "node:url"
import { loadIcons, unionOwned, type Emitter } from "./lib/emit"
import { androidEmitter } from "./lib/emit-android"
import { iosEmitter } from "./lib/emit-ios"
import { reactEmitter } from "./lib/emit-react"
import { formatSvg } from "./lib/format"
import { formatIssue, lint, SRC_DIR } from "./lib/lint"
import { check, write } from "./lib/write"

/** The repository root: emitters write into `apps/*` from here. */
export const REPO_ROOT = fileURLToPath(new URL("../../..", import.meta.url))
/** The package root: `icons.json`, `src/`, `waves/`. */
export const PACKAGE_ROOT = fileURLToPath(new URL("..", import.meta.url))

/** Every surface the generator emits, in the order they are written. */
const EMITTERS: readonly Emitter[] = [reactEmitter, iosEmitter, androidEmitter]

const FIX = "run npm run generate --workspace=@pbbls/icons and commit the result"

function runLint(): boolean {
  const { errors, warnings } = lint(PACKAGE_ROOT)
  for (const w of warnings) console.warn(`warning  ${formatIssue(w)}`)
  for (const e of errors) console.error(`error    ${formatIssue(e)}`)
  const files = readdirSync(join(PACKAGE_ROOT, SRC_DIR)).filter((f) => f.endsWith(".svg")).length
  console.log(`${files} sources, ${errors.length} errors, ${warnings.length} warnings`)
  return errors.length === 0
}

function runFormat(): void {
  const dir = join(PACKAGE_ROOT, SRC_DIR)
  let changed = 0
  const files = readdirSync(dir).filter((f) => f.endsWith(".svg")).sort()
  for (const f of files) {
    const path = join(dir, f)
    const before = readFileSync(path, "utf8")
    const after = formatSvg(before)
    if (after !== before) {
      writeFileSync(path, after)
      changed += 1
    }
  }
  console.log(`${files.length} sources, ${changed} rewritten`)
}

/** Lints, then runs every emitter into one map; null when the lint fails. */
function emitAll(): Map<string, string> | null {
  if (!runLint()) return null
  const icons = loadIcons(PACKAGE_ROOT)
  const files = new Map<string, string>()
  for (const emitter of EMITTERS) {
    for (const [path, contents] of emitter.emit(icons)) {
      if (files.has(path)) throw new Error(`${path}: emitted twice (${emitter.name})`)
      files.set(path, contents)
    }
  }
  console.log(`${icons.length} icons emitted by ${EMITTERS.map((e) => e.name).join(", ")}`)
  return files
}

function runGenerate(): number {
  const files = emitAll()
  if (!files) return 1
  const { written, removed } = write(REPO_ROOT, unionOwned(EMITTERS), files)
  console.log(`${written} files written, ${removed} orphaned files removed`)
  return 0
}

function runCheck(): number {
  const files = emitAll()
  if (!files) return 1
  const result = check(REPO_ROOT, unionOwned(EMITTERS), files)
  for (const path of result.missing) console.error(`missing   ${path}`)
  for (const path of result.stale) console.error(`stale     ${path}`)
  for (const path of result.orphaned) console.error(`orphaned  ${path}`)
  const problems = result.missing.length + result.stale.length + result.orphaned.length
  if (problems > 0) {
    console.error(`${result.missing.length} missing, ${result.stale.length} stale, ${result.orphaned.length} orphaned: ${FIX}`)
    return 1
  }
  console.log(`${files.size} generated files are fresh`)
  return 0
}

function main(argv: string[]): number {
  const mode = argv[0] ?? "generate"
  switch (mode) {
    case "--lint":
      return runLint() ? 0 : 1
    case "--format":
      runFormat()
      return 0
    case "--check":
      return runCheck()
    case "generate":
      return runGenerate()
    default:
      console.error(`unknown option ${mode}; use --lint, --format or --check`)
      return 2
  }
}

process.exitCode = main(process.argv.slice(2))
