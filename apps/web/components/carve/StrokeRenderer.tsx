import type { MarkStroke } from "@/lib/types"
import { WOBBLE_ENABLED, wobbleGlyphInk } from "@/lib/wobble"

type StrokeRendererProps = {
  strokes: MarkStroke[]
  className?: string
}

export function StrokeRenderer({ strokes, className }: StrokeRendererProps) {
  const strokeClass = className ?? "text-foreground"
  return (
    <>
      {strokes.map((stroke, i) => {
        // Petroglyph wobble (#555): dev-only. Committed strokes render as leaky
        // filled ink (canonical params, content-cached); the in-progress carve
        // stroke is a separate ActiveStroke, so drag latency is untouched.
        // Falls back to the plain stroked path when the `d` doesn't parse.
        const ink = WOBBLE_ENABLED ? wobbleGlyphInk(stroke.d, stroke.width) : null
        if (ink) {
          return <path key={i} d={ink} fill="currentColor" className={strokeClass} />
        }
        return (
          <path
            key={i}
            d={stroke.d}
            fill="none"
            stroke="currentColor"
            strokeWidth={stroke.width}
            strokeLinecap="round"
            strokeLinejoin="round"
            className={strokeClass}
          />
        )
      })}
    </>
  )
}
