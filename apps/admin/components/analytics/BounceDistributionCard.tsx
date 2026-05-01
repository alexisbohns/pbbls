import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { getBounceDistributionToday } from "@/lib/analytics/fetchers"
import type { BounceDistributionRow } from "@/lib/analytics/types"
import { BounceDistribution, type BounceDistributionStats } from "./BounceDistribution"
import type { BounceDistributionDatum } from "./BounceDistributionChart"
import { ErrorBlock } from "./ErrorBlock"

// The view always returns six bucket rows in this order. Matching this list
// here means the chart still renders the full histogram (with zero bars) when
// the RPC is empty mid-deploy, and pins the X-axis order independent of how
// the RPC chooses to sort its rows.
const BUCKET_ORDER: { order: number; label: string }[] = [
  { order: 0, label: "0" },
  { order: 1, label: "1-10" },
  { order: 2, label: "11-25" },
  { order: 3, label: "26-50" },
  { order: 4, label: "51-100" },
  { order: 5, label: "100+" },
]

export async function BounceDistributionCard() {
  let rows: BounceDistributionRow[]
  try {
    rows = await getBounceDistributionToday()
  } catch (err) {
    return (
      <ErrorBlock
        label="Failed to load bounce karma distribution"
        message={err instanceof Error ? err.message : String(err)}
      />
    )
  }

  const { buckets, totalUsers, stats } = projectRows(rows)

  return (
    <Card>
      <CardHeader>
        <CardTitle>Bounce karma distribution</CardTitle>
      </CardHeader>
      <CardContent>
        <BounceDistribution
          buckets={buckets}
          totalUsers={totalUsers}
          stats={stats}
        />
      </CardContent>
    </Card>
  )
}

function projectRows(rows: BounceDistributionRow[]): {
  buckets: BounceDistributionDatum[]
  totalUsers: number
  stats: BounceDistributionStats
} {
  const byOrder = new Map<number, BounceDistributionRow>()
  for (const r of rows) {
    if (r.bucket_order !== null) byOrder.set(r.bucket_order, r)
  }

  const buckets: BounceDistributionDatum[] = BUCKET_ORDER.map(({ order, label }) => ({
    bucket_label: label,
    users: byOrder.get(order)?.users ?? 0,
  }))

  const totalUsers = buckets.reduce((acc, b) => acc + b.users, 0)

  // Summary stats are denormalized onto every row — read whichever row was
  // returned (preferring bucket 0 for determinism). All-null when no rows.
  const sample = byOrder.get(0) ?? rows[0]
  const stats: BounceDistributionStats = {
    medianScore: sample?.median_score ?? null,
    pctMaintaining: sample?.pct_maintaining ?? null,
    avgActiveDaysPerWeek: sample?.avg_active_days_per_week ?? null,
  }

  return { buckets, totalUsers, stats }
}
