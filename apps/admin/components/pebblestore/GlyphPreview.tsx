// apps/admin/components/pebblestore/GlyphPreview.tsx
import { DEFAULT_STROKE_WIDTH, type GlyphStroke } from "@/lib/pebblestore/types"

type Props = {
  /** Strokes to render, already baked (the live editor passes adjusted strokes). */
  strokes: GlyphStroke[]
  /** The glyph's square viewBox (e.g. "0 0 100 100"). */
  viewBox: string
  className?: string
}

/**
 * Renders a glyph inside its canonical square viewBox. The stroke is a CONSTANT
 * 6 units in glyph space (never scaled by adjust) — the SVG viewBox→viewport
 * mapping uniformly scales the whole drawing, including the stroke, exactly as
 * the pebble slot does at render time. Glyphs are shape-agnostic, so there is no
 * pebble-outline clip here.
 */
export function GlyphPreview({ strokes, viewBox, className }: Props) {
  return (
    <svg viewBox={viewBox} className={className} role="img" aria-label="Glyph preview">
      {strokes.map((s, i) => (
        <path
          key={i}
          d={s.d}
          fill="none"
          stroke="currentColor"
          strokeWidth={DEFAULT_STROKE_WIDTH}
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      ))}
    </svg>
  )
}
