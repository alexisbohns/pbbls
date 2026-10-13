import type { PebbleParams } from "./types"

/** 28 days in milliseconds. */
const RETROACTIVE_THRESHOLD_MS = 28 * 24 * 60 * 60 * 1000

/**
 * True when the pebble was recorded more than 28 days after it happened; the
 * template then shows its fossil. False when the record date is unknown (the
 * share projection carries none).
 */
export function isRetroactive(happenedAt: string, createdAt: string | undefined): boolean {
  if (!createdAt) return false
  const elapsed = new Date(createdAt).getTime() - new Date(happenedAt).getTime()
  return elapsed > RETROACTIVE_THRESHOLD_MS
}

type PebbleLike = {
  intensity: number
  positiveness: number
  happened_at: string
  created_at?: string
}

/**
 * A pebble (owner-side, shared or a connection's) into the engine's input
 * contract. The body is drawn in `currentColor`: the caller's `.pbbls-visual`
 * wrapper tints it from the category palette, as it did the server-composed
 * render. Out-of-range valence values clamp to the nearest template.
 * Pure, deterministic, and side-effect-free.
 */
export function toPebbleParams(pebble: PebbleLike): PebbleParams {
  return {
    intensity: clampIntensity(pebble.intensity),
    positiveness: Math.sign(pebble.positiveness) as -1 | 0 | 1,
    emotionColor: "currentColor",
    retroactive: isRetroactive(pebble.happened_at, pebble.created_at),
  }
}

function clampIntensity(value: number): 1 | 2 | 3 {
  if (value <= 1) return 1
  if (value >= 3) return 3
  return 2
}
