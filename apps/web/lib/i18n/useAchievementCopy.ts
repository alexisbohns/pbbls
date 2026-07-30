"use client"

import { useCallback } from "react"
import { useTranslations } from "next-intl"
import { useLocale } from "./useLocale"
import { EMOTIONS } from "@/lib/config/emotions"
import { DOMAINS } from "@/lib/config/domains"
import type { Achievement } from "@/lib/types"

export type AchievementCopy = { title: string; description: string }

/**
 * Resolve a badge's localized title & description (D7). Seeded rows carry no
 * database copy: both strings are composed from family-keyed i18n, with
 * `{threshold}` and — for emotion_first / domain_first — the reference
 * entity's localized display `{name}` interpolated. When a row carries admin
 * copy overrides (title_en/title_fr pairs), the current locale's override
 * wins; overrides are written in pairs by the admin RPC, so the cross-language
 * fallback is belt-and-braces only.
 */
export function useAchievementCopy(): (achievement: Achievement) => AchievementCopy {
  const { locale } = useLocale()
  // Untyped access because family and reference slugs are runtime DB values,
  // not part of the typed message tree (mirrors useReferenceCatalog).
  const t = useTranslations() as unknown as {
    (key: string, values?: Record<string, string | number>): string
    has(key: string): boolean
  }

  return useCallback(
    (achievement: Achievement): AchievementCopy => {
      const override =
        locale === "fr"
          ? {
              title: achievement.titleFr ?? achievement.titleEn,
              description: achievement.descriptionFr ?? achievement.descriptionEn,
            }
          : {
              title: achievement.titleEn ?? achievement.titleFr,
              description: achievement.descriptionEn ?? achievement.descriptionFr,
            }

      const values = {
        threshold: achievement.threshold ?? 0,
        name: referenceName(achievement, t),
      }
      return {
        title: override.title ?? t(`achievements.family.${achievement.family}.title`, values),
        description:
          override.description ??
          t(`achievements.family.${achievement.family}.description`, values),
      }
    },
    [locale, t],
  )
}

/**
 * The localized emotion/domain display name interpolated into the
 * emotion_first / domain_first patterns — resolved from the static reference
 * config by id, then localized by slug with the DB-name fallback contract of
 * useReferenceCatalog. Rows the config doesn't know yet (a server-side
 * addition before the client catches up) fall back to the achievement slug's
 * qualifier so the badge still renders something meaningful.
 */
function referenceName(
  achievement: Achievement,
  t: { (key: string): string; has(key: string): boolean },
): string {
  if (achievement.family === "emotion_first") {
    const emotion = EMOTIONS.find((e) => e.id === achievement.emotionId)
    if (emotion) {
      const key = `emotion.${emotion.slug}.name`
      return t.has(key) ? t(key) : emotion.name
    }
    return achievement.slug.replace(/^emotion-first-/, "")
  }
  if (achievement.family === "domain_first") {
    const domain = DOMAINS.find((d) => d.id === achievement.domainId)
    if (domain) {
      const key = `domain.${domain.slug}.name`
      return t.has(key) ? t(key) : domain.name
    }
    return achievement.slug.replace(/^domain-first-/, "")
  }
  return ""
}
