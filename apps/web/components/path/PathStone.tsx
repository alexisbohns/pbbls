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
import { WOBBLE_ENABLED, wobblePebbleSvg } from "@/lib/wobble"
import { cn } from "@/lib/utils"

/** How big the stone sits on a card. Steps down for a small pebble, so intensity
 *  still reads on a wall where every card is the same column width. */
export type StoneSize = "sm" | "md" | "lg"

const BOX: Record<StoneSize, string> = {
  sm: "size-11",
  md: "size-14",
  lg: "size-18",
}

/**
 * The stone laid on a polaroid — the outline silhouette with the composed pebble
 * scaled inside it, the same construction as `PebbleFramed`.
 *
 * Built separately from `PebbleFramed` because of how colour has to reach the
 * strokes here. A pebble with no server render takes the client-engine fallback,
 * and that path bakes a flat hex into the markup via the engine's `recolor()` —
 * `PebbleVisual` documents that it ignores `strokeOverride` there for exactly that
 * reason. Substituting `currentColor` back into the composed SVG is what lets the
 * theme swap happen in CSS (see `.pbbls-visual` in globals.css) rather than from a
 * JS-read theme, which would desync between server and client render.
 *
 * On the wall a stone fills with the palette's pale `light` colour and draws in
 * `primary`; dark mode fills with `dark` and draws in `secondary`. Both ends are
 * emitted as custom properties and picked by the `.dark` cascade.
 */
export function PathStone({
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
  // Fallback only — the hook has to be called unconditionally, but a pebble with
  // a server render never uses its output.
  const fallback = usePebbleVisual(pebble, mark ?? null, "thumbnail")

  if (!palette) return null

  const rgb = (hex: string) => (hex.length === 9 ? hex.slice(0, 7) : hex)
  const pebbleSize = SIZE_BY_INTENSITY[pebble.intensity]
  const polarity = POLARITY_BY_VALENCE[pebble.positiveness]

  // Same precedence as PebbleVisual: prefer the server-composed render written by
  // the compose-pebble edge function, and fall back to the client engine only for
  // legacy rows and anonymous previews. Reading the fallback unconditionally threw
  // away the composed artwork — which is where the carved glyph lives — so real
  // pebbles rendered as bare outlines.
  const isServerRender = pebble.render_svg !== null
  const raw = pebble.render_svg ?? fallback.svg

  // Petroglyph wobble (#555), dev-only and content-cached, exactly as PebbleVisual
  // applies it. Without this the wall drew clean strokes while the detail sheet
  // drew leaky ink for the same pebble.
  const wobbled = WOBBLE_ENABLED ? wobblePebbleSvg(raw) : raw

  // Server renders already stroke with `currentColor`. Only the client-engine
  // fallback bakes a flat hex in, via the engine's own `recolor()`; swapping it
  // back is what lets the `.pbbls-visual` rule below theme it.
  const baked = EMOTIONS.find((e) => e.id === pebble.emotion_id)?.color ?? "#9CA3AF"
  const inked = isServerRender ? wobbled : wobbled.replaceAll(baked, "currentColor")

  const ar = outlineAspectRatio(pebbleSize)
  // Fit a box at the outline's aspect ratio inside the caller's square box — the
  // CSS equivalent of SwiftUI `.aspectRatio(ar, .fit)`.
  const fitStyle: CSSProperties =
    ar <= 1 ? { height: "100%", aspectRatio: ar } : { width: "100%", aspectRatio: ar }

  const inkStyle: CSSProperties = {
    ["--pebble-stroke-light"]: rgb(palette.primary_color),
    ["--pebble-stroke-dark"]: rgb(palette.secondary_color),
    transform: `scale(${pebbleScale(pebbleSize)})`,
    transformOrigin: "center",
  } as CSSProperties

  // `dark_color` is not on EmotionPalette — the app's dark swap is CSS-only, so no
  // consumer has needed it in JS. `secondary` at low opacity stands in as the dark
  // field until the column is projected into the palette hook.
  const fillStyle: CSSProperties = {
    ["--path-stone-fill-light"]: rgb(palette.light_color),
    ["--path-stone-fill-dark"]: rgb(palette.secondary_color),
  } as CSSProperties

  return (
    <div
      className={cn(
        "pointer-events-none absolute left-1/2 grid -translate-x-1/2 place-items-center",
        "drop-shadow-[0_1px_2px_rgba(0,0,0,0.09)]",
        BOX[size],
        className,
      )}
    >
      <div className="grid size-full place-items-center">
        <div className="relative" style={fitStyle}>
          <div className="path-stone-fill absolute inset-0" style={fillStyle}>
            {/* fill="currentColor" so the silhouette takes its colour from CSS,
                which is what lets the `.dark` cascade swap it. */}
            <PebbleOutlineBackdrop
              size={pebbleSize}
              polarity={polarity}
              fillColor="currentColor"
              fillOpacity={1}
            />
          </div>
          <div className="absolute inset-0 grid place-items-center">
            <div
              className="pbbls-visual size-full"
              style={inkStyle}
              dangerouslySetInnerHTML={{ __html: inked }}
            />
          </div>
        </div>
      </div>
    </div>
  )
}
