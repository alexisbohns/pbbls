import type { PebbleParams, EngineOutput } from "./types"
import { getTemplate } from "./templates"

// ── Regex patterns ───────────────────────────────────────────────

/** Matches the inner content of <g id="glyph">...</g>. */
const GLYPH_RE = /(<g id="glyph">)[\s\S]*?(<\/g>\s*<\/g>)/

/**
 * Matches only the fossil `<path>` element (self-closing).
 * Decorative strokes (Vector 16, 17, etc.) are left intact — they
 * should always be visible regardless of the retroactive flag.
 */
const FOSSIL_RE = /\s*<path id="fossil"[^>]*\/>/

// ── Pipeline ─────────────────────────────────────────────────────

/**
 * Render a pebble's body as a self-contained SVG string: the hand-drawn
 * template for its intensity and positiveness, its placeholder glyph emptied,
 * the fossil toggled, recoloured to `emotionColor`.
 *
 * The string holds nothing but the static templates and `emotionColor` (a
 * config hex or `currentColor`), which is what makes it safe to inject. The
 * pebble's glyph is NOT in it: since the moments split the glyph is resolved
 * server-side from a collection or a domain and can be someone else's drawing,
 * so it is drawn as React children over `glyphZone` instead
 * (`PebbleGlyphStrokes`, #829).
 *
 * Pure function — no DOM or React dependencies.
 */
export function renderPebble(params: PebbleParams): EngineOutput {
  const template = getTemplate(params.intensity, params.positiveness)

  // 1. Empty the placeholder glyph: the real one is drawn over the zone.
  let svg = template.svg.replace(GLYPH_RE, "$1$2")

  // 2. Remove fossil region when not retroactive
  if (!params.retroactive) {
    svg = svg.replace(FOSSIL_RE, "")
  }

  // 3. Recolour all strokes and fills from black to emotion colour
  svg = recolor(svg, params.emotionColor)

  return { svg, viewBox: template.viewBox, glyphZone: template.glyphZone }
}

// ── Helpers ──────────────────────────────────────────────────────

function recolor(svg: string, color: string): string {
  return svg
    .replaceAll('stroke="black"', `stroke="${color}"`)
    .replaceAll('fill="black"', `fill="${color}"`)
}
