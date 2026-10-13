import type { MarkStroke } from "@/lib/types"
import { CARVE_STROKE_PX } from "@/lib/config/stone-geometry"
import { WOBBLE_ENABLED, wobbleGlyphLine } from "@/lib/wobble"

type StoneGlyphProps = {
  strokes: MarkStroke[]
  /** Line weight of an engine-width (6) stroke, in px; thinner strokes scale. */
  weight?: number
  stroke?: string
  strokeOpacity?: number
  /**
   * The glyph's own viewBox side, to hand-cut its lines (the petroglyph
   * wobble). The stone's glyph is where the wobble lives: the drawn glyph is
   * all straight geometry, while the river and gem bodies stay soft.
   */
  wobbleSide?: number
  className?: string
}

/**
 * A glyph's strokes as one constant on-screen line, whatever the stone's scale
 * (`non-scaling-stroke`). The engine draws strokes at width 6; a stroke drawn
 * thinner keeps its proportion.
 */
export function StoneGlyph({
  strokes,
  weight = CARVE_STROKE_PX,
  stroke,
  strokeOpacity,
  wobbleSide,
  className,
}: StoneGlyphProps) {
  return (
    <g
      fill="none"
      stroke={stroke}
      strokeOpacity={strokeOpacity}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
    >
      {strokes.map((s, i) => (
        <path
          key={i}
          d={(WOBBLE_ENABLED && wobbleSide && wobbleGlyphLine(s.d, wobbleSide)) || s.d}
          strokeWidth={`${(weight * s.width) / 6}px`}
          vectorEffect="non-scaling-stroke"
        />
      ))}
    </g>
  )
}
