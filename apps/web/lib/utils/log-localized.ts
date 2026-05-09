import type { Log } from "@/lib/types"

// Picks the FR translation when locale prefers French and one exists, otherwise
// falls back to EN. Mirrors `Log.title(for:)` / `summary(for:)` / `body(for:)` on iOS.
// Locale is wired through but every caller currently passes "en" — paired with the
// i18n issue, callers will start passing the user's preferred locale.
export function pickLang(en: string, fr: string | null, locale: string): string
export function pickLang(en: string | null, fr: string | null, locale: string): string | null
export function pickLang(en: string | null, fr: string | null, locale: string): string | null {
  if (locale.startsWith("fr") && fr) return fr
  return en
}

export function logTitle(log: Log, locale: string): string {
  return pickLang(log.title_en, log.title_fr, locale)
}

export function logSummary(log: Log, locale: string): string {
  return pickLang(log.summary_en, log.summary_fr, locale)
}

export function logBody(log: Log, locale: string): string | null {
  return pickLang(log.body_md_en, log.body_md_fr, locale)
}
