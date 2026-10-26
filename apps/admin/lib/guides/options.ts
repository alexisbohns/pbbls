import type { GuideIllustration } from "./types"

export const ILLUSTRATION_OPTIONS: ReadonlyArray<{ value: GuideIllustration; label: string }> = [
  { value: "pebbles", label: "Pebbles (a moment and its stone)" },
  { value: "stones", label: "Stones (3 × 3 seals by size and valence)" },
  { value: "emotions", label: "Emotions (the seven families)" },
  { value: "valence", label: "Valence (lava, river, gem)" },
  { value: "domains", label: "Domains" },
  { value: "collections", label: "Collections (stack, pack, track)" },
  { value: "souls", label: "Souls (glyph faces)" },
  { value: "glyphs", label: "Glyphs (a carved ring)" },
  { value: "karma", label: "Karma" },
]

const ILLUSTRATION_VALUES = ILLUSTRATION_OPTIONS.map((o) => o.value)

export function isGuideIllustration(value: string | undefined): value is GuideIllustration {
  return value !== undefined && (ILLUSTRATION_VALUES as readonly string[]).includes(value)
}
