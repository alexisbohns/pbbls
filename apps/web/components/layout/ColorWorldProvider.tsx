"use client"

import { createContext, useContext, useEffect, useSyncExternalStore } from "react"
import type { ColorWorld } from "@/lib/types"

const COLOR_WORLDS: ColorWorld[] = [
  "blush-quartz",
  "stoic-rock",
  "cave-pigment",
  "dusk-stone",
  "moss-pool",
]

const DEFAULT_COLOR_WORLD: ColorWorld = "blush-quartz"
const STORAGE_KEY = "pbbls-color-world"

interface ColorWorldContextValue {
  colorWorld: ColorWorld
  setColorWorld: (world: ColorWorld) => void
}

const ColorWorldContext = createContext<ColorWorldContextValue>({
  colorWorld: DEFAULT_COLOR_WORLD,
  setColorWorld: () => {},
})

// ──────────────────────────────────────────────────────────────────────────
// External store for color world persistence
// ──────────────────────────────────────────────────────────────────────────

function subscribe(callback: () => void) {
  // Listen for storage changes from other tabs
  window.addEventListener("storage", callback)
  return () => window.removeEventListener("storage", callback)
}

function getSnapshot(): ColorWorld {
  // Read from localStorage
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored && COLOR_WORLDS.includes(stored as ColorWorld)) {
    return stored as ColorWorld
  }
  return DEFAULT_COLOR_WORLD
}

function getServerSnapshot(): ColorWorld {
  // On server, always return default
  return DEFAULT_COLOR_WORLD
}

export function ColorWorldProvider({
  children,
}: {
  children: React.ReactNode
}) {
  // Use useSyncExternalStore to safely subscribe to localStorage
  const colorWorld = useSyncExternalStore(
    subscribe,
    getSnapshot,
    getServerSnapshot,
  )

  const setColorWorld = (world: ColorWorld) => {
    localStorage.setItem(STORAGE_KEY, world)

    // Apply class to <html>
    const root = document.documentElement
    COLOR_WORLDS.forEach((w) => root.classList.remove(w))
    if (world !== DEFAULT_COLOR_WORLD) {
      root.classList.add(world)
    }

    // Notify listeners (for cross-tab sync)
    window.dispatchEvent(new StorageEvent("storage", { key: STORAGE_KEY }))
  }

  // Apply class to <html> when colorWorld changes (after hydration)
  useEffect(() => {
    const root = document.documentElement
    COLOR_WORLDS.forEach((w) => root.classList.remove(w))
    if (colorWorld !== DEFAULT_COLOR_WORLD) {
      root.classList.add(colorWorld)
    }
  }, [colorWorld])

  return (
    <ColorWorldContext.Provider value={{ colorWorld, setColorWorld }}>
      {children}
    </ColorWorldContext.Provider>
  )
}

export function useColorWorld() {
  return useContext(ColorWorldContext)
}
