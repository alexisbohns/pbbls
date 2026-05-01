import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { getRetentionCohorts } from "@/lib/analytics/fetchers"
import type { RetentionCohortRow } from "@/lib/analytics/types"
import { ErrorBlock } from "./ErrorBlock"

export async function RetentionHeatmapCard() {
  let rows: RetentionCohortRow[]
  try {
    rows = await getRetentionCohorts()
  } catch (err) {
    return (
      <ErrorBlock
        label="Failed to load retention cohorts"
        message={err instanceof Error ? err.message : String(err)}
      />
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Retention by signup cohort</CardTitle>
      </CardHeader>
      <CardContent>
        <RetentionHeatmap rows={rows} />
      </CardContent>
    </Card>
  )
}

type RetentionHeatmapProps = {
  rows: RetentionCohortRow[]
}

// 8 buckets, 10% steps, light → stone. Listed as full class strings so
// Tailwind doesn't tree-shake them.
const BUCKET_CLASSES = [
  "bg-stone-50 text-stone-700",
  "bg-stone-100 text-stone-700",
  "bg-stone-200 text-stone-800",
  "bg-stone-300 text-stone-900",
  "bg-stone-400 text-stone-50",
  "bg-stone-500 text-stone-50",
  "bg-stone-600 text-stone-50",
  "bg-stone-700 text-stone-50",
] as const

function bucketClass(pct: number): string {
  // pct in [0, 100]. Bucket 0 = [0, 10), …, bucket 7 = [70, 100].
  const idx = Math.min(7, Math.max(0, Math.floor(pct / 10)))
  return BUCKET_CLASSES[idx]
}

function formatCohortLabel(isoDate: string): string {
  // isoDate is the Monday of the cohort week (UTC). Show as "MMM D".
  const d = new Date(`${isoDate}T00:00:00Z`)
  return d.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    timeZone: "UTC",
  })
}

export function RetentionHeatmap({ rows }: RetentionHeatmapProps) {
  // Drop rows missing the keys we need to render.
  const valid = rows.filter(
    (r): r is RetentionCohortRow & {
      cohort_week: string
      week_offset: number
      cohort_size: number
      retention_pct: number
    } =>
      r.cohort_week !== null &&
      r.week_offset !== null &&
      r.cohort_size !== null &&
      r.retention_pct !== null,
  )

  if (valid.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        No cohorts yet. The heatmap appears once users start signing up.
      </p>
    )
  }

  // Cohort weeks: oldest on top → ascending sort.
  const cohortWeeks = Array.from(new Set(valid.map((r) => r.cohort_week))).sort()
  const maxOffset = valid.reduce((m, r) => Math.max(m, r.week_offset), 0)
  const offsets = Array.from({ length: maxOffset + 1 }, (_, i) => i)

  // Index cells by cohort_week + week_offset.
  const cellByKey = new Map<
    string,
    { retention_pct: number; cohort_size: number; active_users: number | null }
  >()
  const sizeByCohort = new Map<string, number>()
  for (const r of valid) {
    cellByKey.set(`${r.cohort_week}|${r.week_offset}`, {
      retention_pct: r.retention_pct,
      cohort_size: r.cohort_size,
      active_users: r.active_users,
    })
    sizeByCohort.set(r.cohort_week, r.cohort_size)
  }

  return (
    <div className="space-y-3">
      <div className="overflow-x-auto">
        <table className="w-full border-separate border-spacing-1 text-xs">
          <thead>
            <tr>
              <th className="sticky left-0 bg-card text-left font-medium text-muted-foreground">
                Cohort
              </th>
              <th className="text-right font-medium text-muted-foreground">Size</th>
              {offsets.map((w) => (
                <th
                  key={w}
                  className="text-center font-medium text-muted-foreground"
                  scope="col"
                >
                  W{w}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {cohortWeeks.map((week) => {
              const size = sizeByCohort.get(week) ?? 0
              return (
                <tr key={week}>
                  <th
                    scope="row"
                    className="sticky left-0 whitespace-nowrap bg-card pr-2 text-left font-medium text-muted-foreground"
                  >
                    {formatCohortLabel(week)}
                  </th>
                  <td className="pr-2 text-right tabular-nums text-muted-foreground">
                    {size}
                  </td>
                  {offsets.map((w) => {
                    const cell = cellByKey.get(`${week}|${w}`)
                    if (!cell) {
                      return (
                        <td
                          key={w}
                          className="rounded-sm bg-muted/30"
                          aria-label={`Cohort ${formatCohortLabel(week)}, week ${w}: no data`}
                        />
                      )
                    }
                    const pct = cell.retention_pct
                    const display = Math.round(pct)
                    return (
                      <td
                        key={w}
                        className={`rounded-sm text-center tabular-nums ${bucketClass(pct)}`}
                        title={`${formatCohortLabel(week)} · W${w}: ${pct}% (${cell.active_users ?? 0}/${cell.cohort_size})`}
                      >
                        <div className="flex h-7 w-9 items-center justify-center">
                          {display}
                        </div>
                      </td>
                    )
                  })}
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      <Legend />
    </div>
  )
}

function Legend() {
  return (
    <div
      className="flex items-center gap-2 text-xs text-muted-foreground"
      aria-hidden
    >
      <span>0%</span>
      <div className="flex">
        {BUCKET_CLASSES.map((cls, i) => (
          <span key={i} className={`h-3 w-4 ${cls.split(" ")[0]}`} />
        ))}
      </div>
      <span>70%+</span>
    </div>
  )
}
