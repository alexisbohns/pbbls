#!/usr/bin/env -S deno run
/**
 * Engine smoke-test — runs composePebble against synthetic input for all 9
 * (size, valence) variants, asserting output shape. No DB, no network.
 *
 * Run: deno run packages/supabase/scripts/smoke-test-engine.ts
 *
 * Exit 0 on success, non-zero on any assertion failure.
 */

import { createGlyphArtwork } from "../supabase/functions/_shared/engine/glyph.ts";
import { composePebble } from "../supabase/functions/_shared/engine/compose.ts";
import { getShape } from "../supabase/functions/_shared/engine/shapes/index.ts";
import type { PebbleSize, PebbleValence, Stroke } from "../supabase/functions/_shared/engine/types.ts";

const SIZES: PebbleSize[] = ["small", "medium", "large"];
const VALENCES: PebbleValence[] = ["lowlight", "neutral", "highlight"];

const SYNTHETIC_STROKES: Stroke[] = [
  { d: "M 20 100 Q 100 20 180 100 T 340 100", width: 3 },
  { d: "M 50 50 L 150 150", width: 3 },
];

function assert(cond: boolean, msg: string): void {
  if (!cond) {
    console.error(`✗ ${msg}`);
    Deno.exit(1);
  }
}

let ok = 0;
for (const size of SIZES) {
  for (const valence of VALENCES) {
    const artwork = createGlyphArtwork(SYNTHETIC_STROKES);
    assert(artwork.svg.startsWith("<svg"), `${size}/${valence}: artwork starts with <svg`);
    assert(artwork.viewBox === "0 0 200 200", `${size}/${valence}: artwork viewBox is 200×200`);

    const shapeSvg = getShape(size, valence);
    assert(shapeSvg.startsWith("<svg"), `${size}/${valence}: shape starts with <svg`);

    const { svg } = composePebble({
      size,
      valence,
      shapeSvg,
      glyphSvg: artwork.svg,
    });

    assert(svg.startsWith("<svg"), `${size}/${valence}: composed svg starts with <svg`);
    assert(svg.includes(`<g id="layer:shape">`), `${size}/${valence}: composed svg has shape layer`);
    assert(svg.includes(`<g id="layer:glyph"`), `${size}/${valence}: composed svg has glyph layer`);

    ok += 1;
    console.log(`✓ ${size}/${valence}`);
  }
}

console.log(`\nrendered=${ok}/9`);
Deno.exit(ok === 9 ? 0 : 1);
