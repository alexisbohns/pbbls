import type { Tags } from "./manifest"
import { partCombinations } from "./roles"

/** One path of a glyph in `glyphs.json`: normalised data and its role. A hidden path (mark or step) is not drawn until its role fills. */
export type GlyphPath = {
  d: string
  role?: "body" | "mark" | "step"
  /** The body part this path belongs to, when the body is split (spec D2). */
  part?: string
  /** A step's number, 1…n. */
  step?: number
  hidden?: true
  /** Mark or step with other lines inside it: the path filled and stroked, minus those lines outlined at the stroke (even-odd, unstroked). */
  fill?: string
  /** Indices into `paths` of the lines `fill` is carved through; they are not drawn when this path is filled. */
  cuts?: number[]
}

/** A path as a form draws it. */
export type RenderedPath = { d: string; fill: boolean; evenOdd: boolean; stroke: boolean }

/**
 * A solid or parts fill worked out at build time: the carved fill, the paths
 * drawn stroked on top of it (`keep`) and the mark paths drawn filled beside it
 * (`lit`: a mark outside the fill, under `marked`). Indices are into `paths`.
 */
export type CarvedFill = { d: string; keep: number[]; lit: number[] }

/** A glyph as `glyphs.json` stores it (spec §2): the contract every surface reads. */
export type GlyphData = {
  name: string
  meaning: string
  family: string | null
  tags: Tags
  paths: GlyphPath[]
  /** The body's parts in source order; empty when the body is whole. */
  parts: string[]
  /** The number of steps, n of progress(k). */
  steps: number
  /** Every solid and parts form, carved, by form key: `solid`, `solid-marked`, `parts-moon`, `parts-moon-marked`, … */
  carved: Record<string, CarvedFill>
  /** The drawn dotted form (`src/<name>.dotted.svg`), or null. */
  dotted: RenderedPath[] | null
  /** Hand-drawn replacements, keyed by form key (`marked`, `solid`, `progress-2`). */
  overrides: Record<string, RenderedPath[]>
}

/** What the form engine reads of a glyph: `GlyphData` without its manifest prose, as the components inline it. */
export type GlyphForms = Pick<GlyphData, "name" | "paths" | "parts" | "steps" | "carved" | "dotted" | "overrides">

export type Fill =
  | { kind: "regular" }
  | { kind: "solid" }
  | { kind: "parts"; parts: string[] }
  | { kind: "progress"; k: number }
  | { kind: "dotted" }

/** A form (spec D3): a fill, plus the mark when the glyph has one. */
export type Form = { fill: Fill; marked?: boolean }

export function fillKey(fill: Fill): string {
  switch (fill.kind) {
    case "parts":
      return `parts-${fill.parts.join("+")}`
    case "progress":
      return `progress-${fill.k}`
    default:
      return fill.kind
  }
}

/** `regular` marked is `marked`; any other fill takes a `-marked` suffix. */
export function formKey(form: Form): string {
  const fill = fillKey(form.fill)
  if (!form.marked) return fill
  return fill === "regular" ? "marked" : `${fill}-marked`
}

const KEY = /^(regular|marked|dotted|solid|parts-([a-z]+(?:\+[a-z]+)*)|progress-([1-9]\d*))(-marked)?$/

/** The form a key names, or null when it is not a form key. */
export function parseFormKey(key: string): Form | null {
  const m = KEY.exec(key)
  if (!m) return null
  const [, head, parts, k, marked] = m
  if (head === "marked") return marked ? null : { fill: { kind: "regular" }, marked: true }
  if ((head === "regular" || head === "dotted") && marked) return null
  let fill: Fill
  if (parts !== undefined) fill = { kind: "parts", parts: parts.split("+") }
  else if (k !== undefined) fill = { kind: "progress", k: Number(k) }
  else if (head === "solid") fill = { kind: "solid" }
  else if (head === "dotted") fill = { kind: "dotted" }
  else fill = { kind: "regular" }
  return marked ? { fill, marked: true } : { fill }
}

/** What decides a glyph's forms. */
export type FormShape = { mark: boolean; body: boolean; parts: readonly string[]; steps: number; dotted: boolean }

/** The forms a glyph has, as keys, in a stable order (spec D3): each fill then the same fill marked, dotted after regular. */
export function formKeysOf(shape: FormShape): string[] {
  const fills: Fill[] = [{ kind: "regular" }]
  if (shape.body) fills.push({ kind: "solid" })
  for (const parts of partCombinations(shape.parts)) fills.push({ kind: "parts", parts })
  for (let k = 1; k <= shape.steps; k += 1) fills.push({ kind: "progress", k })
  const keys = fills.flatMap((fill) => (shape.mark ? [formKey({ fill }), formKey({ fill, marked: true })] : [formKey({ fill })]))
  if (shape.dotted) keys.splice(shape.mark ? 2 : 1, 0, "dotted")
  return keys
}

export function shapeOf(glyph: GlyphForms): FormShape {
  return {
    mark: glyph.paths.some((p) => p.role === "mark"),
    body: glyph.paths.some((p) => p.role === "body"),
    parts: glyph.parts,
    steps: glyph.steps,
    dotted: glyph.dotted !== null,
  }
}

/** The forms a glyph has, as keys (spec D3). */
export function formsOf(glyph: GlyphForms): string[] {
  return formKeysOf(shapeOf(glyph))
}

const stroked = (d: string): RenderedPath => ({ d, fill: false, evenOdd: false, stroke: true })
const filled = (d: string): RenderedPath => ({ d, fill: true, evenOdd: false, stroke: true })
const carvedPath = (d: string): RenderedPath => ({ d, fill: true, evenOdd: true, stroke: false })
/** A filled mark or step: its carved fill when lines run inside it. */
const lit = (p: GlyphPath): RenderedPath => (p.fill !== undefined ? carvedPath(p.fill) : filled(p.d))

/** The paths a form draws, overrides first (spec D3, D4). `progress(0)` is regular. */
export function renderForm(glyph: GlyphForms, form: Form): RenderedPath[] {
  if (form.fill.kind === "progress" && form.fill.k === 0) return renderForm(glyph, { fill: { kind: "regular" }, marked: form.marked })
  const key = formKey(form)
  if (!formsOf(glyph).includes(key)) throw new Error(`${glyph.name} has no ${key} form`)
  const override = glyph.overrides[key]
  if (override) return override
  if (form.fill.kind === "dotted") return glyph.dotted ?? []
  if (form.fill.kind === "solid" || form.fill.kind === "parts") {
    const carved = glyph.carved[key]
    if (!carved) throw new Error(`${glyph.name}: no carved ${key}`)
    return [
      carvedPath(carved.d),
      ...carved.keep.flatMap((i) => {
        const p = glyph.paths[i]
        return p ? [stroked(p.d)] : []
      }),
      ...carved.lit.flatMap((i) => {
        const p = glyph.paths[i]
        return p ? [lit(p)] : []
      }),
    ]
  }
  const k = form.fill.kind === "progress" ? form.fill.k : 0
  // A hidden path is drawn only when its role fills it; everything else is stroked.
  // A filled path with a carved `fill` draws that instead, and the lines it is carved through are left out.
  const fills = (p: GlyphPath) => (form.marked === true && p.role === "mark") || (p.role === "step" && (p.step ?? 0) <= k)
  const cut = new Set(glyph.paths.flatMap((p) => (fills(p) && p.fill !== undefined ? (p.cuts ?? []) : [])))
  return glyph.paths.flatMap((p, i) => {
    const on = fills(p)
    if ((p.hidden && !on) || cut.has(i)) return []
    return [on ? lit(p) : stroked(p.d)]
  })
}
