"use client"

import { useCallback } from "react"
import { useTranslations } from "next-intl"

/**
 * An emotion family's display name (#1308): `emotionCategory.<slug>.name`, or
 * the served slug for a family this catalog does not know yet (as Android's
 * `ReferenceStrings` and iOS's lookups fall back). Untyped access because the
 * slug is a runtime value, as in `useReferenceCatalog`.
 */
export function useFamilyName(): (slug: string) => string {
  const t = useTranslations() as unknown as {
    (key: string): string
    has(key: string): boolean
  }
  return useCallback(
    (slug: string) => {
      const key = `emotionCategory.${slug}.name`
      return t.has(key) ? t(key) : slug
    },
    [t],
  )
}
