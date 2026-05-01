import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { getPebbleVolumeSeries } from "@/lib/analytics/fetchers"
import type { PebbleVolumeRow, TimeRange } from "@/lib/analytics/types"
import { ErrorBlock } from "./ErrorBlock"
import {
  PebbleVolumeChart,
  type PebbleVolumeChartDatum,
} from "./PebbleVolumeChart"

type PebbleVolumeChartCardProps = { range: TimeRange }

export async function PebbleVolumeChartCard({ range }: PebbleVolumeChartCardProps) {
  let rows: PebbleVolumeRow[]
  try {
    // Always fetch at daily granularity; the client component aggregates for
    // the Day/Week/Month/Year toggle.
    rows = await getPebbleVolumeSeries(range, "day")
  } catch (err) {
    return (
      <ErrorBlock
        label="Failed to load pebble volume chart"
        message={err instanceof Error ? err.message : String(err)}
      />
    )
  }

  const data: PebbleVolumeChartDatum[] = rows
    .filter((r): r is PebbleVolumeRow & { bucket_date: string } => r.bucket_date !== null)
    .map((r) => ({
      bucket_date: r.bucket_date,
      pebbles: r.pebbles ?? 0,
      pebbles_with_picture: r.pebbles_with_picture ?? 0,
      pebbles_in_collection: r.pebbles_in_collection ?? 0,
    }))

  return (
    <Card>
      <CardHeader>
        <CardTitle>Pebbles collected</CardTitle>
      </CardHeader>
      <CardContent>
        <PebbleVolumeChart data={data} />
      </CardContent>
    </Card>
  )
}
