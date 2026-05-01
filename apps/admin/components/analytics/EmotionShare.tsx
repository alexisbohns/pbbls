import {
  EmotionShareChart,
  type EmotionShareSnapshot,
  type EmotionShareWeeklyDatum,
} from "./EmotionShareChart"

export type EmotionShareProps = {
  snapshot: EmotionShareSnapshot[]
  weekly: EmotionShareWeeklyDatum[]
  catalog: { slug: string; name: string; color: string }[]
  totalPebbles: number
  rangeLabel?: string
}

export function EmotionShare({
  snapshot,
  weekly,
  catalog,
  totalPebbles,
  rangeLabel,
}: EmotionShareProps) {
  if (totalPebbles === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        No pebbles {rangeLabel ? `in the last ${rangeLabel.toLowerCase()}` : "yet"} —
        emotion share appears once users start collecting.
      </p>
    )
  }

  return (
    <EmotionShareChart
      snapshot={snapshot}
      weekly={weekly}
      catalog={catalog}
      totalPebbles={totalPebbles}
      rangeLabel={rangeLabel}
    />
  )
}
