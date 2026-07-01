import type { Mark } from "@/lib/types"
import { StrokeRenderer } from "@/components/carve/StrokeRenderer"

type GlyphPreviewProps = {
  mark: Mark
  className?: string
}

/**
 * Renders a glyph as plain strokes in its own (square) viewBox — no pebble
 * shape, no clip, no background. Mirrors the canonical model (#278) and the iOS
 * GlyphThumbnail: glyphs are shapeless squares scaled into whatever slot the
 * caller provides via `className`.
 */
export function GlyphPreview({ mark, className }: GlyphPreviewProps) {
  return (
    <svg viewBox={mark.viewBox} className={className} aria-hidden="true">
      <StrokeRenderer strokes={mark.strokes} />
    </svg>
  )
}
