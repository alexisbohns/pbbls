"use client"

import { useMemo } from "react"
import { useTranslations } from "next-intl"
import type { SheetNames } from "@/lib/achievements/sheet-rows"
import { DOMAINS } from "@/lib/config/domains"
import { EMOTIONS } from "@/lib/config/emotions"

/**
 * The achievements sheets' names (#1308): an emotion and a domain by id from
 * the static configs, localized by slug with the config's name as the
 * fallback (useReferenceCatalog's contract), and a family by slug. One this
 * build does not know is null, and its row keeps its place under a fallback.
 */
export function useSheetNames(): SheetNames {
  // Untyped: slugs are runtime values, not part of the typed message tree.
  const t = useTranslations() as unknown as { (key: string): string; has(key: string): boolean }
  return useMemo(() => {
    const localized = (key: string, fallback: string) => (t.has(key) ? t(key) : fallback)
    return {
      emotion: (id) => {
        const e = id === null ? undefined : EMOTIONS.find((x) => x.id === id.toLowerCase())
        return e ? localized(`emotion.${e.slug}.name`, e.name) : null
      },
      domain: (id) => {
        const d = id === null ? undefined : DOMAINS.find((x) => x.id === id.toLowerCase())
        return d ? localized(`domain.${d.slug}.name`, d.name) : null
      },
      family: (slug) => (slug !== null && t.has(`emotionCategory.${slug}.name`) ? t(`emotionCategory.${slug}.name`) : null),
    }
  }, [t])
}
