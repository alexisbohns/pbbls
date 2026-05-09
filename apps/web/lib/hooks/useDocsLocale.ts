"use client"

import { useLocale } from "@/lib/i18n"
import type { DocsLocale } from "@/lib/docs/types"

/**
 * Backwards-compatible wrapper over the app-wide locale state.
 * Docs locales currently match the supported app locales; if that diverges,
 * narrow here.
 */
export function useDocsLocale() {
  const { locale, setLocale, locales } = useLocale()
  return {
    locale: locale as DocsLocale,
    setLocale: setLocale as (value: DocsLocale) => void,
    locales: locales as readonly DocsLocale[],
  }
}
