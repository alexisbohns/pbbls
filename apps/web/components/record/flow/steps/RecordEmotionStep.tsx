"use client"

import { EmotionPickerContent } from "@/components/record/EmotionPicker"

type RecordEmotionStepProps = {
  selected: string | undefined
  intensity: 1 | 2 | 3
  valence: -1 | 0 | 1
  onSelect: (id: string) => void
}

/**
 * Step 4 — the emotion. Categories arrive ordered by the cell chosen on step 3,
 * which is the reason valence comes first.
 *
 * Unlike `EmotionPickerSheet` there is no toggle-to-clear: a step that advances
 * on tap cannot be cancelled, so the tap is the commit.
 */
export function RecordEmotionStep({
  selected,
  intensity,
  valence,
  onSelect,
}: RecordEmotionStepProps) {
  return (
    <EmotionPickerContent
      selected={selected}
      intensity={intensity}
      valence={valence}
      onSelect={onSelect}
    />
  )
}
