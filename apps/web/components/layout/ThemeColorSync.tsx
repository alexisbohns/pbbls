"use client"

import { useEffect } from "react"
import { useTheme } from "next-themes"
import { useColorWorld } from "@/components/layout/ColorWorldProvider"
import { getColorWorldBackground } from "@/lib/config"

export function ThemeColorSync() {
  const { colorWorld } = useColorWorld()
  const { resolvedTheme } = useTheme()

  useEffect(() => {
    const bg = getColorWorldBackground(colorWorld)

    const metaLight = document.querySelector(
      'meta[name="theme-color"][media*="light"]'
    )
    const metaDark = document.querySelector(
      'meta[name="theme-color"][media*="dark"]'
    )

    if (metaLight) metaLight.setAttribute("content", bg.light)
    if (metaDark) metaDark.setAttribute("content", bg.dark)
  }, [colorWorld, resolvedTheme])

  return null
}
