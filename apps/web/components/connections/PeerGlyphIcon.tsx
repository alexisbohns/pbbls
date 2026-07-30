import type { PeerGlyph } from "@/lib/types"
import { StrokeRenderer } from "@/components/carve/StrokeRenderer"

type PeerGlyphIconProps = {
  glyph: PeerGlyph
  className?: string
}

/**
 * `GlyphPreview` for the cross-user peer projection: the connections RPCs
 * expose only strokes + view_box (no Mark id/created_at, design D5/D7), so
 * this renders the narrow `PeerGlyph` shape directly. Decorative — the peer's
 * name is always rendered alongside, hence `aria-hidden`.
 */
export function PeerGlyphIcon({ glyph, className }: PeerGlyphIconProps) {
  return (
    <svg viewBox={glyph.viewBox} className={className} aria-hidden="true">
      <StrokeRenderer strokes={glyph.strokes} />
    </svg>
  )
}
