"use client"

import { GlyphSvg } from "@pbbls/icons/runtime"
import type { Form, Glyph } from "@/lib/design/glyph-forms"

/**
 * One glyph in one form for the design page's review: the runtime's `GlyphSvg`,
 * the element every component draws, so the page shows what ships. A form the
 * glyph lacks draws regular; a path that starts filling fades in (`animate`).
 */
export function GlyphFormView({ glyph, form, className }: { glyph: Glyph; form: Form; className?: string }) {
  return <GlyphSvg glyph={glyph} form={form} animate className={className} />
}
