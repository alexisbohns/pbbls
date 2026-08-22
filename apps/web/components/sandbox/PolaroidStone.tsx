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

/** A small pebble's card gets a stone one step down, so intensity still reads on a
 *  wall where every card is the same column width. */
export const STEP_DOWN: Record<StoneSize, StoneSize> = { sm: "sm", md: "sm", lg: "md" }

/**
 * How a stone is painted, by intensity.
 *
 * Small and medium fill with the pale `light` colour and draw in `primary`;
 * large keeps the production rule and inverts it — opaque `primary` fill, `light`
 * strokes. So the wall reads as pale stones throughout with the occasional dark
 * one, and the inversion is what marks a large pebble out rather than size alone.
 *
 * Both replace `pebbleFrameColors`, whose intensity-1/2 branch fills with the
 * low-alpha `surface` colour. That is a faint wash designed for the app's tinted
 * background; on white paper it disappears.
 */
function stoneColors(
  palette: { primary_color: string; light_color: string },
  intensity: 1 | 2 | 3,
): PebbleFrameColors {
  const rgb = (hex: string) => (hex.length === 9 ? hex.slice(0, 7) : hex)
  const primary = rgb(palette.primary_color)
  const light = rgb(palette.light_color)
  return intensity === 3
    ? { fillColor: primary, fillOpacity: 1, strokeColor: light }
    : { fillColor: light, fillOpacity: 1, strokeColor: primary }
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
        colors={palette ? stoneColors(palette, pebble.intensity) : undefined}
        className="size-full drop-shadow-[0_1px_2px_rgba(0,0,0,0.09)]"
      />
    </div>
  )
}
