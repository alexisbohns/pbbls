export const SUPPORTED_LOCALES = ["en", "fr"] as const
export type Locale = (typeof SUPPORTED_LOCALES)[number]

export const DEFAULT_LOCALE: Locale = "en"

export const LOCALE_STORAGE_KEY = "pbbls-locale"

export const LOCALE_LABELS: Record<Locale, string> = {
  en: "English",
  fr: "Français",
}

export function isSupportedLocale(value: string | null | undefined): value is Locale {
  return !!value && (SUPPORTED_LOCALES as readonly string[]).includes(value)
}

export function detectBrowserLocale(): Locale {
  if (typeof navigator === "undefined") return DEFAULT_LOCALE
  const lang = navigator.language?.slice(0, 2).toLowerCase() ?? ""
  return isSupportedLocale(lang) ? lang : DEFAULT_LOCALE
}
