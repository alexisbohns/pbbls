import { existsSync, readdirSync, readFileSync, statSync } from "node:fs"
import { join } from "node:path"
import { formatSvg, ROOT_ATTRIBUTES } from "./format"
import { loadManifest, STATUSES, VARIANTS, type IconRow, type Manifest } from "./manifest"
import { parseSvg, type ParsedPath, type ParsedSvg } from "./parse"

/**
 * The canvas and manifest rules of `DNA.md`, machine-checked. Every issue names
 * the file and, for geometry, the offending number, so a fix is a nudge to the
 * drawing, never a relaxed rule.
 */

export type LintIssue = { file: string; rule: string; message: string }
export type LintResult = { errors: LintIssue[]; warnings: LintIssue[] }

export const SRC_DIR = "src"
export const WAVES_DIR = "waves"

/** Every coordinate sits inside this closed range (the 1.75 stroke's margin). */
export const RANGE: readonly [number, number] = [1.5, 22.5]
/** A twin lights at most this many stones. */
export const MAX_FILLED = 3
/** A lit shape above this area (square units) is a warning: it is probably the whole icon. */
export const WARN_AREA = 64

const KEBAB = /^[a-z0-9]+(-[a-z0-9]+)*$/
const PATH_ATTRIBUTES = new Set(["d", "fill", "fill-rule", "stroke"])
const COMMANDS = new Set(["M", "L", "C", "Q", "A", "Z"])
/** Coordinates per command, after the arc's radii, rotation and flags. */
const ARITY: Record<string, number> = { M: 2, L: 2, C: 6, Q: 4, A: 7, Z: 0 }

/** `home.svg` → base `home`; `home.active.svg` → base `home`, variant `active`. */
export function splitSourceName(file: string): { base: string; variant: string | null } | null {
  const m = /^([^.]+)(?:\.([^.]+))?\.svg$/.exec(file)
  if (!m || m[1] === undefined) return null
  return { base: m[1], variant: m[2] ?? null }
}

type Token = { command: string; numbers: number[] }

/** Splits path data into commands; throws on a command outside the grammar or a malformed number. */
export function tokenizePath(d: string): Token[] {
  const tokens: Token[] = []
  const re = /([A-Za-z])|(-?(?:\d+\.?\d*|\.\d+)(?:e-?\d+)?)|(,)|(\s+)|(.)/g
  for (const m of d.matchAll(re)) {
    if (m[1] !== undefined) tokens.push({ command: m[1], numbers: [] })
    else if (m[2] !== undefined) {
      const last = tokens[tokens.length - 1]
      if (!last) throw new Error(`a number before any command: ${m[2]}`)
      last.numbers.push(Number(m[2]))
    } else if (m[5] !== undefined) throw new Error(`unexpected character ${JSON.stringify(m[5])}`)
  }
  return tokens
}

/** The end and control points of a path, in order, with the command that set each. */
function points(tokens: Token[]): { x: number; y: number; command: string }[] {
  const out: { x: number; y: number; command: string }[] = []
  for (const { command, numbers } of tokens) {
    const arity = ARITY[command]
    if (arity === undefined) continue
    if (arity === 0) continue
    for (let i = 0; i + arity <= numbers.length; i += arity) {
      const group = numbers.slice(i, i + arity)
      const coords = command === "A" ? group.slice(5) : group
      for (let j = 0; j + 1 < coords.length; j += 2) {
        const x = coords[j]
        const y = coords[j + 1]
        if (x !== undefined && y !== undefined) out.push({ x, y, command })
      }
    }
  }
  return out
}

function sampleCubic(p0: [number, number], p1: [number, number], p2: [number, number], p3: [number, number], n: number): [number, number][] {
  const out: [number, number][] = []
  for (let k = 1; k <= n; k += 1) {
    const t = k / n
    const u = 1 - t
    out.push([
      u * u * u * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t * t * t * p3[0],
      u * u * u * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t * t * t * p3[1],
    ])
  }
  return out
}

/** The filled area of a path: each subpath flattened and measured (shoelace); even-odd subtracts the inner rings. */
export function filledArea(d: string, evenOdd: boolean): number {
  const subpaths: [number, number][][] = []
  let current: [number, number][] = []
  let cursor: [number, number] = [0, 0]
  for (const { command, numbers } of tokenizePath(d)) {
    const arity = ARITY[command] ?? 0
    if (command === "Z") continue
    for (let i = 0; i + arity <= numbers.length; i += arity) {
      const g = numbers.slice(i, i + arity) as number[]
      const at = (k: number): number => g[k] ?? 0
      if (command === "M") {
        if (current.length > 0) subpaths.push(current)
        current = [[at(0), at(1)]]
        cursor = [at(0), at(1)]
      } else if (command === "L") {
        cursor = [at(0), at(1)]
        current.push(cursor)
      } else if (command === "C") {
        const pts = sampleCubic(cursor, [at(0), at(1)], [at(2), at(3)], [at(4), at(5)], 8)
        current.push(...pts)
        cursor = [at(4), at(5)]
      } else if (command === "Q") {
        const c1: [number, number] = [cursor[0] + (2 / 3) * (at(0) - cursor[0]), cursor[1] + (2 / 3) * (at(1) - cursor[1])]
        const c2: [number, number] = [at(2) + (2 / 3) * (at(0) - at(2)), at(3) + (2 / 3) * (at(1) - at(3))]
        current.push(...sampleCubic(cursor, c1, c2, [at(2), at(3)], 8))
        cursor = [at(2), at(3)]
      } else if (command === "A") {
        cursor = [at(5), at(6)]
        current.push(cursor)
      }
    }
  }
  if (current.length > 0) subpaths.push(current)
  const areas = subpaths.map((ring) => {
    let sum = 0
    for (let i = 0; i < ring.length; i += 1) {
      const a = ring[i]
      const b = ring[(i + 1) % ring.length]
      if (a && b) sum += a[0] * b[1] - b[0] * a[1]
    }
    return Math.abs(sum) / 2
  })
  if (!evenOdd) return areas.reduce((s, a) => s + a, 0)
  const outer = Math.max(0, ...areas)
  return outer - (areas.reduce((s, a) => s + a, 0) - outer)
}

function lintRoot(parsed: ParsedSvg, file: string, errors: LintIssue[]): void {
  const seen = new Set<string>()
  for (const [key, value] of ROOT_ATTRIBUTES) {
    seen.add(key)
    const actual = parsed.root[key]
    if (actual === undefined) errors.push({ file, rule: "root", message: `<svg> lacks ${key}="${value}"` })
    else if (actual !== value) errors.push({ file, rule: "root", message: `<svg> has ${key}="${actual}", expected "${value}"` })
  }
  for (const key of Object.keys(parsed.root)) {
    if (!seen.has(key)) errors.push({ file, rule: "root", message: `<svg> carries ${key}="${parsed.root[key] ?? ""}", which is not allowed` })
  }
}

function lintPath(path: ParsedPath, index: number, file: string, isTwin: boolean, result: LintResult): void {
  const where = `path ${index + 1}`
  const { errors, warnings } = result
  for (const key of Object.keys(path.attrs)) {
    if (key === "stroke-dasharray") errors.push({ file, rule: "dash", message: `${where} has stroke-dasharray="${path.attrs[key] ?? ""}" (draw the dashes as short strokes)` })
    else if (!PATH_ATTRIBUTES.has(key)) errors.push({ file, rule: "elements", message: `${where} carries ${key}="${path.attrs[key] ?? ""}", which is not allowed` })
  }
  const fill = path.attrs.fill
  if (fill !== undefined && fill !== "currentColor") errors.push({ file, rule: "fill", message: `${where} has fill="${fill}", only currentColor is allowed` })
  if (path.filled && !isTwin) errors.push({ file, rule: "fill", message: `${where} is filled in a base file; only a twin lights a stone` })
  const rule = path.attrs["fill-rule"]
  if (rule !== undefined) {
    if (!path.filled) errors.push({ file, rule: "fill", message: `${where} has fill-rule on an unfilled path` })
    else if (rule !== "evenodd") errors.push({ file, rule: "fill", message: `${where} has fill-rule="${rule}", only evenodd is allowed` })
  }
  const stroke = path.attrs.stroke
  if (stroke !== undefined) {
    if (stroke !== "none") errors.push({ file, rule: "fill", message: `${where} has stroke="${stroke}", only none is allowed on a path` })
    else if (!path.filled) errors.push({ file, rule: "fill", message: `${where} has stroke="none" on an unfilled path` })
  }
  let tokens: Token[]
  try {
    tokens = tokenizePath(path.d)
  } catch (e) {
    errors.push({ file, rule: "commands", message: `${where}: ${e instanceof Error ? e.message : String(e)}` })
    return
  }
  for (const { command } of tokens) {
    if (!COMMANDS.has(command)) errors.push({ file, rule: "commands", message: `${where} uses ${command}; only absolute M L C Q A Z are allowed` })
  }
  for (const { x, y, command } of points(tokens)) {
    for (const n of [x, y]) {
      if (n < RANGE[0] || n > RANGE[1]) errors.push({ file, rule: "range", message: `${where}: ${n} (in ${command}) is outside ${RANGE[0]}..${RANGE[1]}` })
    }
  }
  if (path.filled && tokens.every((t) => COMMANDS.has(t.command))) {
    const area = filledArea(path.d, path.evenOdd)
    if (area > WARN_AREA) warnings.push({ file, rule: "fill", message: `${where} fills ${area.toFixed(1)} square units, above ${WARN_AREA}: is the whole icon lit?` })
  }
}

function lintSource(root: string, file: string, isTwin: boolean, result: LintResult): void {
  const text = readFileSync(join(root, SRC_DIR, file), "utf8")
  let parsed: ParsedSvg
  try {
    parsed = parseSvg(text, file)
  } catch (e) {
    result.errors.push({ file, rule: "elements", message: e instanceof Error ? e.message.replace(`${file}: `, "") : String(e) })
    return
  }
  lintRoot(parsed, file, result.errors)
  if (parsed.paths.length === 0) result.errors.push({ file, rule: "elements", message: "no <path>" })
  parsed.paths.forEach((path, i) => lintPath(path, i, file, isTwin, result))
  const filled = parsed.paths.filter((p) => p.filled).length
  if (filled > MAX_FILLED) result.errors.push({ file, rule: "fill", message: `${filled} filled paths; a twin lights at most ${MAX_FILLED}` })
  let canonical: string
  try {
    canonical = formatSvg(text)
  } catch (e) {
    result.errors.push({ file, rule: "canonical", message: `format fails: ${e instanceof Error ? e.message : String(e)}` })
    return
  }
  if (canonical !== text) result.errors.push({ file, rule: "canonical", message: "not in canonical form; run npm run format --workspace=@pbbls/icons" })
}

function lintManifest(root: string, manifest: Manifest, files: string[], errors: LintIssue[]): void {
  const file = "icons.json"
  const rows = new Map<string, IconRow>()
  for (const row of manifest.icons) {
    if (!KEBAB.test(row.name)) errors.push({ file, rule: "manifest", message: `"${row.name}" is not a kebab-case name` })
    if (rows.has(row.name)) errors.push({ file, rule: "manifest", message: `"${row.name}" is listed twice` })
    rows.set(row.name, row)
    if (!(STATUSES as readonly string[]).includes(row.status)) errors.push({ file, rule: "manifest", message: `${row.name}: unknown status "${row.status}"` })
    const wave = join(root, WAVES_DIR, row.wave)
    if (!existsSync(wave) || !statSync(wave).isDirectory()) errors.push({ file, rule: "manifest", message: `${row.name}: no folder ${WAVES_DIR}/${row.wave}/` })
    if (!files.includes(`${row.name}.svg`)) errors.push({ file, rule: "manifest", message: `${row.name}: no ${SRC_DIR}/${row.name}.svg` })
    for (const variant of row.variants) {
      if (!(VARIANTS as readonly string[]).includes(variant)) errors.push({ file, rule: "manifest", message: `${row.name}: variant "${variant}" is not one of ${VARIANTS.join(", ")}` })
      else if (!files.includes(`${row.name}.${variant}.svg`)) errors.push({ file, rule: "manifest", message: `${row.name}: no ${SRC_DIR}/${row.name}.${variant}.svg for variant "${variant}"` })
    }
  }
  for (const f of files) {
    const split = splitSourceName(f)
    if (!split) {
      errors.push({ file: f, rule: "manifest", message: "not a <name>.svg or <name>.<variant>.svg file" })
      continue
    }
    const base = rows.get(split.base)
    if (split.variant === null) {
      if (!base) errors.push({ file: f, rule: "manifest", message: `no row "${split.base}" in ${file}` })
      continue
    }
    if (!(VARIANTS as readonly string[]).includes(split.variant)) errors.push({ file: f, rule: "manifest", message: `variant "${split.variant}" is not one of ${VARIANTS.join(", ")}` })
    if (!base) errors.push({ file: f, rule: "manifest", message: `twin without its base: no row "${split.base}"` })
    else if (!base.variants.includes(split.variant)) errors.push({ file: f, rule: "manifest", message: `not listed in the variants of "${split.base}"` })
  }
}

/** Lints the package at `root` (the directory holding `icons.json`, `src/` and `waves/`). */
export function lint(root: string): LintResult {
  const result: LintResult = { errors: [], warnings: [] }
  const srcDir = join(root, SRC_DIR)
  const files = existsSync(srcDir) ? readdirSync(srcDir).filter((f) => f.endsWith(".svg")).sort() : []
  let manifest: Manifest | null = null
  try {
    manifest = loadManifest(root)
  } catch (e) {
    result.errors.push({ file: "icons.json", rule: "manifest", message: e instanceof Error ? e.message : String(e) })
  }
  if (manifest) lintManifest(root, manifest, files, result.errors)
  for (const f of files) {
    const split = splitSourceName(f)
    lintSource(root, f, split?.variant !== null && split?.variant !== undefined, result)
  }
  return result
}

export function formatIssue(issue: LintIssue): string {
  return `${issue.file}  [${issue.rule}]  ${issue.message}`
}
