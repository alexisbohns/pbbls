"use client"

import { useCallback, useEffect, useMemo, useRef } from "react"
import { usePathname, useRouter, useSearchParams } from "next/navigation"
import { useTheme } from "next-themes"
import { useColorWorld } from "@/components/layout/ColorWorldProvider"
import {
  appClassesFor,
  classDiff,
  designClassesFor,
  parseDesignTheme,
  serializeDesignTheme,
  type DesignTheme,
} from "@/lib/theme/design-theme"

function applyClasses(target: readonly string[]) {
  const root = document.documentElement
  const { remove, add } = classDiff(Array.from(root.classList), target)
  if (remove.length > 0) root.classList.remove(...remove)
  if (add.length > 0) root.classList.add(...add)
}

/**
 * Drives /sandbox/design's theme from the URL and paints it on <html>, where
 * portaled overlays (dialogs, sheets, popovers under <body>) pick it up too.
 *
 * Never writes the viewer's preferences. next-themes and ColorWorldProvider are
 * only read, so leaving the page hands back exactly the classes the app wants.
 */
export function useDesignTheme(): {
  theme: DesignTheme
  themeKey: string
  setTheme: (patch: Partial<DesignTheme>) => void
} {
  const params = useSearchParams()
  const router = useRouter()
  const pathname = usePathname()
  const theme = useMemo(() => parseDesignTheme(params), [params])
  const themeKey = serializeDesignTheme(theme)

  const { resolvedTheme } = useTheme()
  const { colorWorld } = useColorWorld()
  const appClassesRef = useRef<string[]>([])
  useEffect(() => {
    appClassesRef.current = appClassesFor(resolvedTheme === "dark" ? "dark" : "light", colorWorld)
  }, [resolvedTheme, colorWorld])

  useEffect(() => {
    const target = designClassesFor(parseDesignTheme(new URLSearchParams(themeKey)))
    applyClasses(target)
    // ColorWorldProvider and next-themes write <html> classes too, and on mount
    // their effects run after this one (parents after children). Re-apply
    // whenever someone else changes the list. applyClasses mutates nothing when
    // nothing differs, so the observer cannot loop.
    const observer = new MutationObserver(() => applyClasses(target))
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] })
    return () => observer.disconnect()
  }, [themeKey])

  // Declared after the effect above on purpose: React runs cleanups in
  // declaration order, so the observer is disconnected before this restores
  // the app's classes (otherwise it would put the design classes straight back).
  useEffect(() => () => applyClasses(appClassesRef.current), [])

  const setTheme = useCallback(
    (patch: Partial<DesignTheme>) => {
      const next = serializeDesignTheme({ ...parseDesignTheme(new URLSearchParams(themeKey)), ...patch })
      router.replace(`${pathname}?${next}`, { scroll: false })
    },
    [pathname, router, themeKey],
  )

  return { theme, themeKey, setTheme }
}
