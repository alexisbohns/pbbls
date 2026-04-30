/**
 * Pebble Engine · Shared Types
 *
 * Pure type definitions shared across client (carve editor)
 * and server (Supabase Edge Function compositor).
 * No runtime code — only types and enums.
 */

// ── Primitives ──────────────────────────────────────────────

export type PebbleSize = "small" | "medium" | "large";
export type PebbleValence = "highlight" | "neutral" | "lowlight";

export interface Point {
  x: number;
  y: number;
}

export interface BoundingBox {
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
  width: number;
  height: number;
}

// ── Strokes ─────────────────────────────────────────────────

/** A single stroke as stored in the glyphs table (jsonb).
 *  Field is `width` to match the legacy DB format and docs/seeds/domain-glyph-seed.json.
 *  The POC called this `strokeWidth`; the port renames it to match reality. */
export interface Stroke {
  d: string;
  width?: number;
}

// ── Glyph ───────────────────────────────────────────────────

/** Output of createGlyphArtwork: a normalized square SVG. */
export interface GlyphArtwork {
  /** Complete SVG string (square, normalized). */
  svg: string;
  /** ViewBox of the artwork (always "0 0 {size} {size}"). */
  viewBox: string;
  /** Side length in px. */
  size: number;
}

// ── Layout ──────────────────────────────────────────────────

export interface GlyphSlot {
  /** Square side length of the glyph zone in px. */
  size: number;
  /** X offset from left edge of canvas in px. */
  x: number;
  /** Y offset from top edge of canvas in px. */
  y: number;
}

export interface CanvasSize {
  width: number;
  height: number;
}

export interface PebbleLayoutConfig {
  canvas: CanvasSize;
  glyphSlot: GlyphSlot;
}

// ── Compose ─────────────────────────────────────────────────

export interface PebbleEngineInput {
  size: PebbleSize;
  valence: PebbleValence;
  /** Full-canvas SVG string for the pebble shape. */
  shapeSvg: string;
  /** Square SVG string for the glyph (output of createGlyphArtwork). */
  glyphSvg: string;
  /** Full-canvas SVG string for the fossil layer (optional). */
  fossilSvg?: string;
  /** Layout overrides. If omitted, uses default config. */
  layoutOverride?: PebbleLayoutConfig;
}

export interface PebbleEngineOutput {
  /** Composed monochrome SVG with stroke IDs. No fills, no colors. */
  svg: string;
  /** Canvas dimensions used. */
  canvas: CanvasSize;
}
