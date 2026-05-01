import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { getUserAveragesSeries } from "@/lib/analytics/fetchers"
import type { UserAveragesWeeklyRow } from "@/lib/analytics/types"
import { ErrorBlock } from "./ErrorBlock"
import { UserAverages } from "./UserAverages"
import type { UserAveragesChartDatum } from "./UserAveragesChart"

const WEEKS = 12

export async function UserAveragesCard() {
  let rows: UserAveragesWeeklyRow[]
  try {
    rows = await getUserAveragesSeries(WEEKS)
  } catch (err) {
    return (
      <ErrorBlock
        label="Failed to load per-user averages"
        message={err instanceof Error ? err.message : String(err)}
      />
    )
  }

  const data: UserAveragesChartDatum[] = rows
    .filter((r): r is UserAveragesWeeklyRow & { bucket_week: string } =>
      r.bucket_week !== null,
    )
    .map((r) => ({
      bucket_week: r.bucket_week,
      active_users: r.active_users ?? 0,
      avg_glyphs: r.avg_glyphs ?? 0,
      avg_souls: r.avg_souls ?? 0,
      avg_collections: r.avg_collections ?? 0,
    }))

  return (
    <Card>
      <CardHeader>
        <CardTitle>Per-user weekly averages</CardTitle>
      </CardHeader>
      <CardContent>
        <UserAverages data={data} />
      </CardContent>
    </Card>
  )
}
