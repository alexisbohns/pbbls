"use client"

import { useTranslations } from "next-intl"
import { useAuth } from "@/lib/data/auth-context"
import { useKarma } from "@/lib/data/useKarma"
import { usePebblesCount } from "@/lib/data/usePebblesCount"
import { useProfileEngagement } from "@/lib/data/useProfileEngagement"
import { useRipple } from "@/lib/data/useRipple"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"
import { useLocale } from "@/lib/i18n"
import { YouAchievements } from "./YouAchievements"
import { YouCounters } from "./YouCounters"
import { YouGlyphs } from "./YouGlyphs"
import { YouHeader } from "./YouHeader"
import { YouLab } from "./YouLab"

const COLUMN = "mx-auto flex max-w-md flex-col gap-11 px-4 pt-[1.375rem]"

/**
 * You (#1384, spec D15–D22): the header, the counters, Glyphs, Achievements
 * and the Lab, in one reading column. No log out here: it lives in Settings
 * on every surface (D20).
 */
export function YouScreen() {
  const { user, profile, isAuthenticated, isLoading } = useAuth()
  const t = useTranslations("profile")
  const { locale } = useLocale()
  const { glyphs } = useUsableGlyphs()
  const { ripple } = useRipple()
  const { engagement, loading: engagementLoading, error: engagementError, retry } = useProfileEngagement()
  const { pebblesCount, loading: storeLoading } = usePebblesCount()
  const { karma } = useKarma()

  if (isLoading || !isAuthenticated || !user || !profile) {
    return (
      <div className={COLUMN}>
        <p className="pb-type-row-line text-m3-on-surface-variant">{isLoading ? t("loading") : t("signedOut")}</p>
      </div>
    )
  }

  const glyph = profile.glyph_id ? (glyphs.find((g) => g.id === profile.glyph_id) ?? null) : null
  const served = !engagementLoading && engagementError === null

  return (
    <div className={COLUMN}>
      <div className="flex flex-col gap-7">
        <YouHeader
          name={profile.display_name}
          memberSince={user.created_at}
          glyph={glyph}
          rippleLevel={ripple.level}
          activeToday={ripple.activeToday}
          locale={locale}
        />
        <YouCounters
          values={{
            days: served ? engagement.daysPracticed : null,
            pebbles: storeLoading ? null : pebblesCount,
            karma: storeLoading ? null : karma,
          }}
          locale={locale}
          failed={engagementError !== null}
          onRetry={retry}
        />
      </div>
      <YouGlyphs carved={served ? engagement.glyphsCarved : null} owned={served ? engagement.glyphsOwned : null} />
      <YouAchievements />
      <YouLab />
    </div>
  )
}
