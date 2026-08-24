"use client"

import { GlyphPickerTabs } from "@/components/record/GlyphPickerTabs"

type RecordGlyphStepProps = {
  selected: string | undefined
  onSelect: (id: string | undefined) => void
}

/**
 * Step 8 — the glyph, skippable.
 *
 * Renders the picker's tab bar inline, which brings the inline buy with it.
 * `BuyGlyphDialog` closes itself on a successful purchase rather than relying
 * on the picker sheet's dismissal, so it does not hang over the next step the
 * way iOS's glyph drawer did — checked, not assumed.
 *
 * Carving stays where it is: it is a full modal task with its own canvas, and
 * flattening it into a step would be a second wizard nested inside the first.
 */
export function RecordGlyphStep({ selected, onSelect }: RecordGlyphStepProps) {
  return <GlyphPickerTabs selectedMarkId={selected} onSelect={onSelect} />
}
