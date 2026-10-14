"use client"

import type { CSSProperties } from "react"
import { useTranslations } from "next-intl"
import { outlineAspectRatio } from "@/lib/config/pebble-geometry"
import { BLANK_PALETTE } from "@/lib/config/stone-tones"
import { cn } from "@/lib/utils"
import { PebbleOutlineBackdrop } from "./PebbleOutlineBackdrop"

/** `#8686861A`: the blank ramp's primary at 0x1A alpha (R4). */
const SURFACE_OPACITY = 0x1a / 0xff

type BlankStoneProps = {
  className?: string
}

/**
 * A moment that is not a pebble yet (R4, PD-W9): the medium neutral silhouette
 * in outline only, nothing carved inside: no glyph, no veins, no fossil. Its
 * colours are fixed, not theme roles, so it reads the same in light and dark.
 * The Spine draws its own blank through `LitStone`; this one is for the read
 * page, the success screen and lists.
 */
export function BlankStone({ className }: BlankStoneProps) {
  const t = useTranslations("moment")
  // Fit the silhouette's ratio inside the caller's box, as PebbleFramed does.
  const ar = outlineAspectRatio("medium")
  const fit: CSSProperties = ar <= 1 ? { height: "100%", aspectRatio: ar } : { width: "100%", aspectRatio: ar }

  return (
    <div role="img" aria-label={t("notAPebble")} className={cn("grid place-items-center", className)}>
      <div style={fit}>
        <PebbleOutlineBackdrop
          size="medium"
          polarity="neutral"
          fillColor={BLANK_PALETTE.primary_color}
          fillOpacity={SURFACE_OPACITY}
          stroke={BLANK_PALETTE.primary_color}
        />
      </div>
    </div>
  )
}
