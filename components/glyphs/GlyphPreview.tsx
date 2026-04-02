import { PEBBLE_SHAPES } from "@/lib/config"
import type { Mark } from "@/lib/types"
import { PebbleOutline } from "@/components/carve/PebbleOutline"
import { StrokeRenderer } from "@/components/carve/StrokeRenderer"

type GlyphPreviewProps = {
  mark: Mark
  className?: string
}

export function GlyphPreview({ mark, className }: GlyphPreviewProps) {
  const shape = PEBBLE_SHAPES.find((s) => s.id === mark.shape_id)

  if (!shape) {
    return (
      <svg
        viewBox={mark.viewBox}
        className={className}
        aria-hidden="true"
      >
        <StrokeRenderer strokes={mark.strokes} />
      </svg>
    )
  }

  const clipId = `glyph-${mark.id}`

  return (
    <svg
      viewBox={shape.viewBox}
      className={className}
      aria-hidden="true"
    >
      <PebbleOutline shape={shape} clipId={clipId} />
      <g clipPath={`url(#${clipId})`}>
        <StrokeRenderer strokes={mark.strokes} />
      </g>
    </svg>
  )
}
