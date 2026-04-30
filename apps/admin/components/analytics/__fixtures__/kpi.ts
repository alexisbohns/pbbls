import type { KpiDailyRow } from "@/lib/analytics/types"

const TODAY = new Date()
function iso(daysAgo: number): string {
  const d = new Date(TODAY)
  d.setUTCDate(d.getUTCDate() - daysAgo)
  return d.toISOString().slice(0, 10)
}

/** 30 days of plausible KPI data, ascending. All fields non-null. */
export const kpiFixture: KpiDailyRow[] = Array.from({ length: 30 }, (_, i) => {
  const day = 29 - i
  const dau = 80 + Math.round(20 * Math.sin(i / 4)) + i
  const wau = dau * 4
  const mau = dau * 9
  return {
    bucket_date: iso(day),
    total_users: 1200 + i * 3,
    dau,
    pebbles_today: dau * 2 + (i % 5),
    wau,
    mau,
    dau_mau_pct: Math.round((dau / mau) * 10000) / 100,
  }
})

export const kpiEmptyFixture: KpiDailyRow[] = []
