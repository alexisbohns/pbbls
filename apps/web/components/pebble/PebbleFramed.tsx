"use client"

import type { CSSProperties } from "react"
import { motion, useReducedMotion } from "framer-motion"

import type { Mark, Pebble } from "@/lib/types"
import type { RenderTier } from "@/lib/engine"
import { useEmotionPalettes } from "@/lib/data/useEmotionPalettes"
import {
  SIZE_BY_INTENSITY,
  POLARITY_BY_VALENCE,
  pebbleScale,
  outlineAspectRatio,
} from "@/lib/config/pebble-geometry"
import { pebbleFrameColors } from "@/lib/utils/pebble-frame-colors"
import { PebbleOutlineBackdrop } from "./PebbleOutlineBackdrop"
import { PebbleVisual } from "./PebbleVisual"
import { cn } from "@/lib/utils"

type PebbleFramedProps = {
  pebble: Pebble
  mark?: Mark | null
  tier?: RenderTier
  className?: string
  // When true, the backdrop springs in and the pebble fades on top (used by the
  // detail view). Respects prefers-reduced-motion. Defaults off (static rows).
  animateIn?: boolean
}

/**
 * Layers the per-(size × polarity) outline silhouette behind the pebble render,
 * the web port of iOS PR #475. Replaces the previous squared/surface-tinted
 * backdrop chrome at each consumer site.
 *
 * Replicates SwiftUI's `ZStack + scaleEffect + aspectRatio`: the backdrop fills
 * the box, the pebble is scaled down (~0.74, per-size) and centered so it sits
 * inside the silhouette with ~13% margin. Frame fill + pebble stroke come from
 * the intensity-driven `pebbleFrameColors` rule (theme-neutral, matching iOS).
 */
export function PebbleFramed({
  pebble,
  mark = null,
  tier = "thumbnail",
  className,
  animateIn = false,
}: PebbleFramedProps) {
  const prefersReducedMotion = useReducedMotion()
  const { paletteByEmotionId } = useEmotionPalettes()
  const palette = paletteByEmotionId.get(pebble.emotion_id)

  // No palette (unauthenticated previews, legacy rows) → render the bare
  // pebble, matching PebbleVisual's existing fallback behaviour.
  if (!palette) {
    return <PebbleVisual pebble={pebble} mark={mark} tier={tier} className={className} />
  }

  const size = SIZE_BY_INTENSITY[pebble.intensity]
  const polarity = POLARITY_BY_VALENCE[pebble.positiveness]
  const { fillColor, fillOpacity, strokeColor } = pebbleFrameColors(palette, pebble.intensity)
  const shouldAnimate = animateIn && !prefersReducedMotion

  const scaleStyle: CSSProperties = {
    transform: `scale(${pebbleScale(size)})`,
    transformOrigin: "center",
  }

  return (
    <div
      className={cn("relative", className)}
      style={{ aspectRatio: outlineAspectRatio(size) }}
    >
      <motion.div
        className="absolute inset-0"
        initial={shouldAnimate ? { scale: 0.6, opacity: 0 } : false}
        animate={shouldAnimate ? { scale: 1, opacity: 1 } : undefined}
        transition={{ type: "spring", duration: 0.42, bounce: 0.3 }}
      >
        <PebbleOutlineBackdrop
          size={size}
          polarity={polarity}
          fillColor={fillColor}
          fillOpacity={fillOpacity}
        />
      </motion.div>

      <motion.div
        className="absolute inset-0 grid place-items-center"
        initial={shouldAnimate ? { opacity: 0 } : false}
        animate={shouldAnimate ? { opacity: 1 } : undefined}
        transition={{ duration: 0.25, delay: 0.18, ease: "easeOut" }}
      >
        <div className="size-full" style={scaleStyle}>
          <PebbleVisual
            pebble={pebble}
            mark={mark}
            tier={tier}
            strokeOverride={strokeColor}
            className="size-full"
          />
        </div>
      </motion.div>
    </div>
  )
}
