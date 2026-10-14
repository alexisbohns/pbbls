/**
 * The ten screens of the record flow, in order.
 *
 * Ported from `apps/ios/Pebbles/Features/Record/RecordStep.swift` (M58, #724).
 * The order carries three deliberate dependencies, and they are the reason the
 * flow exists at all — a single-screen form structurally cannot have them:
 *
 * - `photo` before `when`, so the date step arrives pre-filled from the photo's
 *   EXIF `DateTimeOriginal` instead of mutating under the user.
 * - `valence` before `emotion`, so `emotionCategoryOrder` has an
 *   (intensity, valence) cell to order the categories by. In the form the two
 *   controls sit side by side and the ordering is a lucky accident of which one
 *   the user opened first; here it is guaranteed.
 * - `privacy` last, against the publish button, because the grade is the
 *   decision most coupled to "am I ready for other people to see this".
 *
 * `success` is terminal: no dot, no back, no close — only the exit button.
 *
 * There is no glyph step: since the moments split (#1046, D5) a pebble's glyph
 * is derived from its collection, else its domain, and is never chosen.
 */
export const RECORD_STEPS = [
  "photo",
  "when",
  "name",
  "valence",
  "emotion",
  "domain",
  "souls",
  "collection",
  "privacy",
  "success",
] as const

export type RecordStep = (typeof RECORD_STEPS)[number]

/** The steps the progress dots represent — everything but the terminal one. */
export const COUNTED_STEPS: readonly RecordStep[] = RECORD_STEPS.filter(
  (step) => step !== "success",
)

/**
 * Steps the user may pass without answering. Everything else gates.
 *
 * Collection is optional-and-multi-select on web, where the composer has always
 * allowed a pebble in several collections; see `RecordCollectionStep`.
 */
const OPTIONAL_STEPS: ReadonlySet<RecordStep> = new Set<RecordStep>([
  "photo",
  "souls",
  "collection",
])

export function isOptionalStep(step: RecordStep): boolean {
  return OPTIONAL_STEPS.has(step)
}

export function stepIndex(step: RecordStep): number {
  return stepIndexIn(RECORD_STEPS, step)
}

/** 0-based dot index, or null for the uncounted terminal step. */
export function dotIndex(step: RecordStep): number | null {
  return step === "success" ? null : stepIndex(step)
}

export function nextStep(step: RecordStep): RecordStep | null {
  return nextIn(RECORD_STEPS, step)
}

export function previousStep(step: RecordStep): RecordStep | null {
  return previousIn(RECORD_STEPS, step)
}

/**
 * The Complete flow's screens (#1050): a moment becomes the caller's pebble.
 * Ported from `apps/ios/Pebbles/Features/Complete/CompleteStep.swift`. The
 * moment's photo, date and name were captured already, so the flow starts at
 * valence, which still precedes emotion for the category ordering. No glyph
 * step (D5). `success` is terminal, as in the record flow.
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
// Order helpers over any step list, so the record and Complete flows share one
// chrome and one history hook.
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
