"use client"

import { useCallback, useEffect, useMemo, useRef } from "react"
import { usePathname, useSearchParams } from "next/navigation"
import { useTheme } from "next-themes"
import { useColorWorld } from "@/components/layout/ColorWorldProvider"
import {
  appClassesFor,
  classDiff,
  designClassesFor,
  parseDesignTheme,
  serializeDesignTheme,
  type DesignMode,
  type DesignTheme,
} from "@/lib/theme/design-theme"

type HtmlTheme = { classes: readonly string[]; mode: DesignMode }

/**
 * Paints `theme` on <html>: the classes, plus the `color-scheme` next-themes
 * would set, so native controls (select popups, scrollbars) follow the mode.
 * Mutates nothing when nothing differs, which is what lets the observer below
 * call it on every mutation without looping.
 */
function applyHtmlTheme({ classes, mode }: HtmlTheme) {
  const root = document.documentElement
  const { remove, add } = classDiff(Array.from(root.classList), classes)
  if (remove.length > 0) root.classList.remove(...remove)
  if (add.length > 0) root.classList.add(...add)
  if (root.style.colorScheme !== mode) root.style.colorScheme = mode
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
  const pathname = usePathname()
  const theme = useMemo(() => parseDesignTheme(params), [params])
  const themeKey = serializeDesignTheme(theme)

  const { resolvedTheme } = useTheme()
  const { colorWorld } = useColorWorld()
  const appThemeRef = useRef<HtmlTheme>({ classes: [], mode: "light" })
  useEffect(() => {
    const mode = resolvedTheme === "dark" ? "dark" : "light"
    appThemeRef.current = { classes: appClassesFor(mode, colorWorld), mode }
  }, [resolvedTheme, colorWorld])

  useEffect(() => {
    const design = parseDesignTheme(new URLSearchParams(themeKey))
    const target: HtmlTheme = { classes: designClassesFor(design), mode: design.mode }
    applyHtmlTheme(target)
    // ColorWorldProvider and next-themes write <html> classes and color-scheme
    // too, and on mount their effects run after this one (parents after
    // children). Re-apply whenever someone else changes them.
    const observer = new MutationObserver(() => applyHtmlTheme(target))
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class", "style"] })
    return () => observer.disconnect()
  }, [themeKey])

  // Declared after the effect above on purpose: React runs cleanups in
  // declaration order, so the observer is disconnected before this restores
  // the app's theme (otherwise it would put the design theme straight back).
  useEffect(() => () => applyHtmlTheme(appThemeRef.current), [])

  const setTheme = useCallback(
    (patch: Partial<DesignTheme>) => {
      // Read the live URL, not the rendered params: two quick presses of T must
      // flip twice even before the first update has re-rendered. The native
      // history API keeps the #section hash and is synced with useSearchParams.
      const current = parseDesignTheme(new URLSearchParams(window.location.search))
      const next = serializeDesignTheme({ ...current, ...patch })
      window.history.replaceState(null, "", `${pathname}?${next}${window.location.hash}`)
    },
    [pathname],
  )

  return { theme, themeKey, setTheme }
}
