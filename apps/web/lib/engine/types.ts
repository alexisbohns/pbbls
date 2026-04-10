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

// ── Glyph (mark rendering data) ───────────────────────────────────

export type Glyph = {
  strokes: MarkStroke[]
  viewBox: string
}

// ── Render tier ───────────────────────────────────────────────────

export type RenderTier = "thumbnail" | "detail"

// ── Render output ─────────────────────────────────────────────────

export type EngineOutput = {
  svg: string
  viewBox: string
}

// ── PebbleParams (engine input contract) ──────────────────────────

export type PebbleParams = {
  intensity: Pebble["intensity"]
  positiveness: Pebble["positiveness"]
  emotionColor: string
  retroactive: boolean
  glyph: Glyph | null
}
