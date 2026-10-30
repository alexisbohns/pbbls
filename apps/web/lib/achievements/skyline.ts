import type { CountingLadder } from "@/lib/types"

// The counting stacks' skyline (#1308, the boards' One skyline, g/92): eight
// stages, one per step of a ladder, standing on a ground line at y 148. A
// stage stands in ink when the server lists its step among the levels; the
// server's next step rises from the ground by the server's progress; the
// contour turns ink once every stage stands. Nothing compares a count with a
// threshold. iOS `SkylineGeometry`, Android the same.

export const GROUND = 148

export const STAGES = [
  "M23 148 C23 143.3 29 143.3 29 148 Z",
  "M32.5 148 C32.1 136.9 34.9 133.2 40.7 133.2 C46.1 133.2 49.9 138.8 49.5 148 Z",
  "M53 148 C52.6 133.5 56.8 128.7 63.3 128.7 C68.8 128.7 72.4 136 72 148 Z",
  "M75.5 148 L76.7 135.5 L83.1 125.2 L89.9 127.5 L95.5 138.4 L95.5 148 Z",
  "M99 148 C98.6 128.4 105 121.8 112.8 121.8 C118.8 121.8 122.4 131.8 122 148 Z",
  "M125.5 148 L125.9 123.5 C126.3 116.7 133.4 116.1 134 122.8 L134 148 Z",
  "M141 148 L141 120.7 L137.5 120.7 C137.2 114.5 145.9 113.9 151.5 113.9 C157.1 113.9 165.8 114.5 165.5 120.7 L162 120.7 L162 148 L158 148 L158 120.7 L145 120.7 L145 148 Z",
  "M169 148 C180.5 147.2 182 110.5 194.9 110.5 C208.4 110.5 207.4 145.8 217 148 Z",
] as const

const SPAN: readonly (readonly [number, number])[] = [[23, 29], [32.5, 49.5], [53, 72], [75.5, 95.5], [99, 122], [125.5, 134], [137.5, 165.5], [169, 217]]
const TOP = [144.5, 133.2, 128.7, 125.2, 121.8, 117.4, 113.9, 110.5] as const

/** The card's and the sheet's view (`12 104 216 48`, back to the ground at 224), and a glyph line's. */
export const SKYLINE_VIEWS = {
  wide: { x: 12, y: 104, width: 216, height: 48, end: 224 },
  narrow: { x: 18, y: 118, width: 110, height: 32, end: 126 },
} as const

/** A stage the skyline can draw: the last one stands for any step past the eighth. */
export function stageIndex(k: number): number {
  return Math.min(Math.max(k, 0), STAGES.length - 1)
}

/** The stage a ladder step stands on: its place on the ladder, the last stage past it (iOS `stageIndex(of:in:)`). */
export function stageOf(threshold: number, ladder: readonly number[]): number {
  const last = Math.min(ladder.length, STAGES.length) - 1
  const at = ladder.indexOf(threshold)
  return Math.max(0, Math.min(at < 0 ? last : at, last))
}

/** The contour over the first `count` stages, from x 16 to `end` on the ground. */
export function contour(count: number, end: number): string {
  const stones = STAGES.slice(0, Math.max(count, 0)).map((d) => `L${d.slice(1).replace(/ Z$/, "")}`)
  return [`M16 ${GROUND}`, ...stones, `L${end} ${GROUND}`].join(" ")
}

/** The part of stage `k` a fill of `fraction` covers, from the ground up (the boards' clip). */
export function stageClip(k: number, fraction: number): { x: number; y: number; width: number; height: number } {
  const i = stageIndex(k)
  const [x0, x1] = SPAN[i]
  const h = (GROUND - TOP[i]) * Math.min(Math.max(fraction, 0), 1)
  return { x: x0 - 2, y: GROUND - h, width: x1 - x0 + 4, height: h + 1 }
}

/** A stage alone, centred on the origin in a 28 × 26 box (a register row's mark), and its ground line. */
export function stageAlone(k: number, scale = 1): { transform: string; baseline: [number, number]; scale: number } {
  const i = stageIndex(k)
  const [x0, x1] = SPAN[i]
  const top = TOP[i]
  const s = Math.min(28 / (x1 - x0 + 4), 26 / (GROUND - top + 2)) * scale
  return {
    transform: `scale(${s.toFixed(3)}) translate(${(-(x0 + x1) / 2).toFixed(2)} ${(-(GROUND + top) / 2).toFixed(2)})`,
    baseline: [x0 - 3, x1 + 3],
    scale: s,
  }
}

export type SkylineStage = { index: number; threshold: number; reached: boolean; fill: number }

/** The stages a counting stack draws: its ladder's first eight steps, each reached when listed, the next by its progress. */
export function skylineStages(stack: CountingLadder): { stages: SkylineStage[]; standing: boolean } {
  const reached = new Set(stack.levels.map((l) => l.threshold))
  const stages = stack.ladder.slice(0, STAGES.length).map((threshold, index) => {
    const isReached = reached.has(threshold)
    const rising = !isReached && stack.next !== null && stack.next.threshold === threshold && stack.next.progress > 0
    return { index, threshold, reached: isReached, fill: isReached ? 1 : rising && stack.next ? stack.next.progress : 0 }
  })
  // An empty ladder stands, as on iOS and Android: nothing is left to rise.
  return { stages, standing: stages.every((s) => s.reached) }
}
