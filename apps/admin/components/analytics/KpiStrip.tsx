import { shiftIsoDate, periodLengthDays } from "@/lib/analytics/date"
import { getKpiDaily } from "@/lib/analytics/fetchers"
import type { KpiDailyRow, TimeRange } from "@/lib/analytics/types"
import { ErrorBlock } from "./ErrorBlock"
import { KpiCard, type KpiCardProps } from "./KpiCard"

type KpiStripProps = { range: TimeRange }

export async function KpiStrip({ range }: KpiStripProps) {
  let rows: KpiDailyRow[]
  try {
    rows = await getKpiDaily(range)
  } catch (err) {
    return (
      <ErrorBlock
        label="Failed to load KPI strip"
        message={err instanceof Error ? err.message : String(err)}
      />
    )
  }

  // Filter out rows missing bucket_date — they can't be ordered or matched.
  const dated = rows.filter((r): r is KpiDailyRow & { bucket_date: string } => r.bucket_date !== null)

  if (dated.length === 0) {
    return <EmptyKpiStrip />
  }

  const sorted = [...dated].sort((a, b) => a.bucket_date.localeCompare(b.bucket_date))
  const latest = sorted[sorted.length - 1]
  const period = periodLengthDays(range)
  const priorBucket = period === null ? null : shiftIsoDate(latest.bucket_date, -period)
  const prior = priorBucket ? sorted.find((r) => r.bucket_date === priorBucket) ?? null : null

  // Sparkline window: last 30 days
  const sparkRows = sorted.slice(-30)

  const trailing7 = (key: NumericKpiKey) => {
    const last7 = sparkRows.slice(-7)
    if (last7.length === 0) return 0
    const sum = last7.reduce((acc, r) => acc + (r[key] ?? 0), 0)
    return Math.round(sum / last7.length)
  }

  const sparkValues = (key: NumericKpiKey): number[] =>
    sparkRows.map((r) => r[key] ?? 0)

  const cards: KpiCardProps[] = [
    {
      label: "Total users",
      value: latest.total_users ?? 0,
      delta: deltaFor(latest.total_users, prior?.total_users ?? null),
      subLabel: "all signups",
    },
    {
      label: "DAU",
      value: trailing7("dau"),
      sparkline: sparkValues("dau"),
      delta: deltaFor(latest.dau, prior?.dau ?? null),
      subLabel: "trailing 7-day avg",
    },
    {
      label: "WAU",
      value: latest.wau ?? 0,
      sparkline: sparkValues("wau"),
      delta: deltaFor(latest.wau, prior?.wau ?? null),
      subLabel: "rolling 7 days",
    },
    {
      label: "MAU",
      value: latest.mau ?? 0,
      sparkline: sparkValues("mau"),
      delta: deltaFor(latest.mau, prior?.mau ?? null),
      subLabel: "rolling 30 days",
    },
    {
      label: "Pebbles / day",
      value: trailing7("pebbles_today"),
      sparkline: sparkValues("pebbles_today"),
      delta: deltaFor(latest.pebbles_today, prior?.pebbles_today ?? null),
      subLabel: "trailing 7-day avg",
    },
    {
      label: "DAU / MAU",
      value: latest.dau_mau_pct ?? "—",
      unit: latest.dau_mau_pct === null ? undefined : "%",
      delta: deltaForPct(latest.dau_mau_pct, prior?.dau_mau_pct ?? null),
      subLabel: "stickiness",
    },
  ]

  return (
    <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-6">
      {cards.map((c) => (
        <KpiCard key={c.label} {...c} />
      ))}
    </div>
  )
}

type NumericKpiKey = "total_users" | "dau" | "wau" | "mau" | "pebbles_today"

function deltaFor(
  current: number | null,
  prior: number | null,
): KpiCardProps["delta"] {
  if (current === null || prior === null) return undefined
  const absolute = current - prior
  const direction = absolute > 0 ? "up" : absolute < 0 ? "down" : "flat"
  return { absolute, direction }
}

function deltaForPct(
  current: number | null,
  prior: number | null,
): KpiCardProps["delta"] {
  if (current === null || prior === null) return undefined
  const absolute = Number((current - prior).toFixed(1))
  const direction = absolute > 0 ? "up" : absolute < 0 ? "down" : "flat"
  return { absolute, direction, unit: "pp" }
}

function EmptyKpiStrip() {
  return (
    <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-6">
      {["Total users", "DAU", "WAU", "MAU", "Pebbles / day", "DAU / MAU"].map((label) => (
        <KpiCard key={label} label={label} value="—" subLabel="No data yet" />
      ))}
    </div>
  )
}
