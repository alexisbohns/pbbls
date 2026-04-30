import type { ActiveUsersChartDatum } from "../ActiveUsersChart"

const TODAY = new Date()
function iso(daysAgo: number): string {
  const d = new Date(TODAY)
  d.setUTCDate(d.getUTCDate() - daysAgo)
  return d.toISOString().slice(0, 10)
}

export const denseFixture: ActiveUsersChartDatum[] = Array.from({ length: 90 }, (_, i) => {
  const day = 89 - i
  const dau = 100 + Math.round(40 * Math.sin(i / 7)) + Math.round(i / 3)
  return { bucket_date: iso(day), dau, wau: dau * 4, mau: dau * 9 }
})

export const sparseFixture: ActiveUsersChartDatum[] = Array.from({ length: 12 }, (_, i) => {
  const day = 11 - i
  const dau = 30 + i * 2
  return { bucket_date: iso(day), dau, wau: dau * 3, mau: dau * 6 }
})

export const emptyFixture: ActiveUsersChartDatum[] = []
