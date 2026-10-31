"use client"

import { useMemo, useState } from "react"
import { AchievementsPageContent } from "@/components/achievements/page/AchievementsPageContent"
import { Button } from "@/components/ui/button"
import { palettesFromReference } from "@/lib/achievements/palette"
import type { StonePalette } from "@/lib/config/stone-tones"
import { decodeAchievementsPage } from "@/lib/data/achievements-payload"
import type { AchievementsPage } from "@/lib/types"
import fixture from "../../../../../packages/supabase/scripts/oracles/achievements/design/design-fixture.json"
import categories from "../../../../../packages/supabase/reference/emotion-categories.json"
import emotions from "../../../../../packages/supabase/reference/emotions.json"
import { Specimen } from "./Specimen"

const DESIGN = decodeAchievementsPage(fixture)?.page ?? null

/** The reference palettes, as the page builds them from its reads: every family the fixture names resolves. */
const PALETTES = palettesFromReference(
  emotions,
  new Map(
    emotions.flatMap((e): [string, StonePalette][] => {
      const c = categories.find((x) => x.slug === e.category_slug)
      return c ? [[e.id, c]] : []
    }),
  ),
)

/** The design fixture with every set's completion ready to pull. */
function ready(page: AchievementsPage): AchievementsPage {
  const offer = { completedAt: null, offeredFrom: null, ready: true, pulledAt: null, pebble: null }
  return {
    ...page,
    explorer: page.explorer && { ...page.explorer, completion: { stack: "six_firsts", ...offer } },
    emotions: page.emotions && { ...page.emotions, completion: { stack: "emotions", ...offer } },
  }
}

/**
 * The achievements page's content (#1308) drawn from the canonical design
 * fixture every surface draws (packages/supabase/scripts/oracles/achievements/
 * design/), with the reference palettes. The seals draw from the `StoneSprite`
 * StoneSpecimens renders on this page.
 */
export function AchievementSpecimens() {
  const [offered, setOffered] = useState(false)
  const [said, setSaid] = useState<string | null>(null)
  const page = useMemo(() => (DESIGN && offered ? ready(DESIGN) : DESIGN), [offered])
  return (
    <Specimen id="achievement-cards" note="Rendered from the canonical design fixture; the cards open no sheet here, and a ready mark only says what it would pull.">
      <div className="flex w-full flex-col gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => setOffered((o) => !o)}>
            {offered ? "As served" : "With ceremonies ready"}
          </Button>
          {said ? <span className="text-sm text-muted-foreground">{said}</span> : null}
        </div>
        {page ? (
          <div className="w-full max-w-3xl rounded-xl bg-spine-ground p-3 font-spine text-spine-ink">
            <AchievementsPageContent page={page} palettes={PALETTES} onPull={(stack) => setSaid(`Pulls: ${stack}`)} />
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">The design fixture did not decode.</p>
        )}
      </div>
    </Specimen>
  )
}
