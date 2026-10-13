import type { MarkStroke } from "@/lib/types"
import type { GlyphPlacement } from "@/lib/engine"
import { WOBBLE_ENABLED, wobbleGlyphInk } from "@/lib/wobble"

type PebbleGlyphStrokesProps = {
  strokes: readonly MarkStroke[]
  placement: GlyphPlacement
}

/**
 * A pebble's resolved glyph, drawn into its template's glyph zone.
 *
 * This is the #829 stored-XSS boundary on web. The strokes are jsonb their
 * owner writes directly, and since the moments split they reach other people:
 * a connection's tile and the anonymous `/p/[id]` page draw the sharer's
 * collection glyph. Each `d` therefore only ever travels as a React `<path d>`
 * attribute, which React escapes, one element per stroke — never through a
 * string that reaches `dangerouslySetInnerHTML`. The gate is
 * `lib/engine/glyph-xss.test.ts`.
 *
 * Every stroke lands at the outline's weight (the glyph model: the stored
 * width is always 6 in glyph space, and the apparent weight comes from the
 * fit), so the stored `width` is never read.
 */
export function PebbleGlyphStrokes({ strokes, placement }: PebbleGlyphStrokesProps) {
  return (
    <g transform={placement.transform}>
      {strokes.map((stroke, i) => {
        // Petroglyph wobble (#555): the stroke becomes leaky filled ink at the
        // outline weight, as the template body does. Falls back to the plain
        // stroke when the `d` does not parse — a hostile `d` among them.
        const ink = WOBBLE_ENABLED ? wobbleGlyphInk(stroke.d, placement.strokeWidth) : null
        if (ink) return <path key={i} d={ink} fill="currentColor" stroke="none" />
        return (
          <path
            key={i}
            d={stroke.d}
            fill="none"
            stroke="currentColor"
            strokeWidth={placement.strokeWidth}
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        )
      })}
    </g>
  )
}
