/**
 * Pebble Engine · Compositor
 *
 * Layers a pebble shape, glyph, and optional fossil into a single
 * monochrome SVG with stroke IDs, plus an animation manifest.
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
  AnimationManifest,
  AnimationManifestLayer,
  CanvasSize,
  GlyphSlot,
} from "./types.ts";
import { resolveLayout } from "./layout.ts";

// ── Config ──────────────────────────────────────────────────

/** Default animation timing (ms). Tunable per engine version. */
const TIMING = {
  glyph:  { delay: 0,    duration: 800 },
  shape:  { delay: 600,  duration: 800 },
  fossil: { delay: 1000, duration: 600 },
  fill:   { delay: 1200, duration: 600 },
  settle: { delay: 1600, duration: 400 },
} as const;

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
 * Extract all <path> `d` attributes from an SVG string.
 * Used to build the animation manifest.
 */
function extractPaths(svgString: string): Array<{ d: string }> {
  const paths: Array<{ d: string }> = [];
  const regex = /d="([^"]+)"/g;
  let match: RegExpExecArray | null;
  while ((match = regex.exec(svgString)) !== null) {
    paths.push({ d: match[1] });
  }
  return paths;
}

/**
 * Estimate the length of an SVG path from its `d` string.
 * Rough approximation — sums the Euclidean distances between
 * extracted coordinate points. Good enough for animation timing.
 */
function estimatePathLength(d: string): number {
  const numbers = d.match(/-?\d+\.?\d*/g);
  if (!numbers || numbers.length < 4) return 100; // fallback

  let length = 0;
  let prevX = parseFloat(numbers[0]);
  let prevY = parseFloat(numbers[1]);

  for (let i = 2; i < numbers.length - 1; i += 2) {
    const x = parseFloat(numbers[i]);
    const y = parseFloat(numbers[i + 1]);
    if (isNaN(x) || isNaN(y)) continue;
    length += Math.sqrt((x - prevX) ** 2 + (y - prevY) ** 2);
    prevX = x;
    prevY = y;
  }

  return Math.round(length * 100) / 100;
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
 * Adds `id="<prefix>:stroke-N"` to each <path>.
 */
function namespaceIds(svgInner: string, prefix: string): string {
  let index = 0;
  return svgInner.replace(/<path\b/g, () => {
    return `<path id="${prefix}:stroke-${index++}"`;
  });
}

// ── Main Compose Function ───────────────────────────────────

/**
 * Compose a pebble from its layers.
 *
 * Stacks shape → fossil (optional) → glyph into a single SVG.
 * All strokes are monochrome (currentColor). No fills.
 * Produces an animation manifest for the reveal sequence.
 *
 * @param input — Shape SVG, glyph SVG, fossil SVG, size, valence.
 * @returns     — Composed SVG + animation manifest.
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
  const glyphInner = extractSvgInner(glyphSvg);
  const glyphViewBox = extractViewBox(glyphSvg) || "0 0 200 200";
  const glyphClean = namespaceIds(monochromeStrokes(stripFills(glyphInner)), "glyph");

  const glyphLayer = [
    `  <g id="layer:glyph" transform="translate(${glyphSlot.x}, ${glyphSlot.y})">`,
    `    <svg viewBox="${glyphViewBox}" width="${glyphSlot.size}" height="${glyphSlot.size}" overflow="visible">`,
    `      ${glyphClean}`,
    `    </svg>`,
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

  // ── Build animation manifest ────────────────────────────
  const manifest = buildManifest(glyphSvg, shapeSvg, fossilSvg);

  return { svg, manifest, canvas };
}

// ── Manifest Builder ────────────────────────────────────────

function buildManifest(
  glyphSvg: string,
  shapeSvg: string,
  fossilSvg?: string
): AnimationManifest {
  const manifest: AnimationManifest = [];

  // Phase 1: Glyph strokes drawn
  const glyphPaths = extractPaths(glyphSvg);
  if (glyphPaths.length > 0) {
    manifest.push({
      type: "glyph",
      paths: glyphPaths.map((p) => ({
        d: p.d,
        length: estimatePathLength(p.d),
      })),
      delay: TIMING.glyph.delay,
      duration: TIMING.glyph.duration,
    });
  }

  // Phase 2: Shape contour traced
  const shapePaths = extractPaths(shapeSvg);
  if (shapePaths.length > 0) {
    manifest.push({
      type: "shape",
      paths: shapePaths.map((p) => ({
        d: p.d,
        length: estimatePathLength(p.d),
      })),
      delay: TIMING.shape.delay,
      duration: TIMING.shape.duration,
    });
  }

  // Phase 2b: Fossil (if present)
  if (fossilSvg) {
    const fossilPaths = extractPaths(fossilSvg);
    if (fossilPaths.length > 0) {
      manifest.push({
        type: "fossil",
        paths: fossilPaths.map((p) => ({
          d: p.d,
          length: estimatePathLength(p.d),
        })),
        delay: TIMING.fossil.delay,
        duration: TIMING.fossil.duration,
      });
    }
  }

  // Phase 3: Color fill
  manifest.push({
    type: "fill",
    delay: TIMING.fill.delay,
    duration: TIMING.fill.duration,
  });

  // Phase 4: Settle
  manifest.push({
    type: "settle",
    delay: TIMING.settle.delay,
    duration: TIMING.settle.duration,
  });

  return manifest;
}

