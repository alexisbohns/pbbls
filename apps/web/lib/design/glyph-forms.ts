import data from "@pbbls/icons/glyphs.json"
import review from "@pbbls/icons/review.json"

/**
 * The forms data the design page reviews (#1607, part 3): every glyph from the
 * generator's `glyphs.json`, drawn with the same rules as `renderForm` in
 * packages/icons/scripts/lib/forms.ts. Part 4 replaces this with the web
 * components.
 */

/** A hidden path (mark or focus) is not drawn at rest; it appears filled when its role fills. */
export type GlyphPath = {
  d: string
  role?: "body" | "mark" | "focus"
  mark?: number
  hidden?: true
  /** Mark or focus with lines inside it: filled and stroked minus those lines, even-odd and unstroked. */
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
  marks: number
  /** `keep` indexes the glyph's non-body, non-hidden paths. */
  solid: { d: string; keep: number[] } | null
  overrides: Record<string, RenderedPath[]>
}

export const GLYPHS: readonly Glyph[] = data.glyphs as Glyph[]

/** The page's form choice: a form name, `progress` meaning k from the slider. */
export type FormChoice = "regular" | "half" | "solid" | "progress"

export function availableForms(glyph: Glyph): string[] {
  const forms = ["regular"]
  if (glyph.paths.some((p) => p.role === "focus")) forms.push("half")
  if (glyph.solid) forms.push("solid")
  for (let k = 1; k <= glyph.marks; k += 1) forms.push(`progress-${k}`)
  return forms
}

const stroked = (d: string): RenderedPath => ({ d, fill: false, evenOdd: false, stroke: true })
const filledPath = (d: string): RenderedPath => ({ d, fill: true, evenOdd: false, stroke: true })

/** Whether a form's role fills this path (a hidden path is drawn only then). */
export function isLit(p: GlyphPath, choice: FormChoice, k: number): boolean {
  return (choice === "half" && p.role === "focus") || (choice === "progress" && p.role === "mark" && (p.mark ?? 0) <= k)
}

/** The paths a form draws; a form the glyph lacks draws regular (the page dims it). */
export function formPaths(glyph: Glyph, choice: FormChoice, k = 0): RenderedPath[] {
  const effective = choice === "progress" && k === 0 ? "regular" : choice
  const key = effective === "progress" ? `progress-${Math.min(k, glyph.marks)}` : effective
  const override = glyph.overrides[key]
  if (override) return override
  if (effective === "solid" && glyph.solid) {
    const others = glyph.paths.filter((p) => p.role !== "body" && !p.hidden)
    return [{ d: glyph.solid.d, fill: true, evenOdd: true, stroke: false }, ...glyph.solid.keep.flatMap((i) => (others[i] ? [stroked(others[i].d)] : []))]
  }
  const form: FormChoice = effective === "half" && !glyph.paths.some((p) => p.role === "focus") ? "regular" : effective === "progress" && glyph.marks === 0 ? "regular" : effective
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
  if (choice === "progress") return glyph.marks > 0
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
