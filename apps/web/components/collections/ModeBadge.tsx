"use client"

import { useTranslations } from "next-intl"
import type { Collection } from "@/lib/types"
import { Badge } from "@/components/ui/badge"

export const MODE_KEYS = ["stack", "pack", "track"] as const

export const MODE_EMOJI: Record<NonNullable<Collection["mode"]>, string> = {
  stack: "🎯",
  pack: "📦",
  track: "🔄",
}

export function ModeBadge({ mode }: { mode: Collection["mode"] }) {
  const tModes = useTranslations("collections.modes")
  if (!mode) return null

  const label = tModes(mode)
  const emoji = MODE_EMOJI[mode]

  return (
    <Badge variant="outline" aria-label={tModes("ariaLabel", { label })}>
      <span aria-hidden="true">{emoji}</span> {label}
    </Badge>
  )
}
