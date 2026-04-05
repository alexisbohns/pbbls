import type { PebbleParams, RenderTier, EngineOutput } from "./types"
import { getTemplate } from "./templates"
import { renderGlyphPaths } from "./glyph"

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
 * Render a complete pebble as a self-contained SVG string.
 *
 * Composes a doodle pebble by selecting a hand-drawn template based
 * on intensity and positiveness, swapping in the user's glyph,
 * toggling fossil visibility, and recolouring to the emotion colour.
 *
 * Pure function — no DOM or React dependencies.
 */
export function renderPebble(
  params: PebbleParams,
  _seed: number,
  _tier: RenderTier,
): EngineOutput {
  const template = getTemplate(params.intensity, params.positiveness)
  let svg = template.svg

  // 1. Replace placeholder glyph with user's mark (or empty it)
  if (params.glyph && params.glyph.strokes.length > 0) {
    const glyphContent = renderGlyphPaths(params.glyph, template.glyphZone)
    svg = svg.replace(GLYPH_RE, `$1${glyphContent}$2`)
  } else {
    svg = svg.replace(GLYPH_RE, "$1$2")
  }

  // 2. Remove fossil region when not retroactive
  if (!params.retroactive) {
    svg = svg.replace(FOSSIL_RE, "")
  }

  // 3. Recolour all strokes and fills from black to emotion colour
  svg = recolor(svg, params.emotionColor)

  return { svg, viewBox: template.viewBox }
}

// ── Helpers ──────────────────────────────────────────────────────

function recolor(svg: string, color: string): string {
  return svg
    .replaceAll('stroke="black"', `stroke="${color}"`)
    .replaceAll('fill="black"', `fill="${color}"`)
}
