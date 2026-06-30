"use client"

import { useEffect, useRef, useState } from "react"
import { PEBBLE_SHAPES } from "@/lib/config"
import type { Mark } from "@/lib/types"
import { PebbleOutline } from "@/components/carve/PebbleOutline"
import { StrokeRenderer } from "@/components/carve/StrokeRenderer"

type GlyphPreviewProps = {
  mark: Mark
  className?: string
}

/** Fraction of the shape box the glyph content fills, leaving a margin. */
const FILL = 0.82

function parseViewBox(vb: string): { x: number; y: number; w: number; h: number } {
  const [x, y, w, h] = vb.trim().split(/[\s,]+/).map(Number)
  return { x, y, w, h }
}

/** Static viewBox→shape fit used for the first paint, before measurement. */
function viewBoxFit(markViewBox: string, s: ReturnType<typeof parseViewBox>): string {
  const m = parseViewBox(markViewBox)
  const scale = Math.min((s.w * FILL) / m.w, (s.h * FILL) / m.h)
  return `translate(${s.x + s.w / 2 - scale * (m.x + m.w / 2)} ${s.y + s.h / 2 - scale * (m.y + m.h / 2)}) scale(${scale})`
}

export function GlyphPreview({ mark, className }: GlyphPreviewProps) {
  const shape = PEBBLE_SHAPES.find((s) => s.id === mark.shape_id)
  const groupRef = useRef<SVGGElement>(null)
  const [transform, setTransform] = useState<string | undefined>(
    shape ? viewBoxFit(mark.viewBox, parseViewBox(shape.viewBox)) : undefined,
  )

  // Centre the glyph's ACTUAL rendered content into the pebble slot, whatever
  // coordinate space it was authored in (uploaded glyphs are a 0 0 100 100
  // square; carved glyphs are in shape space; legacy glyphs may be anything).
  // Measuring the geometry makes centering correct regardless of the stored
  // viewBox, matching how the admin preview centres a glyph in its square.
  useEffect(() => {
    if (!shape || !groupRef.current) return
    let bb: DOMRect
    try {
      bb = groupRef.current.getBBox()
    } catch {
      return
    }
    if (!bb.width || !bb.height) return
    const s = parseViewBox(shape.viewBox)
    const scale = Math.min((s.w * FILL) / bb.width, (s.h * FILL) / bb.height)
    const cx = bb.x + bb.width / 2
    const cy = bb.y + bb.height / 2
    setTransform(`translate(${s.x + s.w / 2 - scale * cx} ${s.y + s.h / 2 - scale * cy}) scale(${scale})`)
    // mark.strokes identity changes when the glyph changes; re-measure then.
  }, [shape, mark.strokes])

  if (!shape) {
    return (
      <svg viewBox={mark.viewBox} className={className} aria-hidden="true">
        <StrokeRenderer strokes={mark.strokes} />
      </svg>
    )
  }

  const clipId = `glyph-${mark.id}`

  return (
    <svg viewBox={shape.viewBox} className={className} aria-hidden="true">
      <PebbleOutline shape={shape} clipId={clipId} />
      <g clipPath={`url(#${clipId})`}>
        <g ref={groupRef} transform={transform}>
          <StrokeRenderer strokes={mark.strokes} />
        </g>
      </g>
    </svg>
  )
}
