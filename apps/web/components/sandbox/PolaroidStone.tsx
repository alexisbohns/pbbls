"use client"

import type { CSSProperties } from "react"
import type { Mark, Pebble } from "@/lib/types"
import { EMOTIONS } from "@/lib/config/emotions"
import { useEmotionPalettes } from "@/lib/data/useEmotionPalettes"
import { usePebbleVisual } from "@/lib/hooks/usePebbleVisual"
import {
  SIZE_BY_INTENSITY,
  POLARITY_BY_VALENCE,
  pebbleScale,
  outlineAspectRatio,
} from "@/lib/config/pebble-geometry"
import { PebbleOutlineBackdrop } from "@/components/pebble/PebbleOutlineBackdrop"
import { SANDBOX_DARK_COLORS } from "@/lib/seed/sandbox-palettes"
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
 * How a stone is painted, by theme.
 *
 * Light mode fills with the pale `light` colour and draws in `primary`; dark mode
 * fills with `dark` and draws in `secondary`. Same relationship both ways round —
 * a near-flat field with the glyph one clear step off it — so the stone carries
 * the same weight in either theme rather than going muddy in one of them.
 *
 * Replaces `pebbleFrameColors`, whose intensity-1/2 branch fills with the
 * low-alpha `surface` colour: a faint wash designed for the app's tinted
 * background, which on white paper disappears.
 */
function stoneColors(
  palette: { primary_color: string; secondary_color: string; light_color: string },
  darkColor: string | undefined,
  dark: boolean,
): { fillColor: string; strokeColor: string } {
  const rgb = (hex: string) => (hex.length === 9 ? hex.slice(0, 7) : hex)
  return dark && darkColor
    ? { fillColor: rgb(darkColor), strokeColor: rgb(palette.secondary_color) }
    : { fillColor: rgb(palette.light_color), strokeColor: rgb(palette.primary_color) }
}

/**
 * The stone laid on the print — the outline silhouette with the composed pebble
 * scaled inside it, the same construction as `PebbleFramed`.
 *
 * Rebuilt here rather than reusing `PebbleFramed` because of how the colour has to
 * reach the strokes. These fixtures carry `render_svg: null`, so they take the
 * client-engine fallback, and that path bakes the legacy `EMOTIONS[].color` into
 * the markup via the engine's own `recolor()` — `PebbleVisual` documents that it
 * ignores `strokeOverride` there for exactly that reason. So a stroke colour
 * handed to `PebbleFramed` is silently dropped on every pebble this page renders.
 * Re-running the substitution on the composed SVG is the only way to land it
 * without changing how the shipped component renders anonymous pebbles.
 */
function StoneVisual({
  pebble,
  mark,
  fillColor,
  strokeColor,
}: {
  pebble: Pebble
  mark?: Mark
  fillColor: string
  strokeColor: string
}) {
  const { svg } = usePebbleVisual(pebble, mark ?? null, "thumbnail")

  const size = SIZE_BY_INTENSITY[pebble.intensity]
  const polarity = POLARITY_BY_VALENCE[pebble.positiveness]

  // The colour the engine baked in, so we know what to swap out. Mirrors
  // `resolveEmotionColor` in lib/engine/params.ts, including its fallback.
  const baked = EMOTIONS.find((e) => e.id === pebble.emotion_id)?.color ?? "#9CA3AF"
  const inked = svg.replaceAll(baked, strokeColor)

  const ar = outlineAspectRatio(size)
  // Fit a box at the outline's aspect ratio inside the caller's square box — the
  // CSS equivalent of SwiftUI `.aspectRatio(ar, .fit)`.
  const fitStyle: CSSProperties =
    ar <= 1 ? { height: "100%", aspectRatio: ar } : { width: "100%", aspectRatio: ar }

  const scaleStyle: CSSProperties = {
    transform: `scale(${pebbleScale(size)})`,
    transformOrigin: "center",
  }

  return (
    <div className="grid size-full place-items-center">
      <div className="relative" style={fitStyle}>
        <div className="absolute inset-0">
          <PebbleOutlineBackdrop
            size={size}
            polarity={polarity}
            fillColor={fillColor}
            fillOpacity={1}
          />
        </div>
        <div className="absolute inset-0 grid place-items-center">
          <div
            className="pbbls-visual size-full"
            style={scaleStyle}
            dangerouslySetInnerHTML={{ __html: inked }}
          />
        </div>
      </div>
    </div>
  )
}

/**
 * The stone laid on the print. Absolutely positioned by the caller's `relative`
 * box, centred and pulled up so it breaks the paper's top edge.
 */
export function PolaroidStone({
  pebble,
  mark,
  size,
  dark,
  className,
}: {
  pebble: Pebble
  mark?: Mark
  size: StoneSize
  dark: boolean
  className?: string
}) {
  const { paletteByEmotionId } = useEmotionPalettes()
  const palette = paletteByEmotionId.get(pebble.emotion_id)

  if (!palette) return null

  const { fillColor, strokeColor } = stoneColors(
    palette,
    SANDBOX_DARK_COLORS.get(pebble.emotion_id),
    dark,
  )

  return (
    <div
      className={cn(
        "pointer-events-none absolute left-1/2 -translate-x-1/2 grid place-items-center",
        "drop-shadow-[0_1px_2px_rgba(0,0,0,0.09)]",
        BOX[size],
        className,
      )}
    >
      <StoneVisual pebble={pebble} mark={mark} fillColor={fillColor} strokeColor={strokeColor} />
    </div>
  )
}
