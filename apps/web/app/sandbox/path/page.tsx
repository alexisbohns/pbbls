"use client"

import { useEffect, useState } from "react"
import { primeEmotionPalettes } from "@/lib/data/useEmotionPalettes"
import { SANDBOX_PALETTES } from "@/lib/seed/sandbox-palettes"
import { SANDBOX_SCENARIOS } from "@/lib/seed/sandbox-pebbles"
import { SandboxPathScreen } from "@/components/sandbox/SandboxPathScreen"
import { SandboxToolbar } from "@/components/sandbox/SandboxToolbar"
import type { StoneSize } from "@/components/sandbox/PolaroidStone"

// Seed the palette cache at module scope, before any consumer mounts: the page
// has no Supabase session, and PebbleFramed degrades to a bare untinted pebble
// when it cannot find a palette.
primeEmotionPalettes(SANDBOX_PALETTES)

/**
 * Throwaway page for iterating on a more dynamic Path display (#720).
 *
 * Unauthenticated, network-free, and deliberately not wired to the real Path:
 * it composes `components/sandbox/*` so nothing tried here can regress /path.
 */
export default function SandboxPathPage() {
  const [scenario, setScenario] = useState(SANDBOX_SCENARIOS[0].key)
  const [stoneSize, setStoneSize] = useState<StoneSize>("md")
  const [dark, setDark] = useState(false)

  // The page drives `.dark` on <html> directly rather than through next-themes,
  // so flipping it here cannot write the user's real theme preference to storage.
  useEffect(() => {
    const root = document.documentElement
    const had = root.classList.contains("dark")
    root.classList.toggle("dark", dark)
    return () => {
      root.classList.toggle("dark", had)
    }
  }, [dark])

  const active = SANDBOX_SCENARIOS.find((s) => s.key === scenario) ?? SANDBOX_SCENARIOS[0]

  return (
    // bg-surface in light mode: the app's page ground is #FFFFFF there, the same
    // white as the card, so a white print on it had no edge at all. Dark mode
    // already separates them (card 0.19 over background 0.15), so it keeps its own
    // pair — this only fixes the light half.
    //
    // The colour goes on a full-width wrapper, not on the max-w-md column: on a
    // wide viewport the column is narrower than the page, and tinting only the
    // column would leave white gutters either side of it.
    <div className="min-h-dvh w-full bg-surface dark:bg-background">
      <div className="mx-auto flex min-h-dvh w-full max-w-md flex-col">
        <SandboxToolbar
          scenario={scenario}
          onScenario={setScenario}
          stoneSize={stoneSize}
          onStoneSize={setStoneSize}
          dark={dark}
          onDark={setDark}
        />
        <SandboxPathScreen pebbles={active.pebbles} stoneSize={stoneSize} dark={dark} />
      </div>
    </div>
  )
}
