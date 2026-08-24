/**
 * The eleven screens of the record flow, in order.
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
  "glyph",
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
  "glyph",
])

export function isOptionalStep(step: RecordStep): boolean {
  return OPTIONAL_STEPS.has(step)
}

export function stepIndex(step: RecordStep): number {
  return RECORD_STEPS.indexOf(step)
}

/** 0-based dot index, or null for the uncounted terminal step. */
export function dotIndex(step: RecordStep): number | null {
  return step === "success" ? null : stepIndex(step)
}

export function nextStep(step: RecordStep): RecordStep | null {
  return RECORD_STEPS[stepIndex(step) + 1] ?? null
}

export function previousStep(step: RecordStep): RecordStep | null {
  const index = stepIndex(step)
  return index <= 0 ? null : RECORD_STEPS[index - 1]
}
