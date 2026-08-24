"use client"

import { ValenceGrid } from "@/components/record/ValenceIntensityGrid"

type RecordValenceStepProps = {
  intensity: 1 | 2 | 3
  valence: -1 | 0 | 1
  onSelect: (intensity: 1 | 2 | 3, valence: -1 | 0 | 1) => void
}

/**
 * Step 3 — how big and how bright.
 *
 * Unlike the other tile steps this one does not advance on pick: the grid is a
 * comparison of nine cells, and a tap that leaves the screen denies the user
 * the look at what they just chose next to the eight they did not. Continue
 * does the advancing. The step always arrives answered — web's grid has no
 * empty state, so the centre cell is the starting point rather than a gap.
 */
export function RecordValenceStep({ intensity, valence, onSelect }: RecordValenceStepProps) {
  return <ValenceGrid intensity={intensity} valence={valence} onSelect={onSelect} />
}
