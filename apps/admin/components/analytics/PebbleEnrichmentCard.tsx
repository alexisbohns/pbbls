import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { getPebbleEnrichment } from "@/lib/analytics/fetchers"
import {
  TIME_RANGE_LABELS,
  type PebbleEnrichmentRow,
  type TimeRange,
} from "@/lib/analytics/types"
import { ErrorBlock } from "./ErrorBlock"
import { PebbleEnrichment } from "./PebbleEnrichment"

type PebbleEnrichmentCardProps = { range: TimeRange }

export async function PebbleEnrichmentCard({ range }: PebbleEnrichmentCardProps) {
  let row: PebbleEnrichmentRow | null
  try {
    row = await getPebbleEnrichment(range)
  } catch (err) {
    return (
      <ErrorBlock
        label="Failed to load pebble enrichment"
        message={err instanceof Error ? err.message : String(err)}
      />
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Pebble enrichment</CardTitle>
      </CardHeader>
      <CardContent>
        <PebbleEnrichment row={row} rangeLabel={TIME_RANGE_LABELS[range]} />
      </CardContent>
    </Card>
  )
}
