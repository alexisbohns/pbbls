import { STATES, type PbblsGlyphState } from "../react/states"
import type { PbblsGlyphComponent } from "../react/types"
import { resolveWith, type Form } from "./forms"

export { DOTTED, MARKED, REGULAR, SOLID, drawForm, formKey, progress, type Form, type GlyphForms, type RenderedPath } from "./forms"
export { GlyphSvg, type GlyphSvgProps } from "./GlyphSvg"
export { formsOf, parseFormKey } from "../scripts/lib/forms"

/** The form a state shows on this glyph (spec D6): `glyphForm(Home, "selected")`; `k` is the caller's progress. */
export function glyphForm(glyph: Pick<PbblsGlyphComponent, "forms">, state: PbblsGlyphState, k = 0): Form {
  return resolveWith(STATES, glyph.forms, state, k)
}
