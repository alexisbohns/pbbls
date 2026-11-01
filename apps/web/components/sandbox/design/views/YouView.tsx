"use client"

import { M3Island } from "@/components/layout/M3Island"
import { YouAchievementsList } from "@/components/you/YouAchievementsList"
import { YouCounters } from "@/components/you/YouCounters"
import { YouGlyphs } from "@/components/you/YouGlyphs"
import { YouHeader } from "@/components/you/YouHeader"
import { YouLabView } from "@/components/you/YouLabView"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"
import { useLocale } from "@/lib/i18n"
import { DESIGN_ACCOUNT, DESIGN_PROFILE, DESIGN_RIPPLE } from "@/lib/seed/design-fixtures"
import { DESIGN_LAB_ANNOUNCEMENT, DESIGN_LAB_CHANGELOG, DESIGN_YOU_BADGES, DESIGN_YOU_COUNTS } from "@/lib/seed/design-you"

/** app/profile's tree since #1384 (You), in its island, with fixtures where You reads the network. */
export function YouView() {
  const { locale } = useLocale()
  const { glyphs } = useUsableGlyphs()
  const glyph = glyphs.find((g) => g.id === DESIGN_PROFILE.glyph_id) ?? null
  const { days, pebbles, karma, glyphsCarved, glyphsOwned } = DESIGN_YOU_COUNTS
  return (
    <M3Island className="pb-12">
      <div className="mx-auto flex max-w-md flex-col gap-11 px-4 pt-[1.375rem]">
        <div className="flex flex-col gap-7">
          <YouHeader
            name={DESIGN_PROFILE.display_name}
            memberSince={DESIGN_ACCOUNT.created_at}
            glyph={glyph}
            rippleLevel={DESIGN_RIPPLE.level}
            activeToday={DESIGN_RIPPLE.activeToday}
            locale={locale}
          />
          <YouCounters values={{ days, pebbles, karma }} locale={locale} />
        </div>
        <YouGlyphs carved={glyphsCarved} owned={glyphsOwned} />
        <YouAchievementsList badges={DESIGN_YOU_BADGES} count={12} />
        <YouLabView announcement={DESIGN_LAB_ANNOUNCEMENT} changelog={DESIGN_LAB_CHANGELOG} locale={locale} />
      </div>
    </M3Island>
  )
}
