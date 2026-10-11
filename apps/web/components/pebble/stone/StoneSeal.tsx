import type { CSSProperties } from "react"
import { getOutline } from "@/lib/config/pebble-outlines"
import type { Polarity, Size } from "@/lib/config/pebble-geometry"
import { canvasTransform, carveTransform, glyphSide, glyphTransform } from "@/lib/config/stone-geometry"
import { BLANK_PALETTE, stonePaletteVars, type StonePalette } from "@/lib/config/stone-tones"
import type { PebbleGlyph } from "@/lib/types"
import { stoneBodyId, stoneClipId, stoneHatchId } from "@/lib/utils/spine/stone-ids"
import { cn } from "@/lib/utils"
import { StoneGlyph } from "./StoneGlyph"

type StoneSealProps = {
  size: Size
  polarity: Polarity
  palette: StonePalette | null
  glyph: PebbleGlyph | null
  /** `mini` for the contents and the card index (20–30 px); `print` larger. */
  variant?: "mini" | "print"
  className?: string
}

// Line weights in px: glyph, glyph knock-out halo, rim.
const WEIGHT = {
  mini: { glyph: 1, halo: 2.6, rim: 1.1 },
  print: { glyph: 1.4, halo: 3.6, rim: 1.3 },
} as const

/** What the glyph is cut back to: the ground, unless a card sets its stock. */
const KO = "var(--spine-ko, var(--spine-ground))"

/**
 * The stone as a flat print (#1052, spec D7): wherever it is read rather than
 * pressed. A high moment prints in relief (solid ink, the glyph cut back to
 * the paper), an even one as an open line on a pale tint, a low one engraved
 * (hatched in the deeper ink). It prints in the pebble's spot ink, primary on
 * Paper and secondary at Night; the knock-out colour is `--spine-ko`, the
 * ground unless a card sets its stock.
 *
 * Needs `StoneSprite` once on the page.
 */
export function StoneSeal({ size, polarity: cut, palette, glyph: carved, variant = "mini", className }: StoneSealProps) {
  // A moment with no emotion yet prints as a blank, uncarved river pebble.
  const polarity: Polarity = palette ? cut : "neutral"
  const glyph = palette ? carved : null
  const outline = getOutline(size, polarity)
  const body = `#${stoneBodyId(size, polarity)}`
  const weight = WEIGHT[variant]
  const glyphPlace = glyph
    ? { carve: carveTransform(size, polarity), zone: glyphTransform(size, polarity, glyphSide(glyph.viewBox)) }
    : null
  const style = stonePaletteVars(palette ?? BLANK_PALETTE) as CSSProperties

  return (
    <svg
      aria-hidden
      focusable="false"
      viewBox={`0 0 ${outline.width} ${outline.height}`}
      className={cn(
        "spine-spot block overflow-visible",
        polarity === "lowlight" ? "text-(--spine-deep)" : "text-(--spine-spot)",
        className,
      )}
      style={style}
    >
      {polarity === "highlight" && (
        <>
          <use href={body} fill="currentColor" stroke="currentColor" strokeWidth="1px" />
          {glyph && glyphPlace && (
            <g transform={glyphPlace.carve}>
              <g transform={glyphPlace.zone}>
                <StoneGlyph strokes={glyph.strokes} weight={weight.glyph} stroke={KO} />
              </g>
            </g>
          )}
        </>
      )}

      {polarity === "neutral" && (
        <>
          <use href={body} fill={`color-mix(in oklab, currentColor 15%, ${KO})`} />
          {glyph && glyphPlace && (
            <g transform={glyphPlace.carve}>
              <g transform={glyphPlace.zone}>
                <StoneGlyph strokes={glyph.strokes} weight={weight.glyph} stroke="currentColor" />
              </g>
            </g>
          )}
        </>
      )}

      {polarity === "lowlight" && (
        <>
          <use href={body} fill={`color-mix(in oklab, currentColor 9%, ${KO})`} />
          <g clipPath={`url(#${stoneClipId(size, polarity)})`}>
            <g transform={canvasTransform(size, polarity)}>
              <use href={`#${stoneHatchId(size)}`} fill="none" stroke="currentColor" strokeWidth="0.8px" opacity={0.85} />
            </g>
          </g>
          {glyph && glyphPlace && (
            <g transform={glyphPlace.carve}>
              <g transform={glyphPlace.zone}>
                <StoneGlyph strokes={glyph.strokes} weight={weight.halo} stroke={KO} />
                <StoneGlyph strokes={glyph.strokes} weight={weight.glyph} stroke="currentColor" />
              </g>
            </g>
          )}
        </>
      )}

      {polarity !== "highlight" && (
        <use
          href={body}
          fill="none"
          stroke="currentColor"
          strokeWidth={`${weight.rim}px`}
        />
      )}
    </svg>
  )
}
