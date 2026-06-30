// apps/admin/lib/pebblestore/svg-to-strokes.ts
//
// SVG → stroke-model conversion for the admin glyph uploader (#497).
//
// SUPPORTED SUBSET (documented — see spec §4):
//   • <path> with commands M L H V Q C Z (absolute + relative). A path using any
//     other command (arcs A, smooth S/T) is SKIPPED and reported.
//   • <line>, <polyline>, <polygon> — converted to an equivalent path `d`.
//   • viewBox: taken from <svg viewBox>; else from width/height; else a padded
//     bounds computed from the parsed strokes; else a 0 0 100 100 fallback.
//   • stroke-width → the stroke's width (DEFAULT_STROKE_WIDTH when absent).
//     NOTE: renderGlyphPaths normalizes width at market-render time, so width is
//     cosmetic here.
//
// NOT SUPPORTED (skipped + reported): <rect> <circle> <ellipse> <text> <image>
//   <use>, gradients/patterns/<style>, CSS classes, and fills. The model is
//   STROKE-ONLY: a filled icon imports as its OUTLINE only. The live preview
//   shows exactly this before publish.
//
// Throws only on unparseable input (no <svg> root / malformed XML).

import { DEFAULT_STROKE_WIDTH, type GlyphStroke } from "./types"
import { parsePath, serializePath, pathBounds, UnsupportedPathError } from "./path"

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

function readWidth(el: Element): number {
  const w = parseFloat(el.getAttribute("stroke-width") ?? "")
  return Number.isFinite(w) && w > 0 ? w : DEFAULT_STROKE_WIDTH
}

function resolveViewBox(root: Element, strokes: GlyphStroke[]): string {
  const vb = root.getAttribute("viewBox")
  if (vb && vb.trim().split(/[\s,]+/).length === 4) return vb.trim().replace(/,/g, " ")

  const w = parseFloat(root.getAttribute("width") ?? "")
  const h = parseFloat(root.getAttribute("height") ?? "")
  if (Number.isFinite(w) && Number.isFinite(h) && w > 0 && h > 0) return `0 0 ${w} ${h}`

  // Compute padded bounds from the parsed strokes.
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
  if (!has) return "0 0 100 100"
  const pad = Math.max(maxX - minX, maxY - minY) * 0.06 || 1
  return `${minX - pad} ${minY - pad} ${maxX - minX + pad * 2} ${maxY - minY + pad * 2}`
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
      if (cmds.length === 0) continue
      strokes.push({ d: serializePath(cmds), width: readWidth(el) })
    } catch (e) {
      if (e instanceof UnsupportedPathError) {
        skipped.push(`${tag} (${e.command})`)
      } else {
        throw e
      }
    }
  }

  return { strokes, viewBox: resolveViewBox(root, strokes), skipped }
}
