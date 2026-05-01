import type { RetentionCohortRow } from "@/lib/analytics/types"

const TODAY = new Date()

function mondayUtc(daysAgo: number): string {
  const d = new Date(TODAY)
  d.setUTCDate(d.getUTCDate() - daysAgo)
  // Snap to Monday (UTC). getUTCDay(): 0 = Sun, 1 = Mon, …
  const dow = d.getUTCDay()
  const offsetToMonday = (dow + 6) % 7
  d.setUTCDate(d.getUTCDate() - offsetToMonday)
  return d.toISOString().slice(0, 10)
}

/** 8 cohorts (oldest → newest), with retention curves that fade over weeks. */
export const denseRetentionFixture: RetentionCohortRow[] = (() => {
  const rows: RetentionCohortRow[] = []
  const cohortCount = 8
  // Cohort sizes: oldest were smaller, recent ones larger.
  const sizes = [12, 18, 22, 27, 31, 38, 44, 51]

  for (let c = 0; c < cohortCount; c++) {
    const ageWeeks = cohortCount - 1 - c // 7, 6, …, 0
    const cohort_week = mondayUtc(ageWeeks * 7 + 7)
    const size = sizes[c]
    const maxOffset = ageWeeks
    for (let w = 0; w <= maxOffset; w++) {
      // Retention decays: ~100, 60, 45, 35, 28, 23, 19, 16
      const decayed = w === 0 ? 100 : Math.round(100 * Math.exp(-0.45 * w) + (c % 3) * 2)
      const pct = Math.max(0, Math.min(100, decayed))
      const active = Math.round((pct / 100) * size)
      rows.push({
        cohort_week,
        week_offset: w,
        cohort_size: size,
        active_users: active,
        retention_pct: pct,
      })
    }
  }
  return rows
})()

export const emptyRetentionFixture: RetentionCohortRow[] = []
