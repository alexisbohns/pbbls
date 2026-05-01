import type { UserAveragesChartDatum } from "../UserAveragesChart"

const TODAY = new Date()

/** Monday of the ISO week, `weeksAgo` weeks before today (UTC, ISO date). */
function isoWeekStart(weeksAgo: number): string {
  const d = new Date(TODAY)
  d.setUTCDate(d.getUTCDate() - weeksAgo * 7)
  // Monday-start week, matching `date_trunc('week', ...)`.
  const dow = d.getUTCDay()
  const offset = (dow + 6) % 7
  d.setUTCDate(d.getUTCDate() - offset)
  return d.toISOString().slice(0, 10)
}

export const denseUserAveragesFixture: UserAveragesChartDatum[] = Array.from(
  { length: 12 },
  (_, i) => {
    const weeksAgo = 11 - i
    const wave = Math.sin(i / 3)
    return {
      bucket_week: isoWeekStart(weeksAgo),
      active_users: 80 + i * 4,
      avg_glyphs: round2(2.5 + i * 0.18 + wave * 0.3),
      avg_souls: round2(1.8 + i * 0.12 + wave * 0.25),
      avg_collections: round2(0.9 + i * 0.07 + wave * 0.15),
    }
  },
)

export const sparseUserAveragesFixture: UserAveragesChartDatum[] = Array.from(
  { length: 4 },
  (_, i) => {
    const weeksAgo = 3 - i
    return {
      bucket_week: isoWeekStart(weeksAgo),
      active_users: 5 + i,
      avg_glyphs: round2(1.0 + i * 0.05),
      avg_souls: round2(0.5 + i * 0.03),
      avg_collections: round2(0.2 + i * 0.02),
    }
  },
)

export const emptyUserAveragesFixture: UserAveragesChartDatum[] = []

function round2(n: number): number {
  return Math.round(n * 100) / 100
}
