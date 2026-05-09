"use client"

import { Suspense, useEffect, useMemo } from "react"
import { useSearchParams } from "next/navigation"
import { NextIntlClientProvider } from "next-intl"
import { useDetectInitialLocale, useLocale } from "./useLocale"
import en from "./messages/en.json"
import fr from "./messages/fr.json"
import {
  LOCALE_STORAGE_KEY,
  isSupportedLocale,
  type Locale,
} from "./locales"

const MESSAGES: Record<Locale, Record<string, unknown>> = { en, fr }

const LOCALE_CHANGE_EVENT = "pbbls-locale-change"

function HtmlLangSync({ locale }: { locale: Locale }) {
  useEffect(() => {
    if (typeof document !== "undefined") {
      document.documentElement.lang = locale
    }
  }, [locale])
  return null
}

/**
 * Mirrors `?lang=fr` into localStorage so a shared link with the lang
 * override switches the visitor's app locale on first paint. Wrapped in
 * its own Suspense boundary because `useSearchParams` requires it under
 * static prerender; intentionally renders nothing so the boundary is
 * cheap and never blocks the surrounding tree.
 */
function LangParamSyncInner() {
  const searchParams = useSearchParams()
  const langParam = searchParams.get("lang")

  useEffect(() => {
    if (langParam && isSupportedLocale(langParam)) {
      localStorage.setItem(LOCALE_STORAGE_KEY, langParam)
      window.dispatchEvent(new Event(LOCALE_CHANGE_EVENT))
    }
  }, [langParam])

  return null
}

function LangParamSync() {
  return (
    <Suspense fallback={null}>
      <LangParamSyncInner />
    </Suspense>
  )
}

/**
 * App-wide locale layer. Resolves the active locale from `localStorage`
 * (with browser-language detection and `?lang=` override syncing into the
 * same store) and bridges next-intl's React provider.
 */
export function LocaleProvider({ children }: { children: React.ReactNode }) {
  const { locale } = useLocale()
  useDetectInitialLocale()
  const messages = useMemo(() => MESSAGES[locale], [locale])

  return (
    <NextIntlClientProvider locale={locale} messages={messages}>
      <HtmlLangSync locale={locale} />
      <LangParamSync />
      {children}
    </NextIntlClientProvider>
  )
}
