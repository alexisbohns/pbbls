/**
 * Deterministic per-pebble "mess" for the polaroid grid, derived from the
 * pebble's id so the layout survives re-renders and refreshes intact.
 *
 * Math.random() would reshuffle every card on every render, which makes the
 * layout impossible to judge — you would never be looking at the same wall twice.
 */
export type PolaroidChaos = {
  /** deg, -6..6 */
  rotate: number
  /** px, -6..6 */
  shiftX: number
  /** 0..9 — varies who wins where cards overlap on hover */
  z: number
}

/** FNV-1a. Cheap, well-spread for short ids, and stable across runtimes — which
 *  a JS string hash has to be here, since the server and the client both run it. */
function hash(input: string): number {
  let h = 0x811c9dc5
  for (let i = 0; i < input.length; i++) {
    h ^= input.charCodeAt(i)
    h = Math.imul(h, 0x01000193)
  }
  return h >>> 0
}

export function polaroidChaos(id: string): PolaroidChaos {
  const h = hash(id)
  const byte = (i: number) => (h >>> (i * 8)) & 0xff
  return {
    rotate: (byte(0) % 13) - 6,
    shiftX: (byte(1) % 13) - 6,
    z: byte(2) % 10,
  }
}
