"use client"

import { createContext, useContext, useEffect, useState } from "react"
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

function getStoredColorWorld(): ColorWorld {
  if (typeof window === "undefined") return DEFAULT_COLOR_WORLD
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored && COLOR_WORLDS.includes(stored as ColorWorld)) {
    return stored as ColorWorld
  }
  return DEFAULT_COLOR_WORLD
}

interface ColorWorldContextValue {
  colorWorld: ColorWorld
  setColorWorld: (world: ColorWorld) => void
}

const ColorWorldContext = createContext<ColorWorldContextValue>({
  colorWorld: DEFAULT_COLOR_WORLD,
  setColorWorld: () => {},
})

export function ColorWorldProvider({
  children,
}: {
  children: React.ReactNode
}) {
  const [colorWorld, setColorWorld] = useState<ColorWorld>(getStoredColorWorld)

  // Apply class to <html> and persist whenever colorWorld changes
  useEffect(() => {
    const root = document.documentElement
    COLOR_WORLDS.forEach((w) => root.classList.remove(w))
    // blush-quartz is the :root default — no class needed
    if (colorWorld !== DEFAULT_COLOR_WORLD) {
      root.classList.add(colorWorld)
    }
    localStorage.setItem(STORAGE_KEY, colorWorld)
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
