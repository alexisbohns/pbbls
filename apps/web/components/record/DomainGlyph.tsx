import type { MarkStroke } from "@/lib/types"
import { StrokeRenderer } from "@/components/carve/StrokeRenderer"

type DomainGlyphProps = {
  strokes: MarkStroke[]
  viewBox: string
  className?: string
}

/**
 * Renders a domain's glyph as plain strokes in its square viewBox — same model
 * as the glyphs GlyphPreview, but takes raw strokes (the domain hook returns
 * strokes + viewBox, not a full Mark).
 */
export function DomainGlyph({ strokes, viewBox, className }: DomainGlyphProps) {
  return (
    <svg viewBox={viewBox} className={className} aria-hidden="true">
      <StrokeRenderer strokes={strokes} />
    </svg>
  )
}
