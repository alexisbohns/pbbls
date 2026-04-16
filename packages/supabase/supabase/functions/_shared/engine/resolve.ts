import type { PebbleSize, PebbleValence } from "./types.ts";

/**
 * Map the pebble `intensity` column (1..3) to the POC's PebbleSize.
 * Unknown values fall back to "medium" — the engine still renders,
 * the caller is expected to enforce the 1..3 check at insertion time.
 */
export function intensityToSize(intensity: number): PebbleSize {
  switch (intensity) {
    case 1: return "small";
    case 2: return "medium";
    case 3: return "large";
    default: return "medium";
  }
}

/**
 * Map the pebble `positiveness` column (-1, 0, 1) to the POC's PebbleValence.
 * Unknown values fall back to "neutral".
 */
export function positivenessToValence(positiveness: number): PebbleValence {
  switch (positiveness) {
    case -1: return "lowlight";
    case 0:  return "neutral";
    case 1:  return "highlight";
    default: return "neutral";
  }
}
