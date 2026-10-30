import { unitHash } from "@/lib/achievements/geometry"

// The domains' mended band (#1308, the boards' `shardStrip`): `count` shards
// across `width`, each seam leaning by up to 35 % of a cell at its top and its
// bottom by the stable hash of its index, so the band mends the same way on
// every device. iOS `ShardBandGeometry`, Android the same.

export type Shard = { d: string; centre: number }

export function shards(count: number, width: number, height: number): Shard[] {
  const cell = width / Math.max(count, 1)
  const lean = (key: string) => (unitHash(key) - 0.5) * cell * 0.7
  const out: Shard[] = []
  let x = 0
  let left: [number, number] = [0, 0]
  for (let i = 0; i < count; i++) {
    const last = i === count - 1
    const rightTop = last ? 0 : lean(`R${i}t`)
    const rightBottom = last ? 0 : lean(`R${i}b`)
    const [leftTop, leftBottom] = i === 0 ? [0, 0] : left
    out.push({
      d: `M${(x + leftTop).toFixed(2)} 0 L${(x + cell + rightTop).toFixed(2)} 0 L${(x + cell + rightBottom).toFixed(2)} ${height} L${(x + leftBottom).toFixed(2)} ${height}Z`,
      centre: x + cell / 2,
    })
    left = [rightTop, rightBottom]
    x += cell
  }
  return out
}

/** The band's height, and a new domain's dot 6 below its shard's middle. */
export const BAND_HEIGHT = 30
export const BAND_DOT_DROP = 6
