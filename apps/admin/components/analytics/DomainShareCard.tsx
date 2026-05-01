import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { getDomainShare } from "@/lib/analytics/fetchers"
import {
  TIME_RANGE_LABELS,
  type DomainShareWeeklyRow,
  type TimeRange,
} from "@/lib/analytics/types"
import { ErrorBlock } from "./ErrorBlock"
import {
  DomainShare,
  type DomainShareMover,
  type DomainShareSnapshot,
} from "./DomainShare"

type DomainShareCardProps = { range: TimeRange }

export async function DomainShareCard({ range }: DomainShareCardProps) {
  let result: { current: DomainShareWeeklyRow[]; previous: DomainShareWeeklyRow[] | null }
  try {
    result = await getDomainShare(range)
  } catch (err) {
    return (
      <ErrorBlock
        label="Failed to load domain share"
        message={err instanceof Error ? err.message : String(err)}
      />
    )
  }

  const snapshot = aggregateSnapshot(result.current)
  const totalPebbles = totalAcrossWeeks(result.current)

  let topMover: DomainShareMover | null = null
  let bottomMover: DomainShareMover | null = null
  if (result.previous !== null) {
    const prev = aggregateSnapshot(result.previous)
    const movers = computeMovers(snapshot, prev)
    topMover = movers.top
    bottomMover = movers.bottom
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Domain share</CardTitle>
      </CardHeader>
      <CardContent>
        <DomainShare
          rows={snapshot}
          totalPebbles={totalPebbles}
          rangeLabel={TIME_RANGE_LABELS[range]}
          topMover={topMover}
          bottomMover={bottomMover}
        />
      </CardContent>
    </Card>
  )
}

function aggregateSnapshot(
  rows: DomainShareWeeklyRow[],
): DomainShareSnapshot[] {
  // Sum pebbles_in_domain per domain across weeks; sum total_pebbles by taking
  // each week's total once (not once per domain row in that week).
  const byDomain = new Map<string, DomainShareSnapshot>()
  const weekTotals = new Map<string, number>()

  for (const r of rows) {
    if (!r.domain_id || !r.bucket_week) continue
    const prev = byDomain.get(r.domain_id)
    const count = r.pebbles_in_domain ?? 0
    if (prev) {
      prev.pebbles_in_domain += count
    } else {
      byDomain.set(r.domain_id, {
        domain_id: r.domain_id,
        domain_slug: r.domain_slug ?? r.domain_id,
        domain_name: r.domain_name ?? "—",
        domain_label: r.domain_label ?? "",
        domain_level: r.domain_level,
        pebbles_in_domain: count,
        share_pct: 0,
      })
    }
    if (!weekTotals.has(r.bucket_week)) {
      weekTotals.set(r.bucket_week, r.total_pebbles ?? 0)
    }
  }

  let total = 0
  for (const t of weekTotals.values()) total += t

  const out = Array.from(byDomain.values()).map((d) => ({
    ...d,
    share_pct: total > 0 ? round2((d.pebbles_in_domain / total) * 100) : 0,
  }))
  out.sort((a, b) => b.share_pct - a.share_pct)
  return out
}

function totalAcrossWeeks(rows: DomainShareWeeklyRow[]): number {
  const weekTotals = new Map<string, number>()
  for (const r of rows) {
    if (!r.bucket_week) continue
    if (!weekTotals.has(r.bucket_week)) {
      weekTotals.set(r.bucket_week, r.total_pebbles ?? 0)
    }
  }
  let total = 0
  for (const t of weekTotals.values()) total += t
  return total
}

function computeMovers(
  current: DomainShareSnapshot[],
  previous: DomainShareSnapshot[],
): { top: DomainShareMover | null; bottom: DomainShareMover | null } {
  if (current.length === 0) return { top: null, bottom: null }
  const prevByDomain = new Map(previous.map((p) => [p.domain_id, p]))

  const movers: DomainShareMover[] = current.map((c) => {
    const prev = prevByDomain.get(c.domain_id)
    const prevPct = prev?.share_pct ?? 0
    return {
      domain_id: c.domain_id,
      domain_name: c.domain_name,
      current_pct: c.share_pct,
      previous_pct: prevPct,
      delta_pp: round2(c.share_pct - prevPct),
    }
  })

  const top = [...movers].sort((a, b) => b.delta_pp - a.delta_pp)[0] ?? null
  const bottom = [...movers].sort((a, b) => a.delta_pp - b.delta_pp)[0] ?? null

  // If both extremes are zero, there's no meaningful movement to highlight.
  if (top && top.delta_pp === 0 && bottom && bottom.delta_pp === 0) {
    return { top: null, bottom: null }
  }
  return { top, bottom }
}

function round2(n: number): number {
  return Math.round(n * 100) / 100
}
