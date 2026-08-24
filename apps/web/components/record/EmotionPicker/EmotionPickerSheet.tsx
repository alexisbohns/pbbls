"use client"

import { useTranslations } from "next-intl"
import { PickerSheet } from "@/components/ui/PickerSheet"
import type { Intensity, Valence } from "@/lib/config/emotion-category-ordering"
import { EmotionPickerContent } from "./EmotionPickerContent"

type EmotionPickerSheetProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Current emotion_id from the form. */
  value: string | undefined
  /** Drives section order (mirrors iOS valence-based ordering). */
  intensity?: Intensity
  valence?: Valence
  /**
   * Called when the user picks (or clears) an emotion. Receives `undefined`
   * when the user taps the currently-selected chip to deselect.
   */
  onChange: (id: string | undefined) => void
}

/**
 * Two-level emotion picker presented as a Sheet.
 *
 * The grid itself is `EmotionPickerContent`, shared with the record flow's
 * emotion step. What stays here is the sheet's own commit semantics: tapping a
 * chip commits and dismisses, tapping the currently-selected chip clears the
 * value, and the close (X) button dismisses without committing any change.
 */
export function EmotionPickerSheet({
  open,
  onOpenChange,
  value,
  intensity,
  valence,
  onChange,
}: EmotionPickerSheetProps) {
  const t = useTranslations("record.emotionPicker")

  const handleChipSelect = (id: string) => {
    onChange(id === value ? undefined : id)
    onOpenChange(false)
  }

  return (
    <PickerSheet
      open={open}
      onOpenChange={onOpenChange}
      title={t("title")}
      closeLabel={t("close")}
    >
      <EmotionPickerContent
        selected={value}
        intensity={intensity}
        valence={valence}
        onSelect={handleChipSelect}
      />
    </PickerSheet>
  )
}
