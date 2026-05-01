import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { getQualitySignalsToday } from "@/lib/analytics/fetchers"
import type { QualitySignalRow } from "@/lib/analytics/types"
import { ErrorBlock } from "./ErrorBlock"
import { QualitySignalsTable } from "./QualitySignalsTable"

export async function QualitySignalsTableCard() {
  let rows: QualitySignalRow[]
  try {
    rows = await getQualitySignalsToday()
  } catch (err) {
    return (
      <ErrorBlock
        label="Failed to load quality signals"
        message={err instanceof Error ? err.message : String(err)}
      />
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Quality signals</CardTitle>
      </CardHeader>
      <CardContent>
        <QualitySignalsTable rows={rows} />
      </CardContent>
    </Card>
  )
}
