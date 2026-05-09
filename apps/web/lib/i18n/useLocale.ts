"use client"

import { useCallback, useEffect, useSyncExternalStore } from "react"
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
 * Resolves the active app locale from `localStorage`, with fallback to the
 * default. The `?lang=` override and browser-language detection are wired in
 * separately by `LangParamSync` so this hook can run anywhere without
 * requiring a Suspense boundary.
 */
export function useLocale() {
  const stored = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot)

  const setLocale = useCallback((value: Locale) => {
    localStorage.setItem(LOCALE_STORAGE_KEY, value)
    window.dispatchEvent(new Event(LOCALE_CHANGE_EVENT))
  }, [])

  return { locale: stored, setLocale, locales: SUPPORTED_LOCALES }
}

/**
 * One-time browser-language detection on first visit. Writes the detected
 * locale (or default) to storage so subsequent reads are stable. Safe to
 * call anywhere; runs once on mount.
 */
export function useDetectInitialLocale() {
  useEffect(() => {
    if (!localStorage.getItem(LOCALE_STORAGE_KEY)) {
      const detected = detectBrowserLocale()
      localStorage.setItem(LOCALE_STORAGE_KEY, detected)
      window.dispatchEvent(new Event(LOCALE_CHANGE_EVENT))
    }
  }, [])
}
