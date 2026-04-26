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
  // Prefer the server-composed render written by the compose-pebble edge
  // function. Fall back to the client engine for legacy rows that pre-date
  // the remote engine and for unauthenticated previews (e.g. landing page
  // seed pebbles) where no render exists yet.
  const fallback = usePebbleVisual(pebble, mark, tier)
  const isServerRender = pebble.render_svg !== null
  const svg = pebble.render_svg ?? fallback.svg

  const emotion = EMOTIONS.find((e) => e.id === pebble.emotion_id)
  const emotionName = emotion?.name ?? "Unknown"

  // Server-composed SVGs use stroke="currentColor" and carry their own
  // width/height attributes. Setting `color` on the wrapper resolves the
  // `currentColor` strokes to the emotion hue (mirrors iOS PebbleRenderView).
  // The CSS rule on `.pbbls-visual svg` (in globals.css) overrides the
  // intrinsic width/height so the SVG fits its container.
  // The client-engine fallback already inlines the emotion color via
  // `recolor()`, so the wrapper color is harmless there.
  const wrapperStyle = isServerRender && emotion
    ? { color: emotion.color }
    : undefined

  return (
    <div
      data-slot="pebble-visual"
      role="img"
      aria-label={`Pebble: ${pebble.name}, ${emotionName}, intensity ${pebble.intensity}`}
      className={cn("pbbls-visual", className)}
      style={wrapperStyle}
      dangerouslySetInnerHTML={{ __html: svg }}
    />
  )
}
