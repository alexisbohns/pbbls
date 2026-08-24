"use client"

import { ValenceFanPicker } from "@/components/record/valence/ValenceFanPicker"

type RecordValenceStepProps = {
  intensity: 1 | 2 | 3
  valence: -1 | 0 | 1
  onSelect: (intensity: 1 | 2 | 3, valence: -1 | 0 | 1) => void
}

/**
 * Step 3 — how big and how bright.
 *
 * A fan of the nine real stones with a swipeable lockup underneath, aligned
 * with iOS (#728 / handoff #729). Tap a stone or roll the lockup; either way
 * the pick commits in place and Continue advances, because the fan is a
 * comparison and a tap that leaves the screen denies the user the look at what
 * they just chose next to the eight they did not. The step always arrives
 * answered — the composer's defaults park it on neutral-medium, so the roll has
 * something under the finger from the first frame.
 */
export function RecordValenceStep({ intensity, valence, onSelect }: RecordValenceStepProps) {
  return <ValenceFanPicker intensity={intensity} valence={valence} onSelect={onSelect} />
}
