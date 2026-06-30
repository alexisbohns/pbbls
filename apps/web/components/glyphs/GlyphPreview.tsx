import { PEBBLE_SHAPES } from "@/lib/config"
import type { Mark } from "@/lib/types"
import { PebbleOutline } from "@/components/carve/PebbleOutline"
import { StrokeRenderer } from "@/components/carve/StrokeRenderer"

type GlyphPreviewProps = {
  mark: Mark
  className?: string
}

function parseViewBox(vb: string): { x: number; y: number; w: number; h: number } {
  const [x, y, w, h] = vb.trim().split(/[\s,]+/).map(Number)
  return { x, y, w, h }
}

/**
 * Uniform scale + centre fitting the glyph's own viewBox into the shape's
 * viewBox. Glyph strokes live in their OWN coordinate space: carved glyphs
 * match the shape viewBox (→ identity, unchanged), while uploaded glyphs are a
 * canonical `0 0 100 100` square that must be scaled + centred into the slot.
 */
function fitTransform(markViewBox: string, shapeViewBox: string): string {
  const m = parseViewBox(markViewBox)
  const s = parseViewBox(shapeViewBox)
  const scale = Math.min(s.w / m.w, s.h / m.h)
  const offsetX = s.x + (s.w - m.w * scale) / 2 - m.x * scale
  const offsetY = s.y + (s.h - m.h * scale) / 2 - m.y * scale
  return `translate(${offsetX}, ${offsetY}) scale(${scale})`
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
        <g transform={fitTransform(mark.viewBox, shape.viewBox)}>
          <StrokeRenderer strokes={mark.strokes} />
        </g>
      </g>
    </svg>
  )
}
