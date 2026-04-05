import type { Pebble } from "@/lib/types"
import type { Rect, SurfaceOutput, RenderTier } from "./types"
import type { PRNG } from "./prng"
import { turbulenceFilter, specularFilter } from "./filters"

// ── Edge noise ranges by positiveness ───────────────────────────

const EDGE_NOISE_RANGE: Record<Pebble["positiveness"], [number, number]> = {
  "-2": [0.7, 1.0],
  "-1": [0.5, 0.7],
  "0": [0.3, 0.5],
  "1": [0.1, 0.3],
  "2": [0.0, 0.15],
}

// ── Gradient builders ───────────────────────────────────────────

function gradientCoords(
  rng: PRNG,
  vb: Rect,
): { x1: string; y1: string; x2: string; y2: string } {
  const angle = rng.nextFloat(0, Math.PI)
  const cx = vb.x + vb.width / 2
  const cy = vb.y + vb.height / 2
  const halfDiag = Math.sqrt(vb.width ** 2 + vb.height ** 2) / 2

  const dx = Math.cos(angle) * halfDiag
  const dy = Math.sin(angle) * halfDiag

  return {
    x1: `${cx - dx}`,
    y1: `${cy - dy}`,
    x2: `${cx + dx}`,
    y2: `${cy + dy}`,
  }
}

function buildNegativeGradient(
  rng: PRNG,
  uid: number,
  vb: Rect,
  positiveness: -2 | -1,
): string {
  const coords = gradientCoords(rng, vb)
  const gradId = `grad-${uid}`

  const darkBase = positiveness === -2 ? "#1a1a1a" : "#2d2d2d"
  const darkEnd = positiveness === -2 ? "#0d0d0d" : "#1a1a1a"
  const midStop = rng.nextFloat(0.4, 0.6)

  let defs = [
    `<linearGradient id="${gradId}" gradientUnits="userSpaceOnUse" x1="${coords.x1}" y1="${coords.y1}" x2="${coords.x2}" y2="${coords.y2}">`,
    `<stop offset="0" stop-color="${darkBase}"/>`,
    `<stop offset="${midStop}" stop-color="${darkEnd}"/>`,
    `<stop offset="1" stop-color="${darkBase}"/>`,
    `</linearGradient>`,
  ].join("")

  if (positiveness === -2) {
    const vigId = `vig-${uid}`
    const vcx = vb.x + vb.width / 2 + rng.nextFloat(-5, 5)
    const vcy = vb.y + vb.height / 2 + rng.nextFloat(-5, 5)

    defs += [
      `<radialGradient id="${vigId}" gradientUnits="userSpaceOnUse" cx="${vcx}" cy="${vcy}" r="${Math.max(vb.width, vb.height) / 2}">`,
      `<stop offset="0" stop-color="#000000" stop-opacity="0.4"/>`,
      `<stop offset="1" stop-color="#000000" stop-opacity="0"/>`,
      `</radialGradient>`,
    ].join("")
  }

  return defs
}

function buildNeutralGradient(
  rng: PRNG,
  uid: number,
  vb: Rect,
): string {
  const coords = gradientCoords(rng, vb)
  const gradId = `grad-${uid}`
  const midStop = rng.nextFloat(0.45, 0.55)

  return [
    `<linearGradient id="${gradId}" gradientUnits="userSpaceOnUse" x1="${coords.x1}" y1="${coords.y1}" x2="${coords.x2}" y2="${coords.y2}">`,
    `<stop offset="0" stop-color="#9CA3AF"/>`,
    `<stop offset="${midStop}" stop-color="#6B7280"/>`,
    `<stop offset="1" stop-color="#9CA3AF"/>`,
    `</linearGradient>`,
  ].join("")
}

function buildPositiveGradient(
  rng: PRNG,
  uid: number,
  vb: Rect,
  positiveness: 1 | 2,
): string {
  const coords = gradientCoords(rng, vb)
  const gradId = `grad-${uid}`

  const stopCount = positiveness === 2 ? 6 : 4
  const startHue = rng.nextFloat(0, 360)
  const hueStep = rng.nextFloat(40, 80)
  const saturation = rng.nextFloat(70, 90)
  const lightness = rng.nextFloat(60, 80)

  const stops: string[] = []
  for (let i = 0; i < stopCount; i++) {
    const offset = i / (stopCount - 1)
    const hue = (startHue + hueStep * i) % 360
    stops.push(
      `<stop offset="${offset}" stop-color="hsl(${hue}, ${saturation}%, ${lightness}%)"/>`,
    )
  }

  let defs = [
    `<linearGradient id="${gradId}" gradientUnits="userSpaceOnUse" x1="${coords.x1}" y1="${coords.y1}" x2="${coords.x2}" y2="${coords.y2}">`,
    ...stops,
    `</linearGradient>`,
  ].join("")

  if (positiveness === 2) {
    const specId = `glow-${uid}`
    const cx = vb.x + vb.width / 2 + rng.nextFloat(-10, 10)
    const cy = vb.y + vb.height / 2 + rng.nextFloat(-15, -5)

    defs += [
      `<radialGradient id="${specId}" gradientUnits="userSpaceOnUse" cx="${cx}" cy="${cy}" r="${Math.max(vb.width, vb.height) * 0.4}">`,
      `<stop offset="0" stop-color="#ffffff" stop-opacity="0.35"/>`,
      `<stop offset="1" stop-color="#ffffff" stop-opacity="0"/>`,
      `</radialGradient>`,
    ].join("")
  }

  return defs
}

// ── Surface generator ───────────────────────────────────────────

/**
 * Generate surface treatment for a pebble based on its positiveness.
 *
 * Returns SVG defs (gradients + optional filters), a fill attribute,
 * an optional filter reference, and an edge noise value.
 *
 * - Negative values → dark gradients, heavy noise, rough texture
 * - Neutral → muted greys, medium noise
 * - Positive → iridescent multi-stop gradients, specular highlights, smooth edges
 *
 * Filters are only included when `tier` is `"detail"`.
 * Output is fully deterministic for identical PRNG state.
 */
export function generateSurface(
  positiveness: Pebble["positiveness"],
  rng: PRNG,
  viewBox: Rect,
  tier: RenderTier,
): SurfaceOutput {
  const uid = rng.nextInt(1000, 9999)

  // Build gradient defs based on positiveness band
  let gradientDefs: string
  if (positiveness <= -1) {
    gradientDefs = buildNegativeGradient(rng, uid, viewBox, positiveness as -2 | -1)
  } else if (positiveness === 0) {
    gradientDefs = buildNeutralGradient(rng, uid, viewBox)
  } else {
    gradientDefs = buildPositiveGradient(rng, uid, viewBox, positiveness as 1 | 2)
  }

  // Build filters (detail tier only)
  let filterDefs = ""
  let filterRef: string | null = null

  if (tier === "detail") {
    const turbId = `turb-${uid}`
    const turb = turbulenceFilter(turbId, rng, positiveness)
    filterDefs += turb.svg
    filterRef = `url(#${turb.id})`

    if (positiveness >= 1) {
      const specId = `spec-${uid}`
      const spec = specularFilter(specId, rng, positiveness)
      filterDefs += spec.svg
      filterRef = `url(#${spec.id})`
    }
  }

  // Compute edge noise
  const [lo, hi] = EDGE_NOISE_RANGE[positiveness]
  const edgeNoise = rng.nextFloat(lo, hi)

  return {
    defs: gradientDefs + filterDefs,
    fill: `url(#grad-${uid})`,
    filterRef,
    edgeNoise,
  }
}
