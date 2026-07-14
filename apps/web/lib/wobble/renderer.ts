// Per-surface entry points for the wobble experiment. Derives per-surface
// params/half-widths, runs parse → flatten → outline/displace, and memoizes the
// result content-keyed by the source string — the whole flatten/outline/
// displace cost is paid once per artwork, never per render. Pure and SSR-safe
// (no DOM): the SVG rewrite is regex/string surgery, mirroring the iOS
// `WobbleRenderer`'s minimal asset scanning rather than a DOM parser.

import { buildInk, displaceFilledContours } from "./outline"
import { flatten } from "./flatten"
import { parsePath } from "./path-parser"
import { CANONICAL, SEED, scaledParams, type WobbleParams } from "./params"
import { SVGTurbulence } from "./turbulence"

// One noise field for the whole app: the static look is seed 3 (§1).
const noise = new SVGTurbulence(SEED)

// The single weight every composed-pebble layer is traced at, in canvas units —
// mirrors the `6` on every shape path in the engine templates (and iOS
// `PebbleStroke.outlineWidth` / Android `PebbleStroke.OUTLINE_WIDTH`). Custom
// carved glyphs are authored heavier than the outline, so honoring each layer's
// own stroke-width renders the glyph too thick (iOS PR #511 / Android #552);
// tracing every layer at this weight makes glyph == outline everywhere.
const OUTLINE_WIDTH = 6

// ── Content-keyed caches ────────────────────────────────────────────

type Cache<T> = {
  get(key: string): T | undefined
  set(key: string, value: T): void
}

// Bounded, insertion-ordered LRU. Keys are content strings: collision-proof,
// and a few KB per key is negligible next to the cached output.
function makeCache<T>(limit: number): Cache<T> {
  const map = new Map<string, T>()
  return {
    get(key) {
      const value = map.get(key)
      if (value !== undefined) {
        map.delete(key)
        map.set(key, value)
      }
      return value
    },
    set(key, value) {
      if (map.has(key)) map.delete(key)
      map.set(key, value)
      if (map.size > limit) {
        const oldest = map.keys().next().value
        if (oldest !== undefined) map.delete(oldest)
      }
    },
  }
}

const pebbleCache = makeCache<string>(128)
const glyphCache = makeCache<string | null>(512)
const backdropCache = makeCache<string | null>(32)

// ── Minimal SVG attribute scanning ──────────────────────────────────

/** First `name="…"` attribute value in `source`. The boundary lookbehind keeps
 * `d=` from matching `id=`, `fill=` from `fill-rule=`, etc. */
function attr(source: string, name: string): string | null {
  const match = new RegExp(`(?<![\\w-])${name}="([^"]*)"`).exec(source)
  return match ? match[1] : null
}

function parseViewBox(value: string | null): { w: number; h: number } | null {
  if (!value) return null
  const parts = value.split(/[\s,]+/).map(Number)
  if (parts.length !== 4 || parts.some((n) => Number.isNaN(n))) return null
  return { w: parts[2], h: parts[3] }
}

// ── Glyph strokes (thumbnails, carve committed strokes, pills) ──────

/**
 * Wobbled filled ink for one raw glyph stroke in the 200-box space. Returns
 * null when the `d` doesn't parse or nothing renders (caller falls back to the
 * plain stroke).
 */
export function wobbleGlyphInk(d: string, width: number): string | null {
  const key = `${width}|${d}`
  const cached = glyphCache.get(key)
  if (cached !== undefined) return cached

  let ink: string | null = null
  const elements = parsePath(d)
  if (elements) {
    const polylines = flatten(elements, CANONICAL.flattenStep)
    if (polylines.length > 0) {
      ink = buildInk(polylines, width / 2, noise, CANONICAL) || null
    }
  }
  glyphCache.set(key, ink)
  return ink
}

// ── Backdrop silhouettes (pebble-outlines.ts) ───────────────────────

/**
 * Wobbled backdrop silhouette `d`: closed-contour displacement only (the
 * silhouette is already a fill region). Multi-subpath assets (the evenodd
 * `large-lowlight` hole) displace every subpath, so evenodd still carves the
 * hole. Returns null on parse failure (caller falls back to the flat path).
 */
export function wobbleBackdrop(path: string, width: number, height: number): string | null {
  const key = `${width}x${height}|${path}`
  const cached = backdropCache.get(key)
  if (cached !== undefined) return cached

  let out: string | null = null
  const elements = parsePath(path)
  if (elements) {
    const params = scaledParams(width, height)
    const polylines = flatten(elements, params.flattenStep)
    out = displaceFilledContours(polylines, noise, params) || null
  }
  backdropCache.set(key, out)
  return out
}

// ── Composed pebble SVG (PebbleVisual render_svg) ───────────────────

/**
 * Rewrites a composed pebble SVG string, replacing each drawn `<path>` with its
 * wobbled form: stroked paths become leaky filled ink at the uniform outline
 * weight (OUTLINE_WIDTH, ignoring each layer's authored stroke-width so custom
 * glyphs stop rendering too thick — iOS PR #511 / Android #552), filled regions
 * (fossil) get closed-contour displacement. The glyph subtree (`<g id="glyph">`)
 * wobbles with canonical params in its own slot space (matching iOS's glyph
 * rule); all other geometry uses §2.1-scaled canvas params. Falls back to the
 * original SVG on any failure. Content-keyed by the whole SVG string.
 */
export function wobblePebbleSvg(svg: string): string {
  const cached = pebbleCache.get(svg)
  if (cached !== undefined) return cached

  let result: string
  try {
    result = rewritePebbleSvg(svg)
  } catch {
    result = svg
  }
  pebbleCache.set(svg, result)
  return result
}

function rewritePebbleSvg(svg: string): string {
  const viewBox = parseViewBox(attr(svg, "viewBox"))
  const canvasParams = viewBox ? scaledParams(viewBox.w, viewBox.h) : CANONICAL

  const tagRe = /<g\b[^>]*>|<\/g\s*>|<path\b[^>]*>/g
  let out = ""
  let lastIndex = 0
  let depth = 0
  let glyphDepth = -1
  // Accumulated uniform scale of the enclosing `<g transform>` stack, so the
  // outline weight can be expressed in the layer's own (pre-transform) units.
  let scale = 1
  const scaleStack: number[] = []
  let match: RegExpExecArray | null

  while ((match = tagRe.exec(svg)) !== null) {
    out += svg.slice(lastIndex, match.index)
    const tag = match[0]
    if (tag.startsWith("</g")) {
      if (depth === glyphDepth) glyphDepth = -1
      depth -= 1
      scale = scaleStack.pop() ?? 1
      out += tag
    } else if (tag.startsWith("<g")) {
      depth += 1
      if (glyphDepth < 0 && /(?<![\w-])id="glyph"/.test(tag)) glyphDepth = depth
      scaleStack.push(scale)
      scale *= groupScale(tag)
      out += tag
    } else {
      out += wobblePathElement(tag, glyphDepth >= 0, scale, canvasParams)
    }
    lastIndex = match.index + tag.length
  }
  out += svg.slice(lastIndex)
  return out
}

/** Uniform scale factor of a `<g transform="… scale(s) …">`, 1 when absent. The
 * engine only emits translate + uniform scale, matching Android's Affine
 * assumption; a degenerate/zero scale degrades to 1. */
function groupScale(tag: string): number {
  const transform = attr(tag, "transform")
  if (!transform) return 1
  const match = /scale\(\s*([-\d.eE]+)/.exec(transform)
  const s = match ? Number(match[1]) : 1
  return Number.isFinite(s) && s > 0 ? s : 1
}

function wobblePathElement(
  tag: string,
  inGlyph: boolean,
  scale: number,
  canvasParams: WobbleParams,
): string {
  const d = attr(tag, "d")
  if (!d) return tag
  const elements = parsePath(d)
  if (!elements) return tag

  const id = attr(tag, "id")
  const idAttr = id ? `id="${id}" ` : ""
  const stroke = attr(tag, "stroke")
  const fill = attr(tag, "fill")

  const isStroke = stroke !== null && stroke !== "none"
  if (isStroke) {
    const params = inGlyph ? CANONICAL : canvasParams
    // Uniform outline weight, ignoring the layer's authored stroke-width (which
    // is heavy for custom glyphs). OUTLINE_WIDTH is a canvas-space weight; divide
    // by the group's accumulated scale so it lands at 6 after the transform.
    const halfWidth = OUTLINE_WIDTH / 2 / scale
    const polylines = flatten(elements, params.flattenStep)
    const ink = buildInk(polylines, halfWidth, noise, params)
    if (!ink) return tag
    return `<path ${idAttr}d="${ink}" fill="${stroke}" stroke="none"/>`
  }

  const isFill = fill !== null && fill !== "none"
  if (isFill) {
    const polylines = flatten(elements, canvasParams.flattenStep)
    const displaced = displaceFilledContours(polylines, noise, canvasParams)
    if (!displaced) return tag
    const fillRule = attr(tag, "fill-rule")
    const fillRuleAttr = fillRule ? ` fill-rule="${fillRule}"` : ""
    return `<path ${idAttr}d="${displaced}" fill="${fill}"${fillRuleAttr}/>`
  }

  return tag
}
