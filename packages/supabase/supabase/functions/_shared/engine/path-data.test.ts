/**
 * Path-data grammar — hostile-input fixtures for #829.
 *
 * Run: deno test packages/supabase/supabase/functions/_shared/engine/
 */

import { assertEquals, assert } from "https://deno.land/std@0.224.0/assert/mod.ts";

import { sanitizePathData } from "./path-data.ts";
import { createGlyphArtwork } from "./glyph.ts";
import { composePebble } from "./compose.ts";
import { getShape } from "./shapes/index.ts";
import type { Stroke } from "./types.ts";

// ── The payload from the finding ────────────────────────────

/** The attribute breakout Kritik reproduced end to end. */
const BREAKOUT = `M0 0"/><foreignObject><img src=x onerror=alert(document.domain)></foreignObject><path d="`;

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
];

Deno.test("sanitizePathData rejects every hostile fixture outright", () => {
  for (const d of HOSTILE) {
    assertEquals(sanitizePathData(d), "", `should have rejected: ${d}`);
  }
});

Deno.test("sanitizePathData rejects non-string and malformed input", () => {
  for (const d of [undefined, null, 42, {}, [], "", "   ", "L10 10", "10 10", "Z"]) {
    assertEquals(sanitizePathData(d), "", `should have rejected: ${JSON.stringify(d)}`);
  }
});

Deno.test("sanitizePathData preserves real geometry", () => {
  assertEquals(sanitizePathData("M 20 100 L 180 100"), "M20 100 L180 100");
  assertEquals(sanitizePathData("M20,100Q100,20 180,100"), "M20 100 Q100 20 180 100");
  // Repeated argument sets: an implicit lineto after a moveto, an implicit
  // curveto after a curveto.
  assertEquals(sanitizePathData("M0 0 10 10 20 20"), "M0 0 L10 10 L20 20");
  assertEquals(
    sanitizePathData("M0 0 C1 1 2 2 3 3 4 4 5 5 6 6"),
    "M0 0 C1 1 2 2 3 3 C4 4 5 5 6 6",
  );
  // Arc flags are single characters, unseparated from what follows.
  assertEquals(sanitizePathData("M0 0 a5 5 0 1 0 10 0"), "M0 0 a5 5 0 1 0 10 0");
  assertEquals(sanitizePathData("M0 0 H10 V10 Z"), "M0 0 H10 V10 Z");
  assertEquals(sanitizePathData("M-.5 .5 L1e2 2"), "M-0.5 0.5 L100 2");
});

Deno.test("sanitizePathData rejects out-of-range and non-finite coordinates", () => {
  assertEquals(sanitizePathData("M0 0 L1e400 0"), "");
  assertEquals(sanitizePathData("M0 0 L1e9 0"), "");
  assertEquals(sanitizePathData("M0 0 L NaN 0"), "");
});

Deno.test("sanitizePathData rejects arc flags that are not 0 or 1", () => {
  assertEquals(sanitizePathData("M0 0 A5 5 0 2 0 10 0"), "");
});

// ── The full composition path ───────────────────────────────

/** Everything a delivered `render_svg` must never contain. */
function assertInert(svg: string, label: string): void {
  for (const forbidden of [
    "foreignObject", "<script", "onerror", "onload", "javascript:",
    "<img", "<use", "<animate", "alert(",
  ]) {
    assert(
      !svg.toLowerCase().includes(forbidden.toLowerCase()),
      `${label}: composed svg contains ${forbidden}\n${svg}`,
    );
  }
  // No attribute may have been broken out of: every quote in the document
  // belongs to an attribute, so they must come in pairs.
  assert((svg.match(/"/g) ?? []).length % 2 === 0, `${label}: unbalanced quotes`);
}

Deno.test("a hostile glyph composes to inert markup", () => {
  for (const d of HOSTILE) {
    const strokes: Stroke[] = [
      { d: "M 20 100 L 180 100", width: 3 },
      { d, width: 3 },
    ];
    const artwork = createGlyphArtwork(strokes);
    assertInert(artwork.svg, `artwork(${d})`);

    const { svg } = composePebble({
      size: "medium",
      valence: "neutral",
      shapeSvg: getShape("medium", "neutral"),
      glyphSvg: artwork.svg,
    });
    assertInert(svg, `composed(${d})`);
    // The honest stroke survives — the bad one is dropped, not the glyph.
    assert(svg.includes('id="glyph:stroke-0"'), "the valid stroke should still render");
    assert(!svg.includes('id="glyph:stroke-1"'), "the rejected stroke should be gone");
  }
});

Deno.test("a glyph whose every stroke is hostile composes to a blank square", () => {
  const artwork = createGlyphArtwork([{ d: BREAKOUT, width: 3 }]);
  assertInert(artwork.svg, "all-hostile artwork");
  assert(!artwork.svg.includes("<path"), "no path should be emitted");
  assertEquals(artwork.viewBox, "0 0 200 200");
});

Deno.test("a hostile stroke width cannot reach the stroke-width attribute", () => {
  const artwork = createGlyphArtwork([
    // `width` is typed as a number, but the column is jsonb — the value on the
    // wire is whatever its owner PATCHed.
    { d: "M 20 100 L 180 100", width: '3" onload="alert(1)' as unknown as number },
  ]);
  assertInert(artwork.svg, "hostile width");
  assert(/stroke-width="[0-9.]+"/.test(artwork.svg), "width should fall back to a number");
});
