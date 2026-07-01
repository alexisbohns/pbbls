// apps/admin/lib/pebblestore/svg-to-strokes.ts
//
// SVG → stroke-model conversion for the admin glyph uploader (#497).
//
// SUPPORTED SUBSET (documented — see spec §4):
//   • <path> with commands M L H V Q C Z (absolute + relative). A path using any
//     other command (arcs A, smooth S/T) is SKIPPED and reported.
//   • <line>, <polyline>, <polygon> — converted to an equivalent path `d`.
//
// NORMALIZATION (the glyph model — see the shape-deprecation decision):
//   • Glyphs are shape-agnostic. Content is fitted + centered into a CANONICAL
//     SQUARE viewBox (`0 0 100 100`); the source SVG's own viewBox/width/height
//     are used only as a last-resort fallback when there is no geometry.
//   • Stroke width is ALWAYS 6 in glyph space — the source `stroke-width` is
//     ignored. Scaling/adjusting a glyph changes the drawing's footprint within
//     the square, never the stroke weight; the render scales the whole square
//     into the pebble slot via a uniform transform.
//
// NOT SUPPORTED (skipped + reported): <rect> <circle> <ellipse> <text> <image>
//   <use>, gradients/patterns/<style>, CSS classes, and fills. The model is
//   STROKE-ONLY: a filled icon imports as its OUTLINE only. The live preview
//   shows exactly this before publish.
//
// Throws only on unparseable input (no <svg> root / malformed XML).

import { DEFAULT_STROKE_WIDTH, GLYPH_CANVAS, GLYPH_CANVAS_VIEWBOX, type GlyphStroke } from "./types"
import {
  parsePath,
  serializePath,
  pathBounds,
  transformPath,
  UnsupportedPathError,
  type Matrix,
} from "./path"

export type SvgImportResult = {
  strokes: GlyphStroke[]
  viewBox: string
  /** Tag names (or "tag (CMD)") of elements/paths that were skipped. */
  skipped: string[]
}

const SUPPORTED_TAGS = new Set(["path", "line", "polyline", "polygon"])
const STRUCTURAL_TAGS = new Set(["svg", "g", "defs", "title", "desc", "metadata"])

function elementToD(el: Element): string | null {
  const tag = el.tagName.toLowerCase()
  if (tag === "path") return el.getAttribute("d")
  if (tag === "line") {
    const x1 = el.getAttribute("x1") ?? "0"
    const y1 = el.getAttribute("y1") ?? "0"
    const x2 = el.getAttribute("x2") ?? "0"
    const y2 = el.getAttribute("y2") ?? "0"
    return `M ${x1} ${y1} L ${x2} ${y2}`
  }
  if (tag === "polyline" || tag === "polygon") {
    const raw = (el.getAttribute("points") ?? "").trim()
    const nums = raw.split(/[\s,]+/).filter(Boolean).map(Number)
    if (nums.length < 4) return null
    let d = `M ${nums[0]} ${nums[1]}`
    for (let i = 2; i + 1 < nums.length; i += 2) d += ` L ${nums[i]} ${nums[i + 1]}`
    if (tag === "polygon") d += " Z"
    return d
  }
  return null
}

/** Fraction of the square the content fills (leaves a margin for stroke caps). */
const FILL = 0.9

/**
 * Fit + centre all strokes into the canonical `GLYPH_CANVAS`×`GLYPH_CANVAS`
 * square, preserving aspect ratio. Returns the transformed strokes + the square
 * viewBox. With no geometry, returns the strokes untouched in the square box.
 */
function normalizeToSquare(strokes: GlyphStroke[]): { strokes: GlyphStroke[]; viewBox: string } {
  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity
  let has = false
  for (const s of strokes) {
    const b = pathBounds(parsePath(s.d))
    if (!b) continue
    has = true
    minX = Math.min(minX, b.minX)
    minY = Math.min(minY, b.minY)
    maxX = Math.max(maxX, b.maxX)
    maxY = Math.max(maxY, b.maxY)
  }
  if (!has) return { strokes, viewBox: GLYPH_CANVAS_VIEWBOX }

  const span = Math.max(maxX - minX, maxY - minY) || 1
  const scale = (GLYPH_CANVAS * FILL) / span
  const cx = (minX + maxX) / 2
  const cy = (minY + maxY) / 2
  const half = GLYPH_CANVAS / 2
  // p' = canvasCentre + scale·(p - contentCentre)
  const m: Matrix = [scale, 0, 0, scale, half - scale * cx, half - scale * cy]

  return {
    strokes: strokes.map((s) => ({ ...s, d: serializePath(transformPath(parsePath(s.d), m)) })),
    viewBox: GLYPH_CANVAS_VIEWBOX,
  }
}

export function svgToStrokes(svg: string): SvgImportResult {
  const doc = new DOMParser().parseFromString(svg, "image/svg+xml")
  const root = doc.querySelector("svg")
  if (doc.querySelector("parsererror") || !root) {
    throw new Error("Could not parse this file as SVG.")
  }

  const strokes: GlyphStroke[] = []
  const skipped: string[] = []

  for (const el of Array.from(root.querySelectorAll("*"))) {
    const tag = el.tagName.toLowerCase()
    if (STRUCTURAL_TAGS.has(tag)) continue
    // Elements inside <defs> are definitions, not rendered geometry — skip them.
    if (el.closest("defs")) continue
    if (!SUPPORTED_TAGS.has(tag)) {
      skipped.push(tag)
      continue
    }
    const d = elementToD(el)
    if (!d) {
      skipped.push(tag)
      continue
    }
    try {
      const cmds = parsePath(d)
      if (cmds.length === 0) {
        skipped.push(`${tag} (empty)`)
        continue
      }
      // Stroke width is always 6 in glyph space — the source value is ignored.
      strokes.push({ d: serializePath(cmds), width: DEFAULT_STROKE_WIDTH })
    } catch (e) {
      if (e instanceof UnsupportedPathError) {
        skipped.push(`${tag} (${e.command})`)
      } else {
        throw e
      }
    }
  }

  const square = normalizeToSquare(strokes)
  return { strokes: square.strokes, viewBox: square.viewBox, skipped }
}
