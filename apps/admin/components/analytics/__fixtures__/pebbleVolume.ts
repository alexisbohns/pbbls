import type { PebbleVolumeChartDatum } from "../PebbleVolumeChart"

const TODAY = new Date()
function iso(daysAgo: number): string {
  const d = new Date(TODAY)
  d.setUTCDate(d.getUTCDate() - daysAgo)
  return d.toISOString().slice(0, 10)
}

/** 90 days of pebble volume with picture/collection overlays. */
export const denseVolumeFixture: PebbleVolumeChartDatum[] = Array.from(
  { length: 90 },
  (_, i) => {
    const day = 89 - i
    const pebbles = 40 + Math.round(20 * Math.sin(i / 5)) + Math.round(i / 3)
    return {
      bucket_date: iso(day),
      pebbles,
      pebbles_with_picture: Math.round(pebbles * 0.4),
      pebbles_in_collection: Math.round(pebbles * 0.55),
    }
  },
)

/** Sparse week with single-digit volume to stress-test small numbers. */
export const sparseVolumeFixture: PebbleVolumeChartDatum[] = Array.from(
  { length: 12 },
  (_, i) => {
    const day = 11 - i
    const pebbles = i % 3 === 0 ? 0 : 1 + (i % 4)
    return {
      bucket_date: iso(day),
      pebbles,
      pebbles_with_picture: Math.max(0, pebbles - 1),
      pebbles_in_collection: Math.round(pebbles / 2),
    }
  },
)

export const emptyVolumeFixture: PebbleVolumeChartDatum[] = []
