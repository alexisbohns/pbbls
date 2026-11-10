import { readdirSync, readFileSync } from "node:fs"
import { join } from "node:path"
import { carveSolid } from "./carve"
import { GENERATED_NOTICE, type Emitter } from "./emit"
import { formKey, type CarvedFill, type Fill, type GlyphData, type GlyphPath, type RenderedPath } from "./forms"
import { SRC_DIR, splitSourceName } from "./lint"
import { DOTTED_FORM, isOverrideForm, loadManifest, type IconRow } from "./manifest"
import { normalizePath } from "./normalize"
import { parseSvg } from "./parse"
import { partCombinations, readRoles } from "./roles"
import { loadStates } from "./states"

export const GLYPHS_FILE = "packages/icons/glyphs/glyphs.json"

/** `list[n]` for each n, skipping any out of range. */
const at = (list: readonly number[], ns: readonly number[]): number[] =>
  ns.flatMap((n) => {
    const i = list[n]
    return i === undefined ? [] : [i]
  })

/**
 * A solid or parts fill (spec D3): the chosen body paths filled and carved by
 * every other drawn path. Under `marked` the mark is cut out where it lies
 * inside the fill and drawn filled (`lit`) where it does not.
 */
function carveFill(paths: readonly GlyphPath[], body: readonly number[], marked: boolean): CarvedFill {
  const isMark = (p: GlyphPath) => p.role === "mark"
  const lines = paths.flatMap((p, i) => (!body.includes(i) && !p.hidden && !(marked && isMark(p)) ? [i] : []))
  const holes = marked ? paths.flatMap((p, i) => (isMark(p) ? [i] : [])) : []
  const d = (i: number) => paths[i]?.d ?? ""
  const carved = carveSolid(body.map(d), lines.map(d), holes.map(d))
  const lit = at(holes, carved.outside)
  // A lit mark that is carved itself leaves out the lines it is cut through.
  const cut = new Set(lit.flatMap((i) => (paths[i]?.fill !== undefined ? (paths[i]?.cuts ?? []) : [])))
  return { d: carved.d, keep: at(lines, carved.keep).filter((i) => !cut.has(i)), lit }
}

function rendered(text: string, file: string): RenderedPath[] {
  return parseSvg(text, file).paths.map((p) => ({ d: normalizePath(p.d), fill: p.filled, evenOdd: p.evenOdd, stroke: !p.unstroked }))
}

/** One manifest row and its sources as `glyphs.json` stores them (spec §2). */
export function buildGlyph(row: IconRow, source: string, overrides: Record<string, string>, dotted: string | null = null): GlyphData {
  const { paths: parsed } = parseSvg(source, `${row.name}.svg`)
  const { roles } = readRoles(parsed)
  const partOf = new Map(roles.parts.flatMap((part) => part.paths.map((i): [number, string] => [i, part.name])))
  const paths: GlyphPath[] = parsed.map((p, i) => {
    const d = normalizePath(p.d)
    const hidden = roles.hidden.includes(i) ? { hidden: true as const } : {}
    if (roles.body.includes(i)) {
      const part = partOf.get(i)
      return part === undefined ? { d, role: "body" } : { d, role: "body", part }
    }
    if (roles.mark.includes(i)) return { d, role: "mark", ...hidden }
    const step = roles.steps.findIndex((group) => group.includes(i))
    return step >= 0 ? { d, role: "step", step: step + 1, ...hidden } : { d }
  })
  // A mark or step with lines inside it would paint over them: store it carved like the solid.
  paths.forEach((p, i) => {
    if (p.role !== "mark" && p.role !== "step") return
    const inner = paths.flatMap((q, j) => (j !== i && !q.hidden ? [j] : []))
    const carved = carveSolid([p.d], inner.map((j) => paths[j]?.d ?? ""))
    if (carved.keep.length === inner.length) return
    p.fill = carved.d
    p.cuts = inner.filter((_, n) => !carved.keep.includes(n))
  })
  const carved: Record<string, CarvedFill> = {}
  if (roles.body.length > 0) {
    const fills: { fill: Fill; body: number[] }[] = [
      { fill: { kind: "solid" }, body: roles.body },
      ...partCombinations(roles.parts.map((p) => p.name)).map((names) => ({
        fill: { kind: "parts" as const, parts: names },
        body: roles.parts.filter((p) => names.includes(p.name)).flatMap((p) => p.paths),
      })),
    ]
    for (const { fill, body } of fills) {
      carved[formKey({ fill })] = carveFill(paths, body, false)
      if (roles.mark.length > 0) carved[formKey({ fill, marked: true })] = carveFill(paths, body, true)
    }
  }
  const drawn: Record<string, RenderedPath[]> = {}
  for (const [form, text] of Object.entries(overrides)) drawn[form] = rendered(text, `${row.name}.${form}.svg`)
  return {
    name: row.name,
    meaning: row.meaning,
    family: row.family,
    tags: row.tags,
    paths,
    parts: roles.parts.map((p) => p.name),
    steps: roles.steps.length,
    carved,
    dotted: dotted === null ? null : rendered(dotted, `${row.name}.dotted.svg`),
    overrides: drawn,
  }
}

/** Every non-exploring row with its source and overrides, in manifest order. */
export function loadGlyphs(packageRoot: string): GlyphData[] {
  const dir = join(packageRoot, SRC_DIR)
  const files = readdirSync(dir)
  return loadManifest(packageRoot)
    .icons.filter((row) => row.status !== "exploring")
    .map((row) => {
      const overrides: Record<string, string> = {}
      let dotted: string | null = null
      for (const f of files) {
        const split = splitSourceName(f)
        if (split?.base !== row.name || split.variant === null) continue
        if (split.variant === DOTTED_FORM) dotted = readFileSync(join(dir, f), "utf8")
        else if (isOverrideForm(split.variant)) overrides[split.variant] = readFileSync(join(dir, f), "utf8")
      }
      return buildGlyph(row, readFileSync(join(dir, `${row.name}.svg`), "utf8"), overrides, dotted)
    })
}

/** Builds the emitter for a package root: it reads the sources itself, not the old `EmittedIcon` list. */
export function glyphsEmitter(packageRoot: string): Emitter {
  return {
    name: "glyphs",
    owned: { dirs: [], files: [GLYPHS_FILE], patterns: [] },
    emit() {
      const data = { notice: GENERATED_NOTICE, version: 1, states: loadStates(packageRoot), glyphs: loadGlyphs(packageRoot) }
      return new Map([[GLYPHS_FILE, JSON.stringify(data) + "\n"]])
    },
  }
}
