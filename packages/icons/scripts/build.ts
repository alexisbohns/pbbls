/**
 * The icons CLI: `--lint` (sources and manifest), `--format` (rewrite `src/` to
 * the canonical form), `--check` (freshness, Part 2) and the default generate.
 * Run through the package scripts: `npm run lint --workspace=@pbbls/icons`.
 */
import { readdirSync, readFileSync, writeFileSync } from "node:fs"
import { join } from "node:path"
import { fileURLToPath } from "node:url"
import { formatSvg } from "./lib/format"
import { formatIssue, lint, SRC_DIR } from "./lib/lint"

/** The repository root: emitters write into `apps/*` from here. */
export const REPO_ROOT = fileURLToPath(new URL("../../..", import.meta.url))
/** The package root: `icons.json`, `src/`, `waves/`. */
export const PACKAGE_ROOT = fileURLToPath(new URL("..", import.meta.url))

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

function main(argv: string[]): number {
  const mode = argv[0] ?? "generate"
  switch (mode) {
    case "--lint":
      return runLint() ? 0 : 1
    case "--format":
      runFormat()
      return 0
    case "--check":
    case "generate":
      if (!runLint()) return 1
      console.log(`no emitters yet (${mode === "--check" ? "nothing to check" : "nothing to generate"})`)
      return 0
    default:
      console.error(`unknown option ${mode}; use --lint, --format or --check`)
      return 2
  }
}

process.exitCode = main(process.argv.slice(2))
