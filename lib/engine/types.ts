import type { Pebble, MarkStroke } from "@/lib/types"

// ── Geometry primitives ────────────────────────────────────────────

export type Point = { x: number; y: number }

export type BBox = {
  minX: number
  minY: number
  maxX: number
  maxY: number
}

export type Rect = {
  x: number
  y: number
  width: number
  height: number
}

// ── Shape output ─────────────────────────────────────────────────

export type ShapeOutput = {
  path: string
  bbox: BBox
  vertices: Point[]
}

// ── Glyph (mark rendering data) ───────────────────────────────────

export type Glyph = {
  strokes: MarkStroke[]
  viewBox: string
}

// ── Render tier ───────────────────────────────────────────────────

export type RenderTier = "thumbnail" | "detail"

// ── Render output ─────────────────────────────────────────────────

export type RenderOutput = {
  svg: string
  viewBox: string
}

// ── Filter definition ────────────────────────────────────────────

export type FilterDef = {
  /** Unique filter ID for referencing in style attributes. */
  id: string
  /** Complete <filter>...</filter> SVG element string. */
  svg: string
}

// ── Surface output ──────────────────────────────────────────────

export type SurfaceOutput = {
  /** SVG string fragments for a <defs> block (gradients + filters). */
  defs: string
  /** Fill attribute value (e.g. "url(#grad-1234)" or a solid color). */
  fill: string
  /** CSS filter reference (e.g. "url(#turb-1234)"). Null when filters are skipped. */
  filterRef: string | null
  /** Edge noise magnitude [0, 1] for downstream shape displacement. */
  edgeNoise: number
}

// ── PebbleParams (engine input contract) ──────────────────────────

export type PebbleParams = {
  intensity: Pebble["intensity"]
  positiveness: Pebble["positiveness"]
  emotionColor: string
  retroactive: boolean
  glyph: Glyph | null
}
