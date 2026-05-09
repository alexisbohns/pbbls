"use client"

import { useCallback, useEffect, useSyncExternalStore } from "react"
import { useSearchParams } from "next/navigation"
import {
  DEFAULT_LOCALE,
  LOCALE_STORAGE_KEY,
  SUPPORTED_LOCALES,
  detectBrowserLocale,
  isSupportedLocale,
  type Locale,
} from "./locales"

const LOCALE_CHANGE_EVENT = "pbbls-locale-change"

function subscribe(callback: () => void) {
  window.addEventListener("storage", callback)
  window.addEventListener(LOCALE_CHANGE_EVENT, callback)
  return () => {
    window.removeEventListener("storage", callback)
    window.removeEventListener(LOCALE_CHANGE_EVENT, callback)
  }
}

function getSnapshot(): Locale {
  const stored = localStorage.getItem(LOCALE_STORAGE_KEY)
  return isSupportedLocale(stored) ? stored : DEFAULT_LOCALE
}

function getServerSnapshot(): Locale {
  return DEFAULT_LOCALE
}

/**
 * Resolves the active app locale.
 * Priority: `?lang=` query param > stored preference > browser language > EN.
 *
 * The query-param override is non-persistent (doesn't write to storage), so a
 * shared link with `?lang=fr` shows French without changing the visitor's
 * stored preference.
 */
export function useLocale() {
  const searchParams = useSearchParams()
  const stored = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot)

  const langParam = searchParams.get("lang")
  const locale: Locale = isSupportedLocale(langParam) ? langParam : stored

  useEffect(() => {
    if (!localStorage.getItem(LOCALE_STORAGE_KEY)) {
      const detected = detectBrowserLocale()
      localStorage.setItem(LOCALE_STORAGE_KEY, detected)
      window.dispatchEvent(new Event(LOCALE_CHANGE_EVENT))
    }
  }, [])

  const setLocale = useCallback((value: Locale) => {
    localStorage.setItem(LOCALE_STORAGE_KEY, value)
    window.dispatchEvent(new Event(LOCALE_CHANGE_EVENT))
  }, [])

  return { locale, setLocale, locales: SUPPORTED_LOCALES }
}
