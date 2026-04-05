"use client"

import type { Mark, Pebble } from "@/lib/types"
import type { RenderTier } from "@/lib/engine"
import { EMOTIONS } from "@/lib/config/emotions"
import { usePebbleVisual } from "@/lib/hooks/usePebbleVisual"
import { cn } from "@/lib/utils"

type PebbleVisualProps = {
  pebble: Pebble
  mark?: Mark | null
  tier?: RenderTier
  className?: string
}

export function PebbleVisual({
  pebble,
  mark = null,
  tier = "thumbnail",
  className,
}: PebbleVisualProps) {
  const { svg } = usePebbleVisual(pebble, mark, tier)

  const emotionName =
    EMOTIONS.find((e) => e.id === pebble.emotion_id)?.name ?? "Unknown"

  return (
    <div
      data-slot="pebble-visual"
      role="img"
      aria-label={`Pebble: ${pebble.name}, ${emotionName}, intensity ${pebble.intensity}`}
      className={cn(className)}
      dangerouslySetInnerHTML={{ __html: svg }}
    />
  )
}
