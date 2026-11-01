/**
 * A week's cairn (#1384, spec D10; iOS `CairnGeometry`): one stone per day that
 * holds a moment, widest at the base, `9 + 4.2·min(moments, 3) + 2.4·(intensity − 1)`
 * wide (a day without a pebble counts as intensity 1), pressed flat to 0.62 of
 * the pebble outline's height, each sinking 0.2 of its height into the one
 * below, the stack squeezed (never narrowed) to fit 42 tall. Drawn in a 30 × 42 box.
 */
export type CairnDay = readonly [moments: number, topIntensity: number]
export type CairnStone = { x: number; y: number; w: number; h: number; dotted: boolean }

export const CAIRN_BOX = { width: 30, height: 42 } as const

const BASE = 9
const PER_MOMENT = 4.2
const PER_INTENSITY = 2.4
const MOMENTS_CAP = 3
const FLATTEN = 0.62
const OVERLAP = 0.2
/** The pebble outline's aspect (17.6 × 14.8), as the seals draw it. */
const ASPECT = 1.19

export function cairnWidth([moments, intensity]: CairnDay): number {
  return BASE + PER_MOMENT * Math.min(moments, MOMENTS_CAP) + PER_INTENSITY * (Math.min(Math.max(intensity, 1), 3) - 1)
}

/** Base first: the days that hold a moment, widest at the bottom. */
export function cairnWidths(days: readonly CairnDay[]): number[] {
  return days.filter(([m]) => m > 0).map(cairnWidth).sort((a, b) => b - a)
}

/** The stones, bottom first, placed in the 30 × 42 box. */
export function cairnStones(widths: readonly number[], options: { dotted?: boolean } = {}): CairnStone[] {
  const heightOf = (w: number) => (w / ASPECT) * FLATTEN
  const natural = widths.reduce((total, w, k) => total + heightOf(w) * (k === 0 ? 1 : 1 - OVERLAP), 0)
  const squeeze = natural > CAIRN_BOX.height ? CAIRN_BOX.height / natural : 1
  let bottom: number = CAIRN_BOX.height
  return widths.map((w, k) => {
    const h = heightOf(w) * squeeze
    if (k > 0) bottom += h * OVERLAP
    const stone = { x: (CAIRN_BOX.width - w) / 2, y: bottom - h, w, h, dotted: options.dotted ?? false }
    bottom -= h
    return stone
  })
}

/** A week without a pebble: one dotted stone, the width of a single moment. */
export const EMPTY_CAIRN: CairnStone[] = cairnStones([BASE + PER_MOMENT], { dotted: true })
