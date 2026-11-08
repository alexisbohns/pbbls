import type { Tags } from "./manifest"

/** One path of a glyph in `glyphs.json`: normalised data and its role. A hidden path (mark or focus) is not drawn until its role fills. */
export type GlyphPath = { d: string; role?: "body" | "mark" | "focus"; mark?: number; hidden?: true }

/** A path as a form draws it. */
export type RenderedPath = { d: string; fill: boolean; evenOdd: boolean; stroke: boolean }

/** A glyph as `glyphs.json` stores it (spec §2): the contract every surface reads. */
export type GlyphData = {
  name: string
  meaning: string
  family: string | null
  tags: Tags
  paths: GlyphPath[]
  /** The number of marks, n of progress(k). */
  marks: number
  /** The carved solid and the paths kept stroked on top (`keep` indexes the non-body, non-hidden paths), or null without a body. */
  solid: { d: string; keep: number[] } | null
  /** Hand-drawn replacements, keyed by form (`half`, `solid`, `progress-2`). */
  overrides: Record<string, RenderedPath[]>
}

export type Form = { kind: "regular" } | { kind: "half" } | { kind: "solid" } | { kind: "progress"; k: number }

export function formKey(form: Form): string {
  return form.kind === "progress" ? `progress-${form.k}` : form.kind
}

/** The forms a glyph has, in a stable order (spec D3). */
export function formsOf(glyph: GlyphData): string[] {
  const forms = ["regular"]
  if (glyph.paths.some((p) => p.role === "focus")) forms.push("half")
  if (glyph.solid) forms.push("solid")
  for (let k = 1; k <= glyph.marks; k += 1) forms.push(`progress-${k}`)
  return forms
}

const stroked = (d: string): RenderedPath => ({ d, fill: false, evenOdd: false, stroke: true })
const filled = (d: string): RenderedPath => ({ d, fill: true, evenOdd: false, stroke: true })

/** The paths a form draws, overrides first (spec D3, D4). `progress(0)` is regular. */
export function renderForm(glyph: GlyphData, form: Form): RenderedPath[] {
  if (form.kind === "progress" && form.k === 0) return renderForm(glyph, { kind: "regular" })
  const key = formKey(form)
  if (!formsOf(glyph).includes(key)) throw new Error(`${glyph.name} has no ${key} form`)
  const override = glyph.overrides[key]
  if (override) return override
  // A hidden path is drawn only when its role fills it; everything else is stroked.
  const draw = (fills: (p: GlyphPath) => boolean): RenderedPath[] =>
    glyph.paths.flatMap((p) => {
      const on = fills(p)
      if (p.hidden && !on) return []
      return [on ? filled(p.d) : stroked(p.d)]
    })
  switch (form.kind) {
    case "regular":
      return draw(() => false)
    case "half":
      return draw((p) => p.role === "focus")
    case "progress":
      return draw((p) => p.role === "mark" && (p.mark ?? 0) <= form.k)
    case "solid": {
      const solid = glyph.solid
      if (!solid) throw new Error(`${glyph.name} has no solid form`)
      const others = glyph.paths.filter((p) => p.role !== "body" && !p.hidden)
      return [{ d: solid.d, fill: true, evenOdd: true, stroke: false }, ...solid.keep.flatMap((i) => (others[i] ? [stroked(others[i].d)] : []))]
    }
  }
}
