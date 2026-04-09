"use client"

import { useCallback, useEffect, useSyncExternalStore } from "react"
import { useSearchParams } from "next/navigation"
import type { DocsLocale } from "@/lib/docs/types"

const STORAGE_KEY = "pbbls-docs-locale"
const SUPPORTED_LOCALES: readonly DocsLocale[] = ["en", "fr"]
const DEFAULT_LOCALE: DocsLocale = "en"

function subscribe(callback: () => void) {
  window.addEventListener("storage", callback)
  return () => window.removeEventListener("storage", callback)
}

function getSnapshot(): DocsLocale {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored && isSupported(stored)) return stored
  return DEFAULT_LOCALE
}

function getServerSnapshot(): DocsLocale {
  return DEFAULT_LOCALE
}

function isSupported(value: string): value is DocsLocale {
  return (SUPPORTED_LOCALES as readonly string[]).includes(value)
}

function detectBrowserLocale(): DocsLocale {
  if (typeof navigator === "undefined") return DEFAULT_LOCALE
  const lang = navigator.language.slice(0, 2).toLowerCase()
  return isSupported(lang) ? lang : DEFAULT_LOCALE
}

export function useDocsLocale() {
  const searchParams = useSearchParams()
  const stored = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot)

  // Query param override takes priority
  const langParam = searchParams.get("lang")
  const locale: DocsLocale = langParam && isSupported(langParam) ? langParam : stored

  // On first mount, detect browser locale if nothing is stored yet
  useEffect(() => {
    if (!localStorage.getItem(STORAGE_KEY)) {
      const detected = detectBrowserLocale()
      localStorage.setItem(STORAGE_KEY, detected)
      window.dispatchEvent(new StorageEvent("storage", { key: STORAGE_KEY }))
    }
  }, [])

  const setLocale = useCallback((value: DocsLocale) => {
    localStorage.setItem(STORAGE_KEY, value)
    window.dispatchEvent(new StorageEvent("storage", { key: STORAGE_KEY }))
  }, [])

  return { locale, setLocale, locales: SUPPORTED_LOCALES }
}
