import type { CSSProperties } from "react"

import type { EngineOutput } from "@/lib/engine"
import { placeGlyph } from "@/lib/engine"
import type { MarkStroke } from "@/lib/types"
import { cn } from "@/lib/utils"
import { PebbleGlyphStrokes } from "./PebbleGlyphStrokes"

type PebbleArtworkProps = {
  /** The body from `usePebbleVisual`: static templates only. */
  body: EngineOutput
  /** The resolved glyph (collection, else domain), or null for none. */
  glyph: { strokes: readonly MarkStroke[]; viewBox: string } | null
  className?: string
  style?: CSSProperties
  role?: "img"
  "aria-label"?: string
  "aria-hidden"?: boolean
}

/**
 * A pebble as the web draws it since the server stopped rendering (#1046, D6):
 * the hand-drawn body, and the resolved glyph over its glyph zone.
 *
 * Two layers in one box, sharing a viewBox so they align at every size. The
 * body is static template markup and is injected; the glyph is someone's
 * stroke data and is drawn as React children only (`PebbleGlyphStrokes`,
 * #829). Both stroke in `currentColor`, which `.pbbls-visual` sets from the
 * `--pebble-stroke-light` / `--pebble-stroke-dark` custom properties the
 * caller passes in `style`.
 */
export function PebbleArtwork({ body, glyph, className, style, ...a11y }: PebbleArtworkProps) {
  const placement = glyph ? placeGlyph(glyph.viewBox, body.glyphZone) : null

  return (
    <div data-slot="pebble-visual" className={cn("pbbls-visual relative", className)} style={style} {...a11y}>
      <div
        aria-hidden
        className="size-full [&>svg]:block [&>svg]:size-full"
        dangerouslySetInnerHTML={{ __html: body.svg }}
      />
      {glyph && placement && (
        <svg
          aria-hidden
          focusable="false"
          viewBox={body.viewBox}
          fill="none"
          className="pointer-events-none absolute inset-0 size-full"
        >
          <PebbleGlyphStrokes strokes={glyph.strokes} placement={placement} />
        </svg>
      )}
    </div>
  )
}
