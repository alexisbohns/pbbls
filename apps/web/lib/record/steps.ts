/**
 * The Complete flow's screens (#1050): a moment becomes the caller's pebble.
 * Ported from `apps/ios/Pebbles/Features/Complete/CompleteStep.swift`. The
 * moment's photo, date and name were captured already, so the flow starts at
 * valence, which still precedes emotion for the category ordering. No glyph
 * step (D5). `success` is terminal.
 */
export const COMPLETE_STEPS = [
  "valence",
  "emotion",
  "domain",
  "souls",
  "collection",
  "privacy",
  "success",
] as const

export type CompleteStep = (typeof COMPLETE_STEPS)[number]

/** The Complete steps the progress dots represent — everything but `success`. */
export const COMPLETE_COUNTED_STEPS: readonly CompleteStep[] = COMPLETE_STEPS.filter(
  (step) => step !== "success",
)

// ---------------------------------------------------------------------------
// Order helpers over any step list, so the Capture and Complete flows share one
// chrome and one history hook. Capture's list lives with its reducer
// (`lib/record/capture.ts`).
// ---------------------------------------------------------------------------

export function stepIndexIn<S extends string>(steps: readonly S[], step: S): number {
  return steps.indexOf(step)
}

export function nextIn<S extends string>(steps: readonly S[], step: S): S | null {
  return steps[stepIndexIn(steps, step) + 1] ?? null
}

export function previousIn<S extends string>(steps: readonly S[], step: S): S | null {
  const index = stepIndexIn(steps, step)
  return index <= 0 ? null : steps[index - 1]
}
