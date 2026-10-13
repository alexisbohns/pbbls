import { createElement } from "react"
import { renderToStaticMarkup } from "react-dom/server"
import { describe, expect, it } from "vitest"

import { PebbleArtwork } from "@/components/pebble/PebbleArtwork"
import { PebbleGlyphStrokes } from "@/components/pebble/PebbleGlyphStrokes"
import { pebbleGlyphFromRow } from "@/lib/data/pebble-row"
import type { MarkStroke } from "@/lib/types"
import { placeGlyph } from "./glyph"
import { renderPebble } from "./render"
import { toPebbleParams } from "./params"

/**
 * The #829 stored-XSS gate, restored on web (#1049).
 *
 * The server used to compose the glyph into `render_svg` and guarded it with
 * `_shared/engine/path-data.test.ts`. Since the moments split (#1046) the
 * server sends the resolved glyph's raw strokes instead, to the owner, to a
 * connection's tile and to the anonymous `/p/[id]` page, and the client draws
 * them. A glyph's strokes are jsonb their owner writes directly, so whatever
 * turns them into drawing is now the sink. These are the server gate's hostile
 * fixtures, run against the web renderer: each must come out as exactly one
 * `<path>` per stroke with its `d` escaped inside the attribute, never as
 * markup of its own.
 */

/** The attribute breakout Kritik reproduced end to end. */
const BREAKOUT = `M0 0"/><foreignObject><img src=x onerror=alert(document.domain)></foreignObject><path d="`

const HOSTILE: string[] = [
  BREAKOUT,
  `M0 0" onload="alert(1)`,
  `M0 0'/><script>alert(1)</script><path d='`,
  `M0 0"/><use href="data:image/svg+xml;base64,PHN2Zz4="/><path d="`,
  `M0 0"/><animate attributeName="href" values="javascript:alert(1)"/><path d="`,
  `<script>alert(1)</script>`,
  `M0 0 L10 10 <!-- -->`,
  `M0 0 L10 10"`,
  `M0 0 L10 10&lt;`,
]

const HONEST: MarkStroke = { d: "M 20 100 L 180 100", width: 6 }

const PLACEMENT = placeGlyph("0 0 200 200", { x: 16, y: 88, width: 160, height: 160 })!

/** Every element the glyph layer and the template body may emit. */
const ALLOWED_TAG = /^<\/?(div|svg|g|path)[\s/>]/

function strokesMarkup(strokes: MarkStroke[]): string {
  return renderToStaticMarkup(
    createElement("svg", null, createElement(PebbleGlyphStrokes, { strokes, placement: PLACEMENT })),
  )
}

function artworkMarkup(strokes: MarkStroke[]): string {
  const body = renderPebble(toPebbleParams({ intensity: 2, positiveness: 0, happened_at: "2026-10-01T10:00:00Z" }))
  return renderToStaticMarkup(
    createElement(PebbleArtwork, { body, glyph: { strokes, viewBox: "0 0 200 200" }, "aria-hidden": true }),
  )
}

function assertInert(markup: string, label: string): void {
  expect(markup, label).not.toMatch(/<script/i)
  expect(markup, label).not.toMatch(/<foreignObject/i)
  // Nothing but the drawing's own elements: no <img>, <use>, <animate>, <a>…
  for (const tag of markup.match(/<[^>]*>/g) ?? []) {
    expect(tag, `${label}: unexpected element`).toMatch(ALLOWED_TAG)
  }
  // No attribute was broken out of: every quote belongs to an attribute, so
  // they come in pairs.
  expect((markup.match(/"/g) ?? []).length % 2, `${label}: unbalanced quotes`).toBe(0)
}

describe("glyph strokes renderer (#829 gate)", () => {
  it("draws exactly one <path> per stroke, hostile or not", () => {
    for (const d of HOSTILE) {
      const markup = strokesMarkup([HONEST, { d, width: 6 }])
      assertInert(markup, d)
      expect(markup.match(/<path\b/g), d).toHaveLength(2)
    }
  })

  it("draws a glyph whose every stroke is hostile as inert paths", () => {
    const strokes = HOSTILE.map((d) => ({ d, width: 6 }))
    const markup = strokesMarkup(strokes)
    assertInert(markup, "all hostile")
    expect(markup.match(/<path\b/g)).toHaveLength(HOSTILE.length)
  })

  it("never reads the stored width, so a hostile one cannot reach an attribute", () => {
    const hostileWidth = '3" onload="alert(1)' as unknown as number
    const markup = strokesMarkup([{ d: HONEST.d, width: hostileWidth }])
    assertInert(markup, "hostile width")
    expect(markup).not.toContain("onload")
  })

  it("keeps the glyph out of the injected template body", () => {
    for (const d of HOSTILE) {
      const markup = artworkMarkup([HONEST, { d, width: 6 }])
      assertInert(markup, `artwork(${d})`)
      // The template body has its own paths; the glyph layer adds exactly two.
      const layer = markup.slice(markup.lastIndexOf("<svg"))
      expect(layer.match(/<path\b/g), d).toHaveLength(2)
    }
  })

  it("reads a glyph embed from the wire without trusting its d, and drops a malformed one", () => {
    const glyph = pebbleGlyphFromRow({
      id: "g",
      view_box: "0 0 200 200",
      source: "collection",
      strokes: [HONEST, { d: BREAKOUT, width: 6 }],
    })
    expect(glyph?.strokes).toHaveLength(2)
    assertInert(strokesMarkup(glyph!.strokes), "from row")
    expect(pebbleGlyphFromRow({ id: "g", view_box: "0 0 200 200", strokes: [{ d: BREAKOUT }] })).toBeNull()
  })
})

describe("placeGlyph", () => {
  it("fits a square glyph into the zone and keeps the outline weight", () => {
    const placement = placeGlyph("0 0 200 200", { x: 16, y: 88, width: 160, height: 160 })
    expect(placement).toEqual({
      transform: "translate(16 88) scale(0.8)",
      scale: 0.8,
      strokeWidth: 7.5,
    })
  })

  it("refuses an unreadable viewBox rather than drawing at a guess", () => {
    expect(placeGlyph("nonsense", { x: 0, y: 0, width: 10, height: 10 })).toBeNull()
    expect(placeGlyph("0 0 0 200", { x: 0, y: 0, width: 10, height: 10 })).toBeNull()
  })
})
