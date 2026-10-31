import type { StonePalette } from "@/lib/config/stone-tones"

// Colour is a lookup, never a decision (#1308, spec §4): a family slug or an
// emotion id → its palette from the reference data. Anything this build cannot
// resolve is null and draws in ink. iOS `WrapLookups`, Android `AchievementLookups`.

export type AchievementPalettes = {
  family: (slug: string | null) => StonePalette | null
  emotion: (id: string | null) => StonePalette | null
}

export function achievementPalettes(
  families: ReadonlyMap<string, StonePalette>,
  emotions: ReadonlyMap<string, StonePalette>,
): AchievementPalettes {
  return {
    family: (slug) => (slug === null ? null : (families.get(slug) ?? null)),
    emotion: (id) => (id === null ? null : (emotions.get(id.toLowerCase()) ?? null)),
  }
}

/** No palette at all: every figure draws in ink. */
export const NO_PALETTES: AchievementPalettes = { family: () => null, emotion: () => null }
