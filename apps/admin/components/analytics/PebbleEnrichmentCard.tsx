import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { getPebbleEnrichmentToday } from "@/lib/analytics/fetchers"
import type { PebbleEnrichmentRow } from "@/lib/analytics/types"
import { ErrorBlock } from "./ErrorBlock"
import { PebbleEnrichment } from "./PebbleEnrichment"

export async function PebbleEnrichmentCard() {
  let row: PebbleEnrichmentRow | null
  try {
    row = await getPebbleEnrichmentToday()
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
        <PebbleEnrichment row={row} />
      </CardContent>
    </Card>
  )
}
