"use client"

import { useEffect, type MouseEvent } from "react"
import { primeEmotionPalettes } from "@/lib/data/useEmotionPalettes"
import { SANDBOX_PALETTES } from "@/lib/seed/sandbox-palettes"
import { DesignToolbar } from "./DesignToolbar"
import { FixtureProviders } from "./FixtureProviders"
import { useDesignTheme } from "./useDesignTheme"
import { FoundationsSection } from "./foundations/FoundationsSection"
import { PrimitivesSection } from "./primitives/PrimitivesSection"
import { ComponentsSection } from "./ComponentsSection"

// Seed the palette cache at module scope, before any consumer mounts (the
// /sandbox/path precedent): pebbles render tinted without a Supabase call.
primeEmotionPalettes(SANDBOX_PALETTES)

function isTyping(target: EventTarget | null): boolean {
  return (
    target instanceof HTMLElement &&
    (target.isContentEditable || ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName))
  )
}

// Specimens contain real links (cards, tiles, headers). Capture-phase
// preventDefault stops the browser; stopPropagation stops next/link's handler.
// React events follow the React tree, so this covers portaled overlays too.
function blockLinkNavigation(e: MouseEvent) {
  if (e.target instanceof Element && e.target.closest("a[href]")) {
    e.preventDefault()
    e.stopPropagation()
  }
}

/**
 * /sandbox/design (#986): every web component, rendered from fixtures, under a
 * Current ↔ M3 × light/dark × contrast switcher. Unauthenticated,
 * network-free, never writes the viewer's preferences.
 */
export function DesignScreen() {
  const { theme, themeKey, setTheme } = useDesignTheme()

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

  return (
    <div className="min-h-dvh bg-background text-foreground">
      <DesignToolbar theme={theme} onChange={setTheme} />
      <FixtureProviders>
        <main
          data-theme-key={themeKey}
          onClickCapture={blockLinkNavigation}
          className="mx-auto flex max-w-6xl flex-col gap-20 px-4 py-10"
        >
          <FoundationsSection theme={theme} themeKey={themeKey} />
          <PrimitivesSection />
          <ComponentsSection />
        </main>
      </FixtureProviders>
    </div>
  )
}
