"use client"

import { useEffect, useMemo, useRef, useState, type ReactNode, type RefObject } from "react"
import type { CanvasZoom, DeviceChoice } from "@/lib/design/devices"
import type { FormChoice } from "@/lib/design/glyph-forms"
import type { PbblsIconSize } from "@/lib/icons/pbbls-icon"
import { DesignContextProvider, type DesignContextValue } from "./DesignContext"
import { DesignMobileBar } from "./DesignMobileBar"
import { DesignRail } from "./DesignRail"
import { DesignToolbar } from "./DesignToolbar"
import type { DesignToolsState } from "./DesignTools"
import { blockLinkNavigation } from "./link-guard"
import { useDesignTheme } from "./useDesignTheme"

function isTyping(target: EventTarget | null): boolean {
  return (
    target instanceof HTMLElement &&
    (target.isContentEditable || ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName))
  )
}

/** The content box's width, for the canvas's "fit". */
function useWidth(ref: RefObject<HTMLElement | null>): number {
  const [width, setWidth] = useState(0)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    const observer = new ResizeObserver(([entry]) => setWidth(Math.floor(entry.contentRect.width)))
    observer.observe(el)
    return () => observer.disconnect()
  }, [ref])
  return width
}

/**
 * /sandbox/design (#986, grown into a directory): Overview, Colors & type,
 * Icons, Components and Views under one Current ↔ M3 × light/dark × contrast
 * switcher. Components and views are drawn as real-size mobile and desktop
 * screens, scaled to the page. Unauthenticated, network-free, never writes
 * the viewer's preferences.
 */
export function DesignDirectory({ children }: { children: ReactNode }) {
  const { theme, themeKey, setTheme } = useDesignTheme()
  const [zoom, setZoom] = useState<CanvasZoom>("fit")
  const [devices, setDevices] = useState<DeviceChoice>("both")
  const [iconQuery, setIconQuery] = useState("")
  const [iconSize, setIconSize] = useState<PbblsIconSize>("tile")
  const [glyphForm, setGlyphForm] = useState<FormChoice>("regular")
  const [glyphProgress, setGlyphProgress] = useState(1)
  const [reviewOnly, setReviewOnly] = useState(false)
  const contentRef = useRef<HTMLDivElement>(null)
  const canvasWidth = useWidth(contentRef)

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if (e.metaKey || e.ctrlKey || e.altKey || isTyping(e.target)) return
      const key = e.key.toLowerCase()
      if (key === "t") setTheme({ theme: theme.theme === "m3" ? "current" : "m3" })
      if (key === "d") setTheme({ mode: theme.mode === "dark" ? "light" : "dark" })
    }
    window.addEventListener("keydown", onKeyDown)
    return () => window.removeEventListener("keydown", onKeyDown)
  }, [theme, setTheme])

  const value = useMemo<DesignContextValue>(
    () => ({ theme, themeKey, zoom, devices, canvasWidth, iconQuery, iconSize, glyphForm, glyphProgress, reviewOnly }),
    [theme, themeKey, zoom, devices, canvasWidth, iconQuery, iconSize, glyphForm, glyphProgress, reviewOnly],
  )

  const tools: DesignToolsState = {
    theme,
    onChange: setTheme,
    zoom,
    onZoom: setZoom,
    devices,
    onDevices: setDevices,
    iconQuery,
    onIconQuery: setIconQuery,
    iconSize,
    onIconSize: setIconSize,
    glyphForm,
    onGlyphForm: setGlyphForm,
    glyphProgress,
    onGlyphProgress: setGlyphProgress,
    reviewOnly,
    onReviewOnly: setReviewOnly,
  }

  // Three areas, like the app (#1384): the rail, the page's header and tools,
  // the page. From 1024 px side by side; from 600 px the header stacks over
  // the page; below 600 px one sticky bar replaces the rail and the header.
  return (
    <DesignContextProvider value={value}>
      <div className="flex min-h-dvh bg-background text-foreground">
        <DesignRail themeKey={themeKey} />
        <div className="min-w-0 flex-1 lg:flex lg:items-start">
          <DesignMobileBar themeKey={themeKey} {...tools} />
          <DesignToolbar {...tools} />
          <div className="mx-auto max-w-[1600px] px-4 py-10 lg:min-w-0 lg:flex-1 lg:px-8">
            <div ref={contentRef} data-theme-key={themeKey} onClickCapture={blockLinkNavigation} className="flex flex-col gap-16">
              {children}
            </div>
          </div>
        </div>
      </div>
    </DesignContextProvider>
  )
}
