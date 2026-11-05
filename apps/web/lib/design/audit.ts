/**
 * The design page's inconsistency scan: what the web's source draws outside
 * the design system. Pure (text in, findings out) so it is tested here and
 * run over the files at build time by `audit-source.ts`.
 *
 * - `lucide`: a Lucide icon still imported, beside the hand-drawn set.
 * - `hex` / `color-function`: a colour written as a literal instead of a token.
 * - `palette`: a Tailwind default palette class (`text-red-500`, `bg-white`),
 *   which no theme, world or mode re-colours.
 * - `arbitrary`: an arbitrary value off the scale (`text-[13px]`, `rounded-[10px]`).
 *
 * Heuristic by design: a finding is a prompt to look, not a verdict. Comments
 * are skipped, so issue references (`#986`) and notes never count.
 */

export type FindingKind = "lucide" | "hex" | "color-function" | "palette" | "arbitrary"

export interface Finding {
  readonly kind: FindingKind
  /** The literal as written (an icon name, `#F8F0F0`, `text-red-500`, `text-[13px]`). */
  readonly value: string
  /** Relative to apps/web. */
  readonly file: string
  readonly line: number
}

/** Blanks comments but keeps every newline, so offsets still map to lines. */
export function stripComments(text: string): string {
  const blank = (s: string) => s.replace(/[^\n]/g, " ")
  return text
    .replace(/\/\*[\s\S]*?\*\//g, blank)
    .replace(/(^|[^:\w"'`])(\/\/.*)$/gm, (_, before: string, comment: string) => before + blank(comment))
}

function lineAt(text: string, index: number): number {
  let line = 1
  for (let i = 0; i < index; i++) if (text.charCodeAt(i) === 10) line++
  return line
}

const LUCIDE_IMPORT = /import\s+(?:type\s+)?\{([^}]*)\}\s*from\s*["']lucide-react["']/g
const HEX = /(?<![\w&#/.-])#([0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{4}|[0-9a-fA-F]{3})(?![\w-])/g
const COLOR_FUNCTION = /(?<![\w-])(?:rgba?|hsla?|oklch|oklab|lch|lab)\([^)]*\)/g
const HAS_COLOR_FUNCTION = /(?:rgba?|hsla?|oklch|oklab|lch|lab)\(/
const PALETTE_HUES =
  "slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose"
const PALETTE_UTILS = "bg|text|border|ring|fill|stroke|from|via|to|outline|decoration|shadow|accent|caret|divide|placeholder"
const PALETTE = new RegExp(
  `(?<![\\w-])(?:${PALETTE_UTILS})-(?:(?:${PALETTE_HUES})-(?:50|[1-9]00|950)|black|white)(?:\\/\\d+)?(?![\\w-])`,
  "g",
)
// `utility-[value]` not followed by `:` or `/name:` (a variant, `data-[state=open]:`, `group-data-[size=sm]/button:`).
const ARBITRARY = /(?<![\w-])([a-z][a-z0-9]*(?:-[a-z0-9]+)*)-\[([^\]\s"'`]+)\](?![:/])/g
/** Lucide's type and helper exports: not drawings. */
const LUCIDE_NON_ICONS = new Set(["LucideIcon", "LucideProps", "IconNode", "createLucideIcon", "icons"])

/** A hex that is only digits and short is far more often a stray reference than a colour. */
function isLikelyColorHex(digits: string): boolean {
  if (/[a-fA-F]/.test(digits)) return true
  if (digits.length >= 6) return true
  return /^(\d)\1+$/.test(digits)
}

function isTokenValue(value: string): boolean {
  return value.includes("var(") || value.includes("--")
}

function isColorValue(value: string): boolean {
  return value.includes("#") || HAS_COLOR_FUNCTION.test(value)
}

export function scanSource(file: string, source: string): Finding[] {
  const text = stripComments(source)
  const findings: Finding[] = []
  const add = (kind: FindingKind, value: string, index: number) =>
    findings.push({ kind, value, file, line: lineAt(text, index) })

  for (const m of text.matchAll(LUCIDE_IMPORT)) {
    for (const spec of m[1].split(",")) {
      const name = spec.replace(/^\s*type\s+/, "").split(/\s+as\s+/)[0].trim()
      if (name && /^[A-Z]/.test(name) && !LUCIDE_NON_ICONS.has(name)) add("lucide", name, m.index ?? 0)
    }
  }
  for (const m of text.matchAll(HEX)) {
    if (isLikelyColorHex(m[1])) add("hex", m[0].toUpperCase(), m.index ?? 0)
  }
  for (const m of text.matchAll(COLOR_FUNCTION)) {
    if (!isTokenValue(m[0])) add("color-function", m[0], m.index ?? 0)
  }
  for (const m of text.matchAll(PALETTE)) add("palette", m[0], m.index ?? 0)
  for (const m of text.matchAll(ARBITRARY)) {
    const value = m[2]
    if (isTokenValue(value) || isColorValue(value)) continue
    add("arbitrary", m[0], m.index ?? 0)
  }
  return findings
}

/** What an arbitrary value is off: the type scale, the shape scale, spacing, sizing. */
export type ArbitraryFamily = "type" | "radius" | "spacing" | "size" | "other"

export function arbitraryFamily(value: string): ArbitraryFamily {
  const utility = value.slice(0, value.indexOf("-["))
  if (utility === "text" || utility === "leading" || utility === "tracking" || utility === "font") return "type"
  if (utility.startsWith("rounded")) return "radius"
  if (/^(?:-?[pm][trblxyse]?|gap(?:-[xy])?|space-[xy]|inset(?:-[xy])?|top|right|bottom|left|scroll-m[trblxy]?|scroll-p[trblxy]?)$/.test(utility)) {
    return "spacing"
  }
  if (/^(?:w|h|size|min-w|min-h|max-w|max-h|basis)$/.test(utility)) return "size"
  return "other"
}

export interface Occurrence {
  readonly file: string
  readonly line: number
}

export interface FindingGroup {
  readonly kind: FindingKind
  readonly value: string
  readonly count: number
  readonly occurrences: readonly Occurrence[]
}

/** One row per distinct value, most frequent first, then by value. */
export function groupFindings(findings: readonly Finding[], kind: FindingKind): FindingGroup[] {
  const groups = new Map<string, Occurrence[]>()
  for (const f of findings) {
    if (f.kind !== kind) continue
    const list = groups.get(f.value) ?? []
    list.push({ file: f.file, line: f.line })
    groups.set(f.value, list)
  }
  return [...groups.entries()]
    .map(([value, occurrences]) => ({ kind, value, count: occurrences.length, occurrences }))
    .sort((a, b) => b.count - a.count || a.value.localeCompare(b.value))
}

/** Files with the most findings, for a "start here" list. */
export function hotspots(findings: readonly Finding[], limit = 10): { file: string; count: number }[] {
  const counts = new Map<string, number>()
  for (const f of findings) counts.set(f.file, (counts.get(f.file) ?? 0) + 1)
  return [...counts.entries()]
    .map(([file, count]) => ({ file, count }))
    .sort((a, b) => b.count - a.count || a.file.localeCompare(b.file))
    .slice(0, limit)
}

export interface CoverageRow {
  /** The folder under components/ ("path", "souls", "ui"). */
  readonly domain: string
  readonly total: number
  readonly documented: number
  readonly missing: readonly string[]
}

/**
 * Which component files the design page renders directly, per domain folder.
 * Views compose many more; this counts what has its own specimen.
 */
export function coverage(componentFiles: readonly string[], documented: ReadonlySet<string>): CoverageRow[] {
  const byDomain = new Map<string, string[]>()
  for (const file of componentFiles) {
    const domain = file.split("/")[1] ?? "(root)"
    const list = byDomain.get(domain) ?? []
    list.push(file)
    byDomain.set(domain, list)
  }
  return [...byDomain.entries()]
    .map(([domain, files]) => {
      const missing = files.filter((f) => !documented.has(f)).sort()
      return { domain, total: files.length, documented: files.length - missing.length, missing }
    })
    .sort((a, b) => b.missing.length - a.missing.length || a.domain.localeCompare(b.domain))
}

export interface DesignAudit {
  readonly scannedFiles: number
  readonly lucide: readonly FindingGroup[]
  readonly hex: readonly FindingGroup[]
  readonly colorFunctions: readonly FindingGroup[]
  readonly palette: readonly FindingGroup[]
  readonly arbitrary: readonly (FindingGroup & { family: ArbitraryFamily })[]
  readonly hotspots: readonly { file: string; count: number }[]
  readonly coverage: readonly CoverageRow[]
}

export function buildAudit(
  sources: readonly { file: string; text: string }[],
  componentFiles: readonly string[],
  documented: ReadonlySet<string>,
): DesignAudit {
  const findings = sources.flatMap((s) => scanSource(s.file, s.text))
  return {
    scannedFiles: sources.length,
    lucide: groupFindings(findings, "lucide"),
    hex: groupFindings(findings, "hex"),
    colorFunctions: groupFindings(findings, "color-function"),
    palette: groupFindings(findings, "palette"),
    arbitrary: groupFindings(findings, "arbitrary").map((g) => ({ ...g, family: arbitraryFamily(g.value) })),
    hotspots: hotspots(findings),
    coverage: coverage(componentFiles, documented),
  }
}
