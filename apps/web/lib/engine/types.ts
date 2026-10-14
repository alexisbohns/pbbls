import type { Intensity, Positiveness } from "@/lib/types"

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

// ── Render tier ───────────────────────────────────────────────────

export type RenderTier = "thumbnail" | "detail"

// ── Render output ─────────────────────────────────────────────────

export type EngineOutput = {
  /** The body only, glyph zone empty: static templates, safe to inject. */
  svg: string
  viewBox: string
  /** Where the glyph goes, in `viewBox` units. */
  glyphZone: Rect
}

// ── PebbleParams (engine input contract) ──────────────────────────

export type PebbleParams = {
  intensity: Intensity
  positiveness: Positiveness
  emotionColor: string
  retroactive: boolean
}
