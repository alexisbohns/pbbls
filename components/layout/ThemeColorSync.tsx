"use client"

import { useEffect } from "react"
import { useTheme } from "next-themes"
import { useColorWorld } from "@/components/layout/ColorWorldProvider"
import { COLOR_WORLDS } from "@/lib/config"

export function ThemeColorSync() {
  const { colorWorld } = useColorWorld()
  const { resolvedTheme } = useTheme()

  useEffect(() => {
    const config = COLOR_WORLDS.find((w) => w.id === colorWorld)
    if (!config) return

    const mode = resolvedTheme === "dark" ? "dark" : "light"
    const color = config.background[mode]

    const meta = document.querySelector('meta[name="theme-color"]')
    if (meta) {
      meta.setAttribute("content", color)
    }
  }, [colorWorld, resolvedTheme])

  return null
}
