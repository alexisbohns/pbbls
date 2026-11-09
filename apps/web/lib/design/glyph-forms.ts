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
  part?: string
  step?: number
  hidden?: true
  /** Mark or step with lines inside it: filled and stroked minus those lines, even-odd and unstroked. */
  fill?: string
  /** Indices into `paths` of the lines `fill` is carved through; not drawn once this path is filled. */
  cuts?: number[]
}
export type RenderedPath = { d: string; fill: boolean; evenOdd: boolean; stroke: boolean }
/** A solid or parts fill: `keep` drawn stroked on it, `lit` (a mark outside it) drawn filled; indices into `paths`. */
export type CarvedFill = { d: string; keep: number[]; lit: number[] }
export type Glyph = {
  name: string
  meaning: string
  family: string | null
  tags: Record<string, string[]>
  paths: GlyphPath[]
  parts: string[]
  steps: number
  carved: Record<string, CarvedFill>
  dotted: RenderedPath[] | null
  overrides: Record<string, RenderedPath[]>
}

export const GLYPHS: readonly Glyph[] = data.glyphs as Glyph[]

export type Fill = { kind: "regular" } | { kind: "solid" } | { kind: "parts"; parts: string[] } | { kind: "progress"; k: number } | { kind: "dotted" }
/** A form (spec D3): a fill, plus the mark when the glyph has one. */
export type Form = { fill: Fill; marked?: boolean }

const REGULAR: Form = { fill: { kind: "regular" } }

export function formKey(form: Form): string {
  const f = form.fill
  const fill = f.kind === "parts" ? `parts-${f.parts.join("+")}` : f.kind === "progress" ? `progress-${f.k}` : f.kind
  if (!form.marked) return fill
  return fill === "regular" ? "marked" : `${fill}-marked`
}

export function hasMark(glyph: Glyph): boolean {
  return glyph.paths.some((p) => p.role === "mark")
}

/** Every combination of parts short of all of them, smallest first (as packages/icons/scripts/lib/roles.ts). */
function partCombinations(names: readonly string[]): string[][] {
  const out: string[][] = []
  for (let bits = 1; bits < (1 << names.length) - 1; bits += 1) out.push(names.filter((_, i) => bits & (1 << i)))
  return out.sort((a, b) => a.length - b.length)
}

/** Every form the glyph has, in the generator's order: each fill then the same fill marked, dotted after regular. */
export function glyphForms(glyph: Glyph): Form[] {
  const fills: Fill[] = [{ kind: "regular" }]
  if (glyph.carved.solid) fills.push({ kind: "solid" })
  for (const parts of partCombinations(glyph.parts)) fills.push({ kind: "parts", parts })
  for (let k = 1; k <= glyph.steps; k += 1) fills.push({ kind: "progress", k })
  const mark = hasMark(glyph)
  const forms = fills.flatMap((fill): Form[] => (mark ? [{ fill }, { fill, marked: true }] : [{ fill }]))
  if (glyph.dotted) forms.splice(mark ? 2 : 1, 0, { fill: { kind: "dotted" } })
  return forms
}

/** progress(0) is regular. */
function normalize(form: Form): Form {
  return form.fill.kind === "progress" && form.fill.k <= 0 ? { fill: { kind: "regular" }, marked: form.marked } : form
}

export function hasForm(glyph: Glyph, form: Form): boolean {
  const key = formKey(normalize(form))
  return glyphForms(glyph).some((f) => formKey(f) === key)
}

/** The form actually drawn: the form itself when the glyph has it, else regular. */
export function drawnForm(glyph: Glyph, form: Form): Form {
  const f = normalize(form)
  return hasForm(glyph, f) ? f : REGULAR
}

export function isCarved(form: Form): boolean {
  return form.fill.kind === "solid" || form.fill.kind === "parts"
}

/** Whether a stroke-level form (regular, marked, progress) fills this path; a hidden path is drawn only then. */
export function isLit(p: GlyphPath, form: Form): boolean {
  const k = form.fill.kind === "progress" ? form.fill.k : 0
  return (form.marked === true && p.role === "mark") || (p.role === "step" && (p.step ?? 0) <= k)
}

/** The paths a lit, carved path is cut through: they are not drawn. */
export function cutIndices(glyph: Glyph, form: Form): Set<number> {
  return new Set(glyph.paths.flatMap((p) => (isLit(p, form) && p.fill !== undefined ? (p.cuts ?? []) : [])))
}

const stroked = (d: string): RenderedPath => ({ d, fill: false, evenOdd: false, stroke: true })
const filledPath = (d: string): RenderedPath => ({ d, fill: true, evenOdd: false, stroke: true })
const carvedPath = (d: string): RenderedPath => ({ d, fill: true, evenOdd: true, stroke: false })
const litPath = (p: GlyphPath): RenderedPath => (p.fill !== undefined ? carvedPath(p.fill) : filledPath(p.d))

/** The paths a form draws, as `renderForm` does; a form the glyph lacks draws regular. */
export function formPaths(glyph: Glyph, form: Form): RenderedPath[] {
  const drawn = drawnForm(glyph, form)
  const key = formKey(drawn)
  const override = glyph.overrides[key]
  if (override) return override
  if (drawn.fill.kind === "dotted") return glyph.dotted ?? []
  const carved = isCarved(drawn) ? glyph.carved[key] : undefined
  if (carved) {
    const pick = (i: number, draw: (p: GlyphPath) => RenderedPath) => {
      const p = glyph.paths[i]
      return p ? [draw(p)] : []
    }
    return [carvedPath(carved.d), ...carved.keep.flatMap((i) => pick(i, (p) => stroked(p.d))), ...carved.lit.flatMap((i) => pick(i, litPath))]
  }
  const cut = cutIndices(glyph, drawn)
  return glyph.paths.flatMap((p, i) => {
    const on = isLit(p, drawn)
    if ((p.hidden && !on) || cut.has(i)) return []
    return [on ? litPath(p) : stroked(p.d)]
  })
}

/** A form in words, for the review dialog: `solid · marked`, `left + middle`, `progress 2`. */
export function formLabel(form: Form): string {
  const f = form.fill
  const fill = f.kind === "parts" ? f.parts.join(" + ") : f.kind === "progress" ? `progress ${f.k}` : f.kind
  if (!form.marked) return fill
  return f.kind === "regular" ? "marked" : `${fill} · marked`
}

/** Every form the glyph has, as the review dialog lays them out. */
export function formChoices(glyph: Glyph): { key: string; label: string; form: Form; override: boolean }[] {
  return glyphForms(glyph).map((form) => {
    const key = formKey(form)
    return { key, label: formLabel(form), form, override: key in glyph.overrides }
  })
}

/** The fill picker: every glyph, those with no fill but regular, or those with a dotted, solid or parts fill. */
export type FillFilter = "all" | "regular" | "dotted" | "solid" | "parts"
export type MarkFilter = "any" | "marked" | "unmarked"
/** The Icons page's pickers (spec §3): each lists only the glyphs that have its form and draws them in it. */
export type GlyphView = { fill: FillFilter; mark: MarkFilter; progress: boolean; k: number }
export const DEFAULT_VIEW: GlyphView = { fill: "all", mark: "any", progress: false, k: 1 }

export function isDefaultView(view: GlyphView): boolean {
  return view.fill === "all" && view.mark === "any" && !view.progress
}

/** A glyph whose only fill is regular: no body, no steps, no dotted drawing (it may still have a mark). */
export function onlyRegular(glyph: Glyph): boolean {
  return glyph.carved.solid === undefined && glyph.steps === 0 && glyph.dotted === null
}

export function viewMatches(glyph: Glyph, view: GlyphView): boolean {
  const fill =
    view.fill === "all" ||
    (view.fill === "regular" && onlyRegular(glyph)) ||
    (view.fill === "dotted" && glyph.dotted !== null) ||
    (view.fill === "solid" && glyph.carved.solid !== undefined) ||
    (view.fill === "parts" && glyph.parts.length > 0)
  const mark = view.mark === "any" || (view.mark === "marked") === hasMark(glyph)
  return fill && mark && (!view.progress || glyph.steps > 0)
}

/** The form a tile draws: progress draws the steps (the fill picker then only filters); parts fills the first part. */
export function viewForm(glyph: Glyph, view: GlyphView): Form {
  const marked = view.mark === "marked" && hasMark(glyph)
  if (view.progress && glyph.steps > 0) return { fill: { kind: "progress", k: Math.min(view.k, glyph.steps) }, marked }
  if (view.fill === "dotted" && glyph.dotted) return { fill: { kind: "dotted" } }
  if (view.fill === "solid" && glyph.carved.solid) return { fill: { kind: "solid" }, marked }
  const first = glyph.parts[0]
  if (view.fill === "parts" && first !== undefined) return { fill: { kind: "parts", parts: [first] }, marked }
  return { fill: { kind: "regular" }, marked }
}

/** The kinds of form a glyph has beyond regular: the tile's signs and its label. */
export type GlyphSignSet = { solid: boolean; mark: boolean; parts: number; steps: number; dotted: boolean }

export function signsOf(glyph: Glyph): GlyphSignSet {
  return { solid: glyph.carved.solid !== undefined, mark: hasMark(glyph), parts: glyph.parts.length, steps: glyph.steps, dotted: glyph.dotted !== null }
}

export function describeSigns(s: GlyphSignSet): string {
  const words = [s.solid && "solid", s.mark && "mark", s.parts > 0 && `${s.parts} parts`, s.steps > 0 && `${s.steps} ${s.steps === 1 ? "step" : "steps"}`, s.dotted && "dotted"].filter(
    (w): w is string => typeof w === "string",
  )
  return words.length === 0 ? "regular only" : words.join(", ")
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

/** review.json was written before the 2026-11-09 amendment: its `focus` is today's mark, its `mark` a step. */
const ROLE_NOW: Readonly<Record<string, string>> = { focus: "mark", mark: "step", body: "body" }
const roleNow = (role: string): string => ROLE_NOW[role] ?? role

/** A flag in plain words; paths are numbered from 1, as the roles map labels them. */
export function describeFlag(flag: ReviewFlag): string {
  switch (flag.kind) {
    case "added-dot":
      return `Path ${flag.path + 1} was added from the ${flag.from} twin as a hidden dot (area ${flag.area}): it is not drawn at rest, only lit.`
    case "redrawn":
      return `The ${flag.from} twin redraws the glyph (${flag.area} units of new area): its state cannot be derived from roles, so it needs an override or a redrawing.`
    case "conflict":
      return "path" in flag
        ? `Path ${flag.path + 1} is ${roleNow(flag.was)}, but another twin wanted it as ${roleNow(flag.wanted)}.`
        : `The ${flag.from} twin adds ${flag.area} units of its own, after another twin already took the ${flag.was} slot: one of the two is lost.`
  }
}

/** The flagged paths' indices, to pick them out in the roles map. */
export function flaggedPaths(name: string): Set<number> {
  return new Set(reviewFlags(name).flatMap((f) => ("path" in f ? [f.path] : [])))
}
