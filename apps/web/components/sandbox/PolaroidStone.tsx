"use client"

import type { Mark, Pebble } from "@/lib/types"
import type { PebbleFrameColors } from "@/lib/utils/pebble-frame-colors"
import { useEmotionPalettes } from "@/lib/data/useEmotionPalettes"
import { PebbleFramed } from "@/components/pebble/PebbleFramed"
import { cn } from "@/lib/utils"

/**
 * How big the stone sits on the card. Placement is settled — top-centre,
 * overlapping the paper's edge, like a real pebble laid on a print — so scale is
 * the variable left open, and the toolbar switches it live.
 */
export type StoneSize = "sm" | "md" | "lg"

export const STONE_SIZES: { key: StoneSize; label: string; note: string }[] = [
  { key: "sm", label: "Small stone", note: "Discreet. The card reads as a photo first, with the pebble as a marker." },
  { key: "md", label: "Medium stone", note: "Balanced. The pebble is a peer of the picture rather than a badge on it." },
  { key: "lg", label: "Large stone", note: "Dominant. The pebble is the subject and the picture supports it." },
]

const BOX: Record<StoneSize, string> = {
  sm: "size-11",
  md: "size-14",
  lg: "size-18",
}

/**
 * Every polaroid stone is painted with the opaque-primary fill and light-colour
 * strokes that `pebbleFrameColors` otherwise reserves for intensity 3.
 *
 * On a polaroid the intensity is already said by the card's own size (half width,
 * full width), so spending the frame's colour on it too is redundant — and the
 * low-alpha `surface` fill that intensity 1/2 would otherwise get disappears
 * against white paper, which is the actual reason this override exists.
 */
function solidColors(palette: {
  primary_color: string
  light_color: string
}): PebbleFrameColors {
  const rgb = (hex: string) => (hex.length === 9 ? hex.slice(0, 7) : hex)
  return {
    fillColor: rgb(palette.primary_color),
    fillOpacity: 1,
    strokeColor: rgb(palette.light_color),
  }
}

/**
 * The stone laid on the print. Absolutely positioned by the caller's `relative`
 * box, centred and pulled up so it breaks the paper's top edge.
 */
export function PolaroidStone({
  pebble,
  mark,
  size,
  className,
}: {
  pebble: Pebble
  mark?: Mark
  size: StoneSize
  className?: string
}) {
  const { paletteByEmotionId } = useEmotionPalettes()
  const palette = paletteByEmotionId.get(pebble.emotion_id)

  return (
    <div
      className={cn(
        "pointer-events-none absolute left-1/2 -translate-x-1/2 grid place-items-center",
        BOX[size],
        className,
      )}
    >
      <PebbleFramed
        pebble={pebble}
        mark={mark}
        tier="thumbnail"
        colors={palette ? solidColors(palette) : undefined}
        className="size-full drop-shadow-[0_2px_4px_rgba(0,0,0,0.28)]"
      />
    </div>
  )
}
