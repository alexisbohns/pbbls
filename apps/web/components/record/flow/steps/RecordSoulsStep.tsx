"use client"

import { useSouls } from "@/lib/data/useSouls"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"
import { SoulPickerContent } from "@/components/record/SoulPickerContent"

type RecordSoulsStepProps = {
  selectedIds: string[]
  onToggle: (id: string) => void
}

/**
 * Step 6 — who was there. Multi-select, so a tap never advances; the step's
 * Skip / Done button does.
 *
 * Souls stay multi-select on purpose: making the step single-select so every
 * step of the flow could advance on tap would be a real capability loss against
 * the composer, not a simplification.
 */
export function RecordSoulsStep({ selectedIds, onToggle }: RecordSoulsStepProps) {
  const { souls, addSoul } = useSouls()
  // Same list the sheet uses, so a soul's glyph thumbnail resolves identically
  // in both composers.
  const { glyphs: marks } = useUsableGlyphs()

  const handleAddSoul = async (name: string) => {
    const soul = await addSoul({ name })
    // Select it immediately — the user created it *for* this pebble, so making
    // them tap it again is friction.
    onToggle(soul.id)
  }

  return (
    <SoulPickerContent
      souls={souls}
      marks={marks}
      selectedIds={selectedIds}
      onToggle={onToggle}
      onAddSoul={handleAddSoul}
    />
  )
}
