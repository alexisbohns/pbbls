"use client"

import { Suspense, useEffect, useMemo } from "react"
import { NextIntlClientProvider } from "next-intl"
import { useLocale } from "./useLocale"
import en from "./messages/en.json"
import fr from "./messages/fr.json"
import { DEFAULT_LOCALE, type Locale } from "./locales"

function HtmlLangSync({ locale }: { locale: Locale }) {
  useEffect(() => {
    if (typeof document !== "undefined") {
      document.documentElement.lang = locale
    }
  }, [locale])
  return null
}

const MESSAGES: Record<Locale, Record<string, unknown>> = { en, fr }

function LocaleProviderInner({ children }: { children: React.ReactNode }) {
  const { locale } = useLocale()
  const messages = useMemo(() => MESSAGES[locale], [locale])

  return (
    <NextIntlClientProvider locale={locale} messages={messages}>
      <HtmlLangSync locale={locale} />
      {children}
    </NextIntlClientProvider>
  )
}

/**
 * App-wide locale layer. Resolves the active locale on the client (search
 * params > stored preference > browser > EN) and bridges next-intl's React
 * provider. Wrapped in Suspense because `useSearchParams` requires it in the
 * Next.js App Router.
 */
export function LocaleProvider({ children }: { children: React.ReactNode }) {
  return (
    <Suspense
      fallback={
        <NextIntlClientProvider locale={DEFAULT_LOCALE} messages={MESSAGES[DEFAULT_LOCALE]}>
          {children}
        </NextIntlClientProvider>
      }
    >
      <LocaleProviderInner>{children}</LocaleProviderInner>
    </Suspense>
  )
}
