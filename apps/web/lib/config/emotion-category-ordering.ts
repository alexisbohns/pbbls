// Static curation: the order in which the 7 emotion categories surface in
// `EmotionPickerSheet`, keyed by the form's currently-selected
// (intensity, valence) cell.
//
// Ported from `apps/ios/Pebbles/Features/Path/Models/EmotionCategoryOrdering.swift`
// (PR #382). Web's `intensity` maps to iOS `sizeGroup` and `valence` to
// `polarity`:
//   intensity 1 → small,  2 → medium, 3 → large
//   valence  -1 → lowlight, 0 → neutral, 1 → highlight
//
// Ordering rule (informally): own polarity first, opposite polarity last.
// Within each polarity, the "peak" member leads at LARGE, the most "subtle"
// member leads at SMALL, balanced at MEDIUM.

export type Intensity = 1 | 2 | 3
export type Valence = -1 | 0 | 1

type Size = "small" | "medium" | "large"
type Polarity = "lowlight" | "neutral" | "highlight"

const SIZE: Record<Intensity, Size> = { 1: "small", 2: "medium", 3: "large" }
const POLARITY: Record<Valence, Polarity> = {
  [-1]: "lowlight",
  0: "neutral",
  1: "highlight",
}

const TABLE: Record<`${Size}.${Polarity}`, readonly string[]> = {
  // HIGHLIGHTS — pleasant first
  "large.highlight":  ["pride",   "joy",     "peace", "fear",  "anger", "shame",   "sadness"],
  "medium.highlight": ["joy",     "pride",   "peace", "fear",  "anger", "shame",   "sadness"],
  "small.highlight":  ["peace",   "joy",     "pride", "shame", "sadness", "fear",  "anger"],

  // NEUTRALS — balanced, peace leads
  "large.neutral":    ["peace",   "joy",     "pride", "fear",  "anger", "shame",   "sadness"],
  "medium.neutral":   ["peace",   "fear",    "joy",   "anger", "pride", "shame",   "sadness"],
  "small.neutral":    ["peace",   "anger",   "joy",   "fear",  "pride", "sadness", "shame"],

  // LOWLIGHTS — unpleasant first
  "large.lowlight":   ["sadness", "fear",    "anger", "shame", "peace", "joy",     "pride"],
  "medium.lowlight":  ["anger",   "fear",    "shame", "sadness", "peace", "pride", "joy"],
  "small.lowlight":   ["shame",   "sadness", "fear",  "anger", "peace", "pride",   "joy"],
}

// Used when no valence/intensity context is available. Equal to medium neutral.
export const DEFAULT_CATEGORY_ORDER: readonly string[] = TABLE["medium.neutral"]

export function emotionCategoryOrder(
  intensity: Intensity | undefined,
  valence: Valence | undefined,
): readonly string[] {
  if (intensity === undefined || valence === undefined) return DEFAULT_CATEGORY_ORDER
  return TABLE[`${SIZE[intensity]}.${POLARITY[valence]}`] ?? DEFAULT_CATEGORY_ORDER
}
