/**
 * Pebble Engine · Glyph Normalization
 *
 * Takes raw strokes from the carve editor and produces a normalized
 * square SVG artwork. Runs SERVER-SIDE in the Supabase Edge Function
 * compositor. Pure function with no DOM/network dependencies, so it can
 * also be reused client-side (e.g. in a future carve editor) without
 * modification.
 *
 * Responsibilities:
 * - Compute bounding box of all strokes
 * - Center and scale strokes to fill ~80% of a square canvas
 * - Output a clean square SVG with a "0 0 {size} {size}" viewBox
 *
 * Pure function. No DOM. No side effects.
 */

import type { Stroke, BoundingBox, GlyphArtwork, Point } from "./types.ts";

// ── Config ──────────────────────────────────────────────────

/** How much of the square canvas the glyph should fill (0-1). */
const FILL_RATIO = 0.8;

/** Default artwork canvas side length. */
const DEFAULT_ARTWORK_SIZE = 200;

/** Default stroke width when not specified on the stroke. */
const DEFAULT_STROKE_WIDTH = 3;

// ── Path Parsing ────────────────────────────────────────────

/**
 * Extract all coordinate points from an SVG path `d` string.
 * Handles M, L, C, S, Q, T, A commands (absolute only for bbox).
 * For relative commands, accumulates position.
 */
function extractPointsFromPath(d: string): Point[] {
  const points: Point[] = [];
  // Match all numbers (including negatives and decimals)
  const numbers = d.match(/-?\d+\.?\d*/g);
  if (!numbers) return points;

  // Match commands
  const commands = d.match(/[MLHVCSQTAZmlhvcsqtaz]/g);
  if (!commands) return points;

  let numIndex = 0;
  let curX = 0;
  let curY = 0;

  const nextNum = (): number => {
    if (numIndex < numbers.length) return parseFloat(numbers[numIndex++]);
    return 0;
  };

  for (const cmd of commands) {
    switch (cmd) {
      case "M":
      case "L":
      case "T":
        curX = nextNum();
        curY = nextNum();
        points.push({ x: curX, y: curY });
        break;
      case "m":
      case "l":
      case "t":
        curX += nextNum();
        curY += nextNum();
        points.push({ x: curX, y: curY });
        break;
      case "H":
        curX = nextNum();
        points.push({ x: curX, y: curY });
        break;
      case "h":
        curX += nextNum();
        points.push({ x: curX, y: curY });
        break;
      case "V":
        curY = nextNum();
        points.push({ x: curX, y: curY });
        break;
      case "v":
        curY += nextNum();
        points.push({ x: curX, y: curY });
        break;
      case "C": {
        // Cubic bezier: 3 coordinate pairs
        for (let i = 0; i < 3; i++) {
          curX = nextNum();
          curY = nextNum();
          points.push({ x: curX, y: curY });
        }
        break;
      }
      case "c": {
        for (let i = 0; i < 3; i++) {
          const dx = nextNum();
          const dy = nextNum();
          if (i === 2) {
            curX += dx;
            curY += dy;
          }
          points.push({ x: curX + dx, y: curY + dy });
        }
        break;
      }
      case "S":
      case "Q": {
        // 2 coordinate pairs
        for (let i = 0; i < 2; i++) {
          curX = nextNum();
          curY = nextNum();
          points.push({ x: curX, y: curY });
        }
        break;
      }
      case "s":
      case "q": {
        for (let i = 0; i < 2; i++) {
          const dx = nextNum();
          const dy = nextNum();
          if (i === 1) {
            curX += dx;
            curY += dy;
          }
          points.push({ x: curX + dx, y: curY + dy });
        }
        break;
      }
      case "A": {
        // Arc: rx ry rotation large-arc sweep x y
        nextNum(); nextNum(); nextNum(); nextNum(); nextNum();
        curX = nextNum();
        curY = nextNum();
        points.push({ x: curX, y: curY });
        break;
      }
      case "a": {
        nextNum(); nextNum(); nextNum(); nextNum(); nextNum();
        curX += nextNum();
        curY += nextNum();
        points.push({ x: curX, y: curY });
        break;
      }
      case "Z":
      case "z":
        break;
    }
  }

  return points;
}

// ── Bounding Box ────────────────────────────────────────────

/**
 * Compute the bounding box of a set of strokes.
 * Accounts for stroke width so edges aren't clipped.
 */
export function computeStrokesBoundingBox(
  strokes: Stroke[],
  defaultStrokeWidth = DEFAULT_STROKE_WIDTH
): BoundingBox {
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;

  for (const stroke of strokes) {
    const halfWidth = (stroke.width ?? defaultStrokeWidth) / 2;
    const points = extractPointsFromPath(stroke.d);

    for (const p of points) {
      minX = Math.min(minX, p.x - halfWidth);
      minY = Math.min(minY, p.y - halfWidth);
      maxX = Math.max(maxX, p.x + halfWidth);
      maxY = Math.max(maxY, p.y + halfWidth);
    }
  }

  // Guard against empty strokes
  if (!isFinite(minX)) {
    return { minX: 0, minY: 0, maxX: 0, maxY: 0, width: 0, height: 0 };
  }

  return {
    minX,
    minY,
    maxX,
    maxY,
    width: maxX - minX,
    height: maxY - minY,
  };
}

// ── Main Function ───────────────────────────────────────────

/**
 * Create a normalized square SVG artwork from raw strokes.
 *
 * The strokes are centered and scaled to fill `FILL_RATIO` (80%)
 * of the square canvas, preserving aspect ratio.
 * The remaining 20% acts as inner padding so the glyph breathes
 * inside its slot.
 *
 * @param strokes  — Raw strokes from the carve editor (jsonb format).
 * @param options  — Optional overrides.
 * @returns        — A GlyphArtwork with the normalized SVG.
 */
export function createGlyphArtwork(
  strokes: Stroke[],
  options?: {
    /** Side length of the output square. Default: 200. */
    artworkSize?: number;
    /** Fill ratio (0-1). Default: 0.8. */
    fillRatio?: number;
    /** Default stroke width. Default: 3. */
    defaultWidth?: number;
  }
): GlyphArtwork {
  const size = options?.artworkSize ?? DEFAULT_ARTWORK_SIZE;
  const fill = options?.fillRatio ?? FILL_RATIO;
  const defaultSW = options?.defaultWidth ?? DEFAULT_STROKE_WIDTH;

  // Empty glyph → empty square
  if (strokes.length === 0) {
    return {
      svg: `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}" width="${size}" height="${size}"></svg>`,
      viewBox: `0 0 ${size} ${size}`,
      size,
    };
  }

  const bbox = computeStrokesBoundingBox(strokes, defaultSW);

  // Compute scale: fit the longest edge into (size × fillRatio)
  const targetSize = size * fill;
  const scaleX = bbox.width > 0 ? targetSize / bbox.width : 1;
  const scaleY = bbox.height > 0 ? targetSize / bbox.height : 1;
  const scale = Math.min(scaleX, scaleY);

  // Compute translation to center the scaled strokes
  const scaledWidth = bbox.width * scale;
  const scaledHeight = bbox.height * scale;
  const translateX = (size - scaledWidth) / 2 - bbox.minX * scale;
  const translateY = (size - scaledHeight) / 2 - bbox.minY * scale;

  // Build path elements
  const paths = strokes
    .map((stroke, i) => {
      const sw = stroke.width ?? defaultSW;
      return `  <path id="glyph:stroke-${i}" d="${stroke.d}" fill="none" stroke="currentColor" stroke-width="${sw * scale}" stroke-linecap="round" stroke-linejoin="round"/>`;
    })
    .join("\n");

  const svg = [
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}" width="${size}" height="${size}">`,
    `  <g transform="translate(${round(translateX)}, ${round(translateY)}) scale(${round(scale)})">`,
    paths,
    `  </g>`,
    `</svg>`,
  ].join("\n");

  return {
    svg,
    viewBox: `0 0 ${size} ${size}`,
    size,
  };
}

// ── Utility ─────────────────────────────────────────────────

function round(n: number, decimals = 2): number {
  const f = Math.pow(10, decimals);
  return Math.round(n * f) / f;
}
