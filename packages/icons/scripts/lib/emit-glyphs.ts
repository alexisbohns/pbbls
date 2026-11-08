import { readdirSync, readFileSync } from "node:fs"
import { join } from "node:path"
import { carveSolid } from "./carve"
import { GENERATED_NOTICE, type Emitter } from "./emit"
import type { GlyphData, GlyphPath, RenderedPath } from "./forms"
import { SRC_DIR, splitSourceName } from "./lint"
import { isOverrideForm, loadManifest, type IconRow } from "./manifest"
import { normalizePath } from "./normalize"
import { parseSvg } from "./parse"
import { readRoles } from "./roles"
import { loadStates } from "./states"

export const GLYPHS_FILE = "packages/icons/glyphs/glyphs.json"

/** One manifest row and its sources as `glyphs.json` stores them (spec §2). */
export function buildGlyph(row: IconRow, source: string, overrides: Record<string, string>): GlyphData {
  const { paths: parsed } = parseSvg(source, `${row.name}.svg`)
  const { roles } = readRoles(parsed)
  const paths: GlyphPath[] = parsed.map((p, i) => {
    const d = normalizePath(p.d)
    const hidden = roles.hidden.includes(i) ? { hidden: true as const } : {}
    if (roles.body.includes(i)) return { d, role: "body" }
    if (roles.focus.includes(i)) return { d, role: "focus", ...hidden }
    const mark = roles.marks.findIndex((group) => group.includes(i))
    return mark >= 0 ? { d, role: "mark", mark: mark + 1, ...hidden } : { d }
  })
  const body = paths.filter((p) => p.role === "body").map((p) => p.d)
  // A hidden path is neither cut from the solid nor kept on it; `keep` indexes what is left.
  const others = paths.filter((p) => p.role !== "body" && !p.hidden).map((p) => p.d)
  const carved = body.length > 0 ? carveSolid(body, others) : null
  const rendered: Record<string, RenderedPath[]> = {}
  for (const [form, text] of Object.entries(overrides)) {
    rendered[form] = parseSvg(text, `${row.name}.${form}.svg`).paths.map((p) => ({
      d: normalizePath(p.d),
      fill: p.filled,
      evenOdd: p.evenOdd,
      stroke: !p.unstroked,
    }))
  }
  return {
    name: row.name,
    meaning: row.meaning,
    family: row.family,
    tags: row.tags,
    paths,
    marks: roles.marks.length,
    solid: carved ? { d: carved.d, keep: carved.keep } : null,
    overrides: rendered,
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
      for (const f of files) {
        const split = splitSourceName(f)
        if (split?.base === row.name && split.variant !== null && isOverrideForm(split.variant)) overrides[split.variant] = readFileSync(join(dir, f), "utf8")
      }
      return buildGlyph(row, readFileSync(join(dir, `${row.name}.svg`), "utf8"), overrides)
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
