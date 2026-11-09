import data from "@pbbls/icons/glyphs.json"
import review from "@pbbls/icons/review.json"

/**
 * The forms data the design page reviews (#1607, part 3): every glyph from the
 * generator's `glyphs.json`, drawn with the same rules as `renderForm` in
 * packages/icons/scripts/lib/forms.ts. Part 4 replaces this with the web
 * components.
 */

/** A hidden path (mark or step) is not drawn at rest; it appears filled when its role fills. */
export type GlyphPath = {
  d: string
  role?: "body" | "mark" | "step"
  step?: number
  part?: string
  hidden?: true
  /** Mark or step with lines inside it: filled and stroked minus those lines, even-odd and unstroked. */
  fill?: string
  /** Indices into `paths` of the lines `fill` is carved through; not drawn once this path is filled. */
  cuts?: number[]
}
export type RenderedPath = { d: string; fill: boolean; evenOdd: boolean; stroke: boolean }
export type Glyph = {
  name: string
  meaning: string
  family: string | null
  tags: Record<string, string[]>
  paths: GlyphPath[]
  parts: string[]
  steps: number
  /** `keep` and `lit` index `paths`. */
  carved: Record<string, { d: string; keep: number[]; lit: number[] }>
  dotted: RenderedPath[] | null
  overrides: Record<string, RenderedPath[]>
}

export const GLYPHS: readonly Glyph[] = data.glyphs as Glyph[]

/** The page's form choice: a form name, `progress` meaning k from the slider. */
export type FormChoice = "regular" | "marked" | "solid" | "progress"

export function availableForms(glyph: Glyph): string[] {
  const forms = ["regular"]
  if (glyph.paths.some((p) => p.role === "mark")) forms.push("marked")
  if (glyph.carved.solid) forms.push("solid")
  for (let k = 1; k <= glyph.steps; k += 1) forms.push(`progress-${k}`)
  return forms
}

const stroked = (d: string): RenderedPath => ({ d, fill: false, evenOdd: false, stroke: true })
const filledPath = (d: string): RenderedPath => ({ d, fill: true, evenOdd: false, stroke: true })

/** Whether a form's role fills this path (a hidden path is drawn only then). */
export function isLit(p: GlyphPath, choice: FormChoice, k: number): boolean {
  return (choice === "marked" && p.role === "mark") || (choice === "progress" && p.role === "step" && (p.step ?? 0) <= k)
}

/** The paths a form draws; a form the glyph lacks draws regular (the page dims it). */
export function formPaths(glyph: Glyph, choice: FormChoice, k = 0): RenderedPath[] {
  const effective = choice === "progress" && k === 0 ? "regular" : choice
  const key = effective === "progress" ? `progress-${Math.min(k, glyph.steps)}` : effective
  const override = glyph.overrides[key]
  if (override) return override
  const solid = glyph.carved.solid
  if (effective === "solid" && solid) {
    return [
      { d: solid.d, fill: true, evenOdd: true, stroke: false },
      ...solid.keep.flatMap((i) => {
        const p = glyph.paths[i]
        return p ? [stroked(p.d)] : []
      }),
    ]
  }
  const form: FormChoice = effective === "marked" && !glyph.paths.some((p) => p.role === "mark") ? "regular" : effective === "progress" && glyph.steps === 0 ? "regular" : effective
  const cut = cutIndices(glyph, form, k)
  return glyph.paths.flatMap((p, i) => {
    const on = isLit(p, form, k)
    if ((p.hidden && !on) || cut.has(i)) return []
    if (on && p.fill !== undefined) return [{ d: p.fill, fill: true, evenOdd: true, stroke: false }]
    return [on ? filledPath(p.d) : stroked(p.d)]
  })
}

/** The paths a lit, carved path is cut through: they are not drawn. */
export function cutIndices(glyph: Glyph, choice: FormChoice, k: number): Set<number> {
  return new Set(glyph.paths.flatMap((p) => (isLit(p, choice, k) && p.fill !== undefined ? (p.cuts ?? []) : [])))
}

export function hasForm(glyph: Glyph, choice: FormChoice): boolean {
  if (choice === "regular") return true
  if (choice === "progress") return glyph.steps > 0
  return availableForms(glyph).includes(choice)
}

/** The Icons page's search: the name, the meaning and every tag value. */
export function glyphMatchesQuery(glyph: Glyph, query: string): boolean {
  const q = query.trim().toLowerCase()
  if (!q) return true
  return glyph.name.includes(q) || glyph.meaning.toLowerCase().includes(q) || Object.values(glyph.tags).some((list) => list.some((v) => v.toLowerCase().includes(q)))
}

const FLAGGED: ReadonlySet<string> = new Set((review as { name: string; flags: unknown[] }[]).filter((r) => r.flags.length > 0).map((r) => r.name))

export function needsReview(name: string): boolean {
  return FLAGGED.has(name)
}

/** Which glyphs the Icons page lists: every one, those the role pass flagged, or those the review marked fix. */
export type GlyphShow = "all" | "review" | "fix"

export function showsGlyph(name: string, show: GlyphShow, verdicts: Readonly<Record<string, string>>): boolean {
  if (show === "review") return FLAGGED.has(name)
  if (show === "fix") return verdicts[name] === "fix"
  return true
}

/** What the role pass (scripts/explore/roles.py) flagged on a glyph, from review.json. */
export type ReviewFlag =
  | { kind: "added-dot"; from: string; path: number; area: number }
  | { kind: "redrawn"; from: string; area: number }
  | { kind: "conflict"; path: number; was: string; wanted: string }
  /** A twin's new geometry matched nothing, after another twin had already added a dot. */
  | { kind: "conflict"; from: string; was: string; area: number }

const FLAGS: ReadonlyMap<string, readonly ReviewFlag[]> = new Map(
  (review as { name: string; flags: ReviewFlag[] }[]).filter((r) => r.flags.length > 0).map((r) => [r.name, r.flags]),
)

export function reviewFlags(name: string): readonly ReviewFlag[] {
  return FLAGS.get(name) ?? []
}

/** A flag in plain words; paths are numbered from 1, as the roles map labels them. */
export function describeFlag(flag: ReviewFlag): string {
  switch (flag.kind) {
    case "added-dot":
      return `Path ${flag.path + 1} was added from the ${flag.from} twin as a hidden mark (area ${flag.area}): it is not drawn at rest, only lit.`
    case "redrawn":
      return `The ${flag.from} twin redraws the glyph (${flag.area} units of new area): its state cannot be derived from roles, so it needs an override or a redrawing.`
    case "conflict":
      return "path" in flag
        ? `Path ${flag.path + 1} is ${flag.was}, but another twin wanted it as ${flag.wanted}.`
        : `The ${flag.from} twin adds ${flag.area} units of its own, after another twin already took the ${flag.was} slot: one of the two is lost.`
  }
}

/** The flagged paths' indices, to pick them out in the roles map. */
export function flaggedPaths(name: string): Set<number> {
  return new Set(reviewFlags(name).flatMap((f) => ("path" in f ? [f.path] : [])))
}

/** Every form the glyph has (the order of `availableForms`), as the review dialog lays them out. */
export function formChoices(glyph: Glyph): { label: string; form: FormChoice; k: number; override: boolean }[] {
  const choices: { label: string; form: FormChoice; k: number }[] = [{ label: "regular", form: "regular", k: 0 }]
  if (glyph.paths.some((p) => p.role === "mark")) choices.push({ label: "marked", form: "marked", k: 0 })
  if (glyph.carved.solid) choices.push({ label: "solid", form: "solid", k: 0 })
  for (let k = 1; k <= glyph.steps; k += 1) choices.push({ label: `progress-${k}`, form: "progress", k })
  return choices.map((c) => ({ ...c, override: c.label in glyph.overrides }))
}
