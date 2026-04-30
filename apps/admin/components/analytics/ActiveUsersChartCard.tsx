import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { getActiveUsersSeries } from "@/lib/analytics/fetchers"
import type { ActiveUsersDailyRow, TimeRange } from "@/lib/analytics/types"
import { ActiveUsersChart, type ActiveUsersChartDatum } from "./ActiveUsersChart"
import { ErrorBlock } from "./ErrorBlock"

type ActiveUsersChartCardProps = { range: TimeRange }

export async function ActiveUsersChartCard({ range }: ActiveUsersChartCardProps) {
  let rows: ActiveUsersDailyRow[]
  try {
    rows = await getActiveUsersSeries(range)
  } catch (err) {
    return (
      <ErrorBlock
        label="Failed to load active users chart"
        message={err instanceof Error ? err.message : String(err)}
      />
    )
  }

  // Filter out rows missing bucket_date — they can't be plotted.
  const data: ActiveUsersChartDatum[] = rows
    .filter((r): r is ActiveUsersDailyRow & { bucket_date: string } => r.bucket_date !== null)
    .map((r) => ({
      bucket_date: r.bucket_date,
      dau: r.dau,
      wau: r.wau,
      mau: r.mau,
    }))

  return (
    <Card>
      <CardHeader>
        <CardTitle>Active users over time</CardTitle>
      </CardHeader>
      <CardContent>
        <ActiveUsersChart data={data} />
      </CardContent>
    </Card>
  )
}
