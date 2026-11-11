/**
 * The form engine as the components use it (spec D3, D6): the generator's own
 * `renderForm` and `resolveState`, with a missing form drawing regular as it
 * does on iOS and Android. Browser-safe: nothing here reads a file.
 */
import { formKey, renderForm, type Form, type GlyphForms, type RenderedPath } from "../scripts/lib/forms"
import { resolveState, type StateTable } from "../scripts/lib/resolve-state"

export type { Form, GlyphForms, RenderedPath }

export const REGULAR: Form = { fill: { kind: "regular" } }
export const MARKED: Form = { fill: { kind: "regular" }, marked: true }
export const SOLID: Form = { fill: { kind: "solid" } }
export const DOTTED: Form = { fill: { kind: "dotted" } }
export const progress = (k: number): Form => ({ fill: { kind: "progress", k } })

/** The paths a form draws; a form the glyph does not have draws regular. */
export function drawForm(glyph: GlyphForms, form: Form): RenderedPath[] {
  try {
    return renderForm(glyph, form)
  } catch {
    return renderForm(glyph, REGULAR)
  }
}

/** The form `state` shows on a glyph with these form keys, against a state table; an unknown state shows regular. */
export function resolveWith(table: StateTable, forms: readonly string[], state: string, k = 0): Form {
  return table[state] ? resolveState(table, forms, state, k) : REGULAR
}

export { formKey }
