import { readFileSync } from "node:fs"
import { decodeAchievementsPage } from "@/lib/data/achievements-payload"
import { achievementPalettes, type AchievementPalettes } from "@/lib/achievements/palette"
import type { StonePalette } from "@/lib/config/stone-tones"
import type { SheetNames } from "@/lib/achievements/sheet-rows"
import { EMOTIONS } from "@/lib/config/emotions"
import type { AchievementsPage } from "@/lib/types"

// The canonical design fixture (#1308): the approved board's page in the shape
// get_achievements serves, shared byte for byte by every surface
// (packages/supabase/scripts/oracles/achievements/design/), with the real
// reference emotions and families (packages/supabase/reference/) for its
// palettes. Test-only: read from disk.

const SUPABASE = new URL("../../../../../packages/supabase/", import.meta.url)

function json(path: string): unknown {
  return JSON.parse(readFileSync(new URL(path, SUPABASE), "utf8"))
}

export function designPage(): AchievementsPage {
  const decoded = decodeAchievementsPage(json("scripts/oracles/achievements/design/design-fixture.json"))
  if (!decoded || decoded.skipped.length > 0) throw new Error("the design fixture did not decode whole")
  return decoded.page
}

function records(raw: unknown): Record<string, unknown>[] {
  if (!Array.isArray(raw)) return []
  return raw.filter((r): r is Record<string, unknown> => typeof r === "object" && r !== null && !Array.isArray(r))
}

const text = (v: unknown) => (typeof v === "string" ? v : "")

/** The reference palettes: every family the fixture names resolves, and every emotion. */
export function designPalettes(): AchievementPalettes {
  const families = new Map<string, StonePalette>(
    records(json("reference/emotion-categories.json")).map((r) => [
      text(r.slug),
      {
        primary_color: text(r.primary_color),
        secondary_color: text(r.secondary_color),
        light_color: text(r.light_color),
        shaded_color: text(r.shaded_color),
        dark_color: text(r.dark_color),
      },
    ]),
  )
  const emotions = new Map<string, StonePalette>()
  for (const r of records(json("reference/emotions.json"))) {
    const palette = families.get(text(r.category_slug))
    if (palette) emotions.set(text(r.id).toLowerCase(), palette)
  }
  return achievementPalettes(families, emotions)
}

/** Names as the page gives them, the fixture's domains from its own list (design/domains.json). */
export function designNames(): SheetNames {
  const domains = new Map(records(json("scripts/oracles/achievements/design/domains.json")).map((r) => [text(r.id), text(r.name)]))
  const families = new Map(records(json("reference/emotion-categories.json")).map((r) => [text(r.slug), text(r.name)]))
  return {
    emotion: (id) => EMOTIONS.find((e) => e.id === id)?.name ?? null,
    domain: (id) => (id === null ? null : (domains.get(id) ?? null)),
    family: (slug) => (slug === null ? null : (families.get(slug) ?? null)),
  }
}

