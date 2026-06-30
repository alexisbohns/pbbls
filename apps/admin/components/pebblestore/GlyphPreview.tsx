// apps/admin/components/pebblestore/GlyphPreview.tsx
import type { Adjust, GlyphStroke, PebbleShape } from "@/lib/pebblestore/types"
import { buildAdjustMatrix, matrixToTransform } from "@/lib/pebblestore/transform-path"
import { fitTransform, parseViewBox } from "@/lib/pebblestore/render-preview"

const STROKE_WIDTH = 6

type Props = {
  strokes: GlyphStroke[]
  glyphViewBox: string
  shape: PebbleShape | null
  adjust?: Adjust
  className?: string
}

export function GlyphPreview({ strokes, glyphViewBox, shape, adjust, className }: Props) {
  // Shape provides the canvas + outline; fall back to the glyph's own box if absent.
  const canvasViewBox = shape?.view_box ?? glyphViewBox
  const zone = parseViewBox(canvasViewBox)
  const fit = fitTransform(glyphViewBox, zone)
  const adjustTransform = adjust ? matrixToTransform(buildAdjustMatrix(glyphViewBox, adjust)) : undefined

  // Pre-divide so the fit scale yields ~STROKE_WIDTH px in canvas coords.
  const fitScale = Math.min(zone.width / parseViewBox(glyphViewBox).width, zone.height / parseViewBox(glyphViewBox).height)
  const strokeWidth = STROKE_WIDTH / (fitScale || 1)

  return (
    <svg
      viewBox={canvasViewBox}
      className={className}
      role="img"
      aria-label="Glyph preview"
    >
      {shape ? (
        <path d={shape.path} fill="none" className="text-muted-foreground/40" stroke="currentColor" strokeWidth={2} />
      ) : null}
      <g transform={fit}>
        <g transform={adjustTransform}>
          {strokes.map((s, i) => (
            <path
              key={i}
              d={s.d}
              fill="none"
              stroke="currentColor"
              strokeWidth={strokeWidth}
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          ))}
        </g>
      </g>
    </svg>
  )
}
