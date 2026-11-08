"use client"

import { useEffect, useMemo, useRef, useState, type ReactNode, type RefObject } from "react"
import type { CanvasZoom, DeviceChoice } from "@/lib/design/devices"
import type { PbblsIconSize } from "@/lib/icons/pbbls-icon"
import { DesignContextProvider, type DesignContextValue } from "./DesignContext"
import { DesignRail } from "./DesignRail"
import { DesignToolbar } from "./DesignToolbar"
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
    () => ({ theme, themeKey, zoom, devices, canvasWidth, iconQuery, iconSize }),
    [theme, themeKey, zoom, devices, canvasWidth, iconQuery, iconSize],
  )

  return (
    <DesignContextProvider value={value}>
      <div className="flex min-h-dvh bg-background text-foreground">
        <DesignRail themeKey={themeKey} />
        {/* Below the rail breakpoint the rail is a bar over the foot: leave it room. */}
        <div className="min-w-0 flex-1 pb-20 rail:pb-0">
          <DesignToolbar
            theme={theme}
            onChange={setTheme}
            zoom={zoom}
            onZoom={setZoom}
            devices={devices}
            onDevices={setDevices}
            iconQuery={iconQuery}
            onIconQuery={setIconQuery}
            iconSize={iconSize}
            onIconSize={setIconSize}
          />
          <div className="mx-auto max-w-[1600px] px-4 py-10">
            <div ref={contentRef} data-theme-key={themeKey} onClickCapture={blockLinkNavigation} className="flex flex-col gap-16">
              {children}
            </div>
          </div>
        </div>
      </div>
    </DesignContextProvider>
  )
}
