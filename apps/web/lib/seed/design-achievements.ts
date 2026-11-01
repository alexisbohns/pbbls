import { palettesFromReference } from "@/lib/achievements/palette"
import type { StonePalette } from "@/lib/config/stone-tones"
import { decodeAchievementsPage } from "@/lib/data/achievements-payload"
import fixture from "../../../../packages/supabase/scripts/oracles/achievements/design/design-fixture.json"
import categories from "../../../../packages/supabase/reference/emotion-categories.json"
import emotions from "../../../../packages/supabase/reference/emotions.json"

// The achievements' canonical design fixture for /sandbox/design (#1308): the
// approved board's page every surface draws, with the reference palettes the
// page builds from its reads. Network-free, never a source of truth.

export const DESIGN_ACHIEVEMENTS = decodeAchievementsPage(fixture)?.page ?? null

/** The reference palettes, as the page builds them from its reads: every family the fixture names resolves. */
export const DESIGN_ACHIEVEMENT_PALETTES = palettesFromReference(
  emotions,
  new Map(
    emotions.flatMap((e): [string, StonePalette][] => {
      const c = categories.find((x) => x.slug === e.category_slug)
      return c ? [[e.id, c]] : []
    }),
  ),
)
