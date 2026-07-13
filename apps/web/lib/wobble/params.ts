// Canonical wobble parameters (issue #555 §1) plus the §2.1 rule mapping them
// into an arbitrary coordinate space. Authored for a normalized 200-unit box;
// `scaledParams` preserves the visual density when the geometry lives in a
// different space (pebble canvases, backdrop assets).

import type { Point } from "./types"
import type { SVGTurbulence } from "./turbulence"

export type WobbleParams = {
  /** Max displacement, in the geometry's own units. */
  amplitude: number
  /** Noise base frequency, in the geometry's own units. */
  frequency: number
  /** Fractal octave count. */
  octaves: number
  /** Target chord length when flattening, in the geometry's own units. */
  flattenStep: number
}

/** The static look's single seed; boil variants would use seed + k later. */
export const SEED = 3

/** The approved look (issue #555 §1) for geometry in the 200-box. */
export const CANONICAL: WobbleParams = {
  amplitude: 18,
  frequency: 0.024,
  octaves: 5,
  flattenStep: 2,
}

/**
 * §2.1: for a w×h space, with s = 200 / max(w, h), amplitude and step scale by
 * 1/s and frequency by s — equivalent to normalizing into the 200-box,
 * wobbling there, and scaling back.
 */
export function scaledParams(spaceWidth: number, spaceHeight: number): WobbleParams {
  const longestSide = Math.max(spaceWidth, spaceHeight)
  if (!(longestSide > 0)) return CANONICAL
  const normalization = 200 / longestSide
  return {
    amplitude: CANONICAL.amplitude / normalization,
    frequency: CANONICAL.frequency * normalization,
    octaves: CANONICAL.octaves,
    flattenStep: CANONICAL.flattenStep / normalization,
  }
}

function unitClamp(value: number): number {
  return value < 0 ? 0 : value > 1 ? 1 : value
}

/**
 * Displaces one point with the R/G noise channels.
 *
 * The sign is MINUS: feDisplacementMap samples source pixels at
 * p + scale·(noise − 0.5), so geometry must move the opposite way to reproduce
 * the filter's appearance. Issue #555 §2.3 writes "+", but the playground bake
 * — the look's source of truth — uses "−". Noise is always sampled at the
 * un-displaced point.
 */
export function displace(point: Point, noise: SVGTurbulence, params: WobbleParams): Point {
  const r =
    unitClamp((noise.turbulence(0, point.x, point.y, params.frequency, params.octaves) + 1) / 2) - 0.5
  const g =
    unitClamp((noise.turbulence(1, point.x, point.y, params.frequency, params.octaves) + 1) / 2) - 0.5
  return { x: point.x - params.amplitude * r, y: point.y - params.amplitude * g }
}
