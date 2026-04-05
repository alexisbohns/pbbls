import type { Pebble } from "@/lib/types"
import type { FilterDef } from "./types"
import type { PRNG } from "./prng"

// ── Turbulence parameters by positiveness band ──────────────────

type TurbulenceProfile = {
  type: "fractalNoise" | "turbulence"
  baseFrequency: [number, number]
  numOctaves: number
}

const TURBULENCE_PROFILES: Record<Pebble["positiveness"], TurbulenceProfile> = {
  "-2": { type: "fractalNoise", baseFrequency: [0.06, 0.08], numOctaves: 5 },
  "-1": { type: "fractalNoise", baseFrequency: [0.04, 0.06], numOctaves: 4 },
  "0": { type: "turbulence", baseFrequency: [0.02, 0.04], numOctaves: 3 },
  "1": { type: "turbulence", baseFrequency: [0.015, 0.025], numOctaves: 2 },
  "2": { type: "turbulence", baseFrequency: [0.01, 0.02], numOctaves: 2 },
}

// ── Specular parameters by positiveness band ────────────────────

type SpecularProfile = {
  surfaceScale: [number, number]
  specularExponent: [number, number]
  specularConstant: number
}

const SPECULAR_PROFILES: Record<Pebble["positiveness"], SpecularProfile> = {
  "-2": { surfaceScale: [1, 2], specularExponent: [5, 10], specularConstant: 0.3 },
  "-1": { surfaceScale: [1, 3], specularExponent: [8, 12], specularConstant: 0.4 },
  "0": { surfaceScale: [2, 4], specularExponent: [10, 15], specularConstant: 0.5 },
  "1": { surfaceScale: [3, 6], specularExponent: [20, 30], specularConstant: 0.7 },
  "2": { surfaceScale: [5, 8], specularExponent: [30, 40], specularConstant: 0.9 },
}

// ── Filter builders ─────────────────────────────────────────────

/**
 * Build a turbulence texture filter.
 *
 * Produces an `<feTurbulence>` noise composited onto the source graphic.
 * Parameters vary by positiveness: negative values produce heavy fractal noise,
 * positive values produce subtle smooth turbulence.
 */
export function turbulenceFilter(
  id: string,
  rng: PRNG,
  positiveness: Pebble["positiveness"],
): FilterDef {
  const profile = TURBULENCE_PROFILES[positiveness]
  const freq = rng.nextFloat(profile.baseFrequency[0], profile.baseFrequency[1])
  const seed = rng.nextInt(0, 10000)

  const svg = [
    `<filter id="${id}" x="0%" y="0%" width="100%" height="100%">`,
    `<feTurbulence type="${profile.type}" baseFrequency="${freq}" numOctaves="${profile.numOctaves}" seed="${seed}" result="noise"/>`,
    `<feColorMatrix type="saturate" values="0" in="noise" result="mono"/>`,
    `<feBlend in="SourceGraphic" in2="mono" mode="multiply"/>`,
    `</filter>`,
  ].join("")

  return { id, svg }
}

/**
 * Build a specular lighting filter for highlight effects.
 *
 * Produces `<feSpecularLighting>` with a point light, composited
 * additively over the source graphic. Light position is PRNG-jittered
 * for per-pebble uniqueness.
 */
export function specularFilter(
  id: string,
  rng: PRNG,
  positiveness: Pebble["positiveness"],
): FilterDef {
  const profile = SPECULAR_PROFILES[positiveness]
  const surfaceScale = rng.nextFloat(profile.surfaceScale[0], profile.surfaceScale[1])
  const exponent = rng.nextFloat(profile.specularExponent[0], profile.specularExponent[1])

  const lightX = rng.nextFloat(-50, 50)
  const lightY = rng.nextFloat(-80, -20)
  const lightZ = rng.nextFloat(80, 150)

  const svg = [
    `<filter id="${id}" x="-20%" y="-20%" width="140%" height="140%">`,
    `<feSpecularLighting surfaceScale="${surfaceScale}" specularConstant="${profile.specularConstant}" specularExponent="${exponent}" in="SourceGraphic" result="specular">`,
    `<fePointLight x="${lightX}" y="${lightY}" z="${lightZ}"/>`,
    `</feSpecularLighting>`,
    `<feComposite in="specular" in2="SourceGraphic" operator="arithmetic" k1="0" k2="1" k3="1" k4="0"/>`,
    `</filter>`,
  ].join("")

  return { id, svg }
}
