"use client"

import { useLabPreview } from "@/lib/data/useLab"
import { useLocale } from "@/lib/i18n"
import { YouLabView } from "./YouLabView"

/** You's Lab (#1384 D19), read through the Lab's own logs API. */
export function YouLab() {
  const { locale } = useLocale()
  const { announcement, changelog, error, retry } = useLabPreview()
  return <YouLabView announcement={announcement} changelog={changelog} locale={locale} failed={error !== null} onRetry={retry} />
}
