/**
 * Pebble Engine · Compositor
 *
 * Layers a pebble shape, glyph, and optional fossil into a single
 * monochrome SVG with stroke IDs.
 *
 * Runs SERVER-SIDE as a Supabase Edge Function (Deno).
 * Called by create_pebble / update_pebble RPCs.
 *
 * Pure function. No DOM. No side effects.
 * All SVG manipulation is string-based.
 */

import type {
  PebbleEngineInput,
  PebbleEngineOutput,
} from "./types.ts";
import { resolveLayout } from "./layout.ts";

// ── SVG Parsing Helpers ─────────────────────────────────────

/**
 * Extract the inner content of an <svg> tag.
 * Returns everything between <svg ...> and </svg>.
 */
function extractSvgInner(svgString: string): string {
  const match = svgString.match(/<svg[^>]*>([\s\S]*?)<\/svg>/i);
  return match ? match[1].trim() : svgString.trim();
}

/**
 * Extract the viewBox from an SVG string.
 */
function extractViewBox(svgString: string): string | null {
  const match = svgString.match(/viewBox=["']([^"']+)["']/);
  return match ? match[1] : null;
}

/**
 * Strip all fill attributes and force fill="none" on shape elements.
 * Inject fill="none" on elements that have no fill attribute
 * (SVG defaults to fill="black").
 */
function stripFills(svgInner: string): string {
  let result = svgInner
    // Replace existing fills with none
    .replace(/fill="[^"]*"/g, 'fill="none"')
    // Kill inline style fills
    .replace(/fill:\s*[^;"]+/g, "fill: none");

  // Inject fill="none" on bare elements (no fill attribute → SVG defaults to black)
  result = result.replace(
    /<(path|circle|ellipse|rect|polygon|polyline)(\s)(?![^>]*fill=)/gi,
    '<$1$2fill="none" '
  );

  return result;
}

/**
 * Replace all stroke colors with "currentColor" (monochrome output).
 * The client applies the emotion color at render time.
 */
function monochromeStrokes(svgInner: string): string {
  return svgInner
    .replace(/stroke="[^"]*"/g, 'stroke="currentColor"')
    .replace(/stroke:\s*[^;"]+/g, "stroke: currentColor");
}

/**
 * Prefix IDs on path/element attributes to namespace layers.
 * Strips any existing `id="…"` from the matched tag first, then adds
 * `id="<prefix>:stroke-N"`. This handles both raw SVG paths (no id) and
 * pre-namespaced glyph paths from createGlyphArtwork (which already writes
 * `id="glyph:stroke-N"` and would otherwise duplicate the attribute).
 */
function namespaceIds(svgInner: string, prefix: string): string {
  let index = 0;
  return svgInner.replace(/<path\b([^>]*)>/g, (_match, attrs: string) => {
    const stripped = attrs.replace(/\s*id="[^"]*"/, "");
    return `<path id="${prefix}:stroke-${index++}"${stripped}>`;
  });
}

// ── Main Compose Function ───────────────────────────────────

/**
 * Compose a pebble from its layers.
 *
 * Stacks shape → fossil (optional) → glyph into a single SVG.
 * All strokes are monochrome (currentColor). No fills.
 *
 * @param input — Shape SVG, glyph SVG, fossil SVG, size, valence.
 * @returns     — Composed SVG + canvas size.
 */
export function composePebble(input: PebbleEngineInput): PebbleEngineOutput {
  const {
    size,
    valence,
    shapeSvg,
    glyphSvg,
    fossilSvg,
    layoutOverride,
  } = input;

  // Resolve layout
  const layout = resolveLayout(size, valence, layoutOverride);
  const { canvas, glyphSlot } = layout;

  // ── Process shape layer ─────────────────────────────────
  const shapeInner = extractSvgInner(shapeSvg);
  const shapeClean = namespaceIds(monochromeStrokes(stripFills(shapeInner)), "shape");

  // ── Process fossil layer (optional) ─────────────────────
  let fossilLayer = "";
  if (fossilSvg) {
    const fossilInner = extractSvgInner(fossilSvg);
    const fossilClean = namespaceIds(monochromeStrokes(stripFills(fossilInner)), "fossil");
    fossilLayer = `\n  <g id="layer:fossil" opacity="0.3">\n    ${fossilClean}\n  </g>`;
  }

  // ── Process glyph layer ─────────────────────────────────
  // Flatten the glyph placement into a single <g transform> instead of a
  // nested <svg viewBox>. SVGView (iOS) doesn't handle nested <svg> elements
  // with their own viewBox correctly, so we compute the viewBox→slot scale
  // ourselves and apply it as part of the transform chain.
  const glyphInner = extractSvgInner(glyphSvg);
  const glyphViewBox = extractViewBox(glyphSvg) || "0 0 200 200";
  const glyphClean = namespaceIds(monochromeStrokes(stripFills(glyphInner)), "glyph");

  const vbParts = glyphViewBox.split(" ").map(Number);
  const vbWidth = vbParts[2] || 200;
  const vbHeight = vbParts[3] || 200;
  const slotScale = Math.min(glyphSlot.size / vbWidth, glyphSlot.size / vbHeight);

  const glyphLayer = [
    `  <g id="layer:glyph" transform="translate(${glyphSlot.x}, ${glyphSlot.y}) scale(${Math.round(slotScale * 1000) / 1000})">`,
    `    ${glyphClean}`,
    `  </g>`,
  ].join("\n");

  // ── Compose final SVG ───────────────────────────────────
  const svg = [
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${canvas.width} ${canvas.height}" width="${canvas.width}" height="${canvas.height}">`,
    `  <g id="layer:shape">`,
    `    ${shapeClean}`,
    `  </g>`,
    fossilLayer,
    glyphLayer,
    `</svg>`,
  ].join("\n");

  return { svg, canvas };
}
