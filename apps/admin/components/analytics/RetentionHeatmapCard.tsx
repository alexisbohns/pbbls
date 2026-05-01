import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { getRetentionCohorts } from "@/lib/analytics/fetchers"
import type { RetentionCohortRow } from "@/lib/analytics/types"
import { ErrorBlock } from "./ErrorBlock"
import { RetentionHeatmap } from "./RetentionHeatmap"

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
