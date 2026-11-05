"use client"

import { useEffect, useMemo, useRef, type RefObject } from "react"
import { useSearchParams } from "next/navigation"
import { primeEmotionPalettes } from "@/lib/data/useEmotionPalettes"
import { frameMessage, parseFrameMessage, parseFrameTarget } from "@/lib/design/frame-protocol"
import { SANDBOX_PALETTES } from "@/lib/seed/sandbox-palettes"
import { FixtureProviders } from "../FixtureProviders"
import { SpecimenScope } from "../SpecimenScope"
import { blockLinkNavigation } from "../link-guard"
import { useDesignTheme } from "../useDesignTheme"
import { ComponentsSection } from "../ComponentsSection"
import { PrimitivesSection } from "../primitives/PrimitivesSection"
import { renderView } from "../views/view-registry"

// Seed the palette cache at module scope, before any consumer mounts (the
// /sandbox/path precedent): pebbles render tinted without a Supabase call.
primeEmotionPalettes(SANDBOX_PALETTES)

/**
 * A view that focuses something as it mounts (a detail's heading, a dialog)
 * would take the keyboard from the canvas: a same-origin frame's focus()
 * moves the page's focus into it. Inside the canvas, a frame moves focus
 * only while it already holds the canvas's focus. Module scope, so it runs
 * before any view mounts; a frame opened on its own (1:1) keeps the app's
 * behaviour.
 */
if (typeof window !== "undefined" && window.parent !== window) {
  const focus = HTMLElement.prototype.focus
  HTMLElement.prototype.focus = function focusWhenUsed(this: HTMLElement, options?: FocusOptions) {
    // Same origin, so the canvas's own focus is readable: the frame holds it
    // only when someone has clicked or tabbed into it.
    if (window.parent.document.activeElement === window.frameElement) focus.call(this, options)
  }
}

/** Takes the canvas's theme into this frame's own URL, where useDesignTheme reads it. */
function useCanvasTheme() {
  useEffect(() => {
    if (window.parent === window) return
    function onMessage(e: MessageEvent) {
      if (e.origin !== window.location.origin || e.source !== window.parent) return
      const message = parseFrameMessage(e.data)
      if (message?.type !== "theme") return
      const next = new URLSearchParams(window.location.search)
      for (const [key, value] of new URLSearchParams(message.themeKey)) next.set(key, value)
      if (next.toString() !== window.location.search.slice(1)) {
        window.history.replaceState(null, "", `${window.location.pathname}?${next.toString()}`)
      }
    }
    window.addEventListener("message", onMessage)
    window.parent.postMessage(frameMessage({ type: "ready" }), window.location.origin)
    return () => window.removeEventListener("message", onMessage)
  }, [])
}

/** Tells the canvas how tall the content is, for frames that grow with it. */
function useReportHeight(ref: RefObject<HTMLDivElement | null>) {
  useEffect(() => {
    const el = ref.current
    if (!el || window.parent === window) return
    let last = 0
    const observer = new ResizeObserver(() => {
      const height = Math.ceil(el.getBoundingClientRect().height)
      if (height === last) return
      last = height
      window.parent.postMessage(frameMessage({ type: "height", height }), window.location.origin)
    })
    observer.observe(el)
    return () => observer.disconnect()
  }, [ref])
}

/**
 * /sandbox/design/frame: one view, or one group of specimens, alone on a
 * viewport, so the canvas can draw it in an iframe of a real device's size
 * and the app's breakpoints see that device. Opened on its own, it is the
 * 1:1 view of the same thing.
 */
export function DesignFrame() {
  const params = useSearchParams()
  const target = useMemo(() => parseFrameTarget(params), [params])
  const { themeKey } = useDesignTheme()
  const contentRef = useRef<HTMLDivElement>(null)
  useCanvasTheme()
  useReportHeight(contentRef)
  const scope = useMemo(() => (target && "specimens" in target ? new Set(target.specimens) : null), [target])

  return (
    // Fixed to the viewport: the app's <main> pads and scrolls, a frame must not.
    <div
      data-theme-key={themeKey}
      onClickCapture={blockLinkNavigation}
      className="fixed inset-0 overflow-y-auto bg-background text-foreground"
    >
      <FixtureProviders>
        {target && "view" in target ? (
          <div className="h-full">{renderView(target.view) ?? <p className="p-6 text-sm">Unknown view “{target.view}”.</p>}</div>
        ) : (
          <div ref={contentRef} className="flex flex-col gap-10 p-6">
            {scope ? (
              <SpecimenScope.Provider value={scope}>
                <PrimitivesSection />
                <ComponentsSection />
              </SpecimenScope.Provider>
            ) : (
              <p className="text-sm">Nothing to draw: add ?view= or ?specimens= to the URL.</p>
            )}
          </div>
        )}
      </FixtureProviders>
    </div>
  )
}
