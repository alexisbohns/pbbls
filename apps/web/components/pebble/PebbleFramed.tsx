"use client"

import type { CSSProperties } from "react"
import { motion, useReducedMotion } from "framer-motion"

import type { StoneLook } from "@/lib/types"
import { useEmotionPalettes } from "@/lib/data/useEmotionPalettes"
import {
  SIZE_BY_INTENSITY,
  POLARITY_BY_VALENCE,
  pebbleScale,
  outlineAspectRatio,
} from "@/lib/config/pebble-geometry"
import { pebbleFrameColors } from "@/lib/utils/pebble-frame-colors"
import { BlankStone } from "./BlankStone"
import { PebbleOutlineBackdrop } from "./PebbleOutlineBackdrop"
import { PebbleVisual } from "./PebbleVisual"
import { cn } from "@/lib/utils"

type PebbleFramedProps = {
  /** Null is a bare moment: the blank stone (R4). */
  look: StoneLook | null
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
 *
 * A null look (a moment that is not a pebble yet) draws `BlankStone`.
 */
export function PebbleFramed({
  look,
  className,
  animateIn = false,
}: PebbleFramedProps) {
  const prefersReducedMotion = useReducedMotion()
  const { paletteByEmotionId } = useEmotionPalettes()
  const palette = look ? paletteByEmotionId.get(look.emotion_id) : undefined

  if (!look) return <BlankStone className={className} />

  // No palette (unauthenticated previews, legacy rows) → render the bare
  // pebble, matching PebbleVisual's existing fallback behaviour.
  if (!palette) {
    return <PebbleVisual look={look} className={className} />
  }

  const size = SIZE_BY_INTENSITY[look.intensity]
  const polarity = POLARITY_BY_VALENCE[look.positiveness]
  const { fillColor, fillOpacity, strokeColor } = pebbleFrameColors(palette, look.intensity)
  const shouldAnimate = animateIn && !prefersReducedMotion

  const scaleStyle: CSSProperties = {
    transform: `scale(${pebbleScale(size)})`,
    transformOrigin: "center",
  }

  // Fit a box at the outline's aspect ratio INSIDE the caller's (usually
  // square) box — the CSS equivalent of iOS `.aspectRatio(ar, .fit)`. Pinning
  // the dimension that would otherwise overflow (height for tall outlines,
  // width for wide ones) lets `aspect-ratio` derive the other. Without this,
  // a square caller box forces the box square, the backdrop letterboxes, and
  // the pebble — scaled against the full square — pokes out of the silhouette.
  const ar = outlineAspectRatio(size)
  const fitStyle: CSSProperties =
    ar <= 1 ? { height: "100%", aspectRatio: ar } : { width: "100%", aspectRatio: ar }

  return (
    <div className={cn("grid place-items-center", className)}>
      <div className="relative" style={fitStyle}>
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
              look={look}
              strokeOverride={strokeColor}
              className="size-full"
            />
          </div>
        </motion.div>
      </div>
    </div>
  )
}
