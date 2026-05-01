import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  getEmotionShare,
  getEmotionShareLast12Weeks,
} from "@/lib/analytics/fetchers"
import {
  TIME_RANGE_LABELS,
  type EmotionShareWeeklyRow,
  type TimeRange,
} from "@/lib/analytics/types"
import { ErrorBlock } from "./ErrorBlock"
import { EmotionShare } from "./EmotionShare"
import type {
  EmotionShareSnapshot,
  EmotionShareWeeklyDatum,
} from "./EmotionShareChart"

type EmotionShareCardProps = { range: TimeRange }

export async function EmotionShareCard({ range }: EmotionShareCardProps) {
  let snapshotRows: EmotionShareWeeklyRow[]
  let weeklyRows: EmotionShareWeeklyRow[]
  try {
    ;[snapshotRows, weeklyRows] = await Promise.all([
      getEmotionShare(range),
      getEmotionShareLast12Weeks(),
    ])
  } catch (err) {
    return (
      <ErrorBlock
        label="Failed to load emotion share"
        message={err instanceof Error ? err.message : String(err)}
      />
    )
  }

  const snapshot = aggregateSnapshot(snapshotRows)
  const totalPebbles = snapshot.reduce(
    (acc, r) => acc + r.pebbles_with_emotion,
    0,
  )
  const weekly = pivotWeekly(weeklyRows)
  const catalog = buildCatalog(weeklyRows, snapshotRows)

  return (
    <Card>
      <CardHeader>
        <CardTitle>Emotion share</CardTitle>
      </CardHeader>
      <CardContent>
        <EmotionShare
          snapshot={snapshot}
          weekly={weekly}
          catalog={catalog}
          totalPebbles={totalPebbles}
          rangeLabel={TIME_RANGE_LABELS[range]}
        />
      </CardContent>
    </Card>
  )
}

function aggregateSnapshot(
  rows: EmotionShareWeeklyRow[],
): EmotionShareSnapshot[] {
  // Sum pebbles_with_emotion per emotion across weeks; sum total_pebbles by
  // taking each week's total once (not once per emotion row).
  const byEmotion = new Map<string, EmotionShareSnapshot>()
  const weekTotals = new Map<string, number>()

  for (const r of rows) {
    if (!r.emotion_id || !r.bucket_week) continue
    const prev = byEmotion.get(r.emotion_id)
    const count = r.pebbles_with_emotion ?? 0
    if (prev) {
      prev.pebbles_with_emotion += count
    } else {
      byEmotion.set(r.emotion_id, {
        emotion_id: r.emotion_id,
        emotion_slug: r.emotion_slug ?? r.emotion_id,
        emotion_name: r.emotion_name ?? "—",
        color: r.color ?? "var(--muted)",
        pebbles_with_emotion: count,
        total_pebbles: 0,
        share_pct: 0,
      })
    }
    if (!weekTotals.has(r.bucket_week)) {
      weekTotals.set(r.bucket_week, r.total_pebbles ?? 0)
    }
  }

  let total = 0
  for (const t of weekTotals.values()) total += t

  const out = Array.from(byEmotion.values()).map((e) => ({
    ...e,
    total_pebbles: total,
    share_pct: total > 0 ? round2((e.pebbles_with_emotion / total) * 100) : 0,
  }))
  out.sort((a, b) => b.share_pct - a.share_pct)
  return out
}

function pivotWeekly(rows: EmotionShareWeeklyRow[]): EmotionShareWeeklyDatum[] {
  const byWeek = new Map<string, EmotionShareWeeklyDatum>()
  for (const r of rows) {
    if (!r.bucket_week || !r.emotion_slug) continue
    let entry = byWeek.get(r.bucket_week)
    if (!entry) {
      entry = { bucket_week: r.bucket_week, shares: {} }
      byWeek.set(r.bucket_week, entry)
    }
    entry.shares[r.emotion_slug] = r.share_pct ?? 0
  }
  return Array.from(byWeek.values()).sort((a, b) =>
    a.bucket_week < b.bucket_week ? -1 : 1,
  )
}

function buildCatalog(
  weekly: EmotionShareWeeklyRow[],
  snapshot: EmotionShareWeeklyRow[],
): { slug: string; name: string; color: string }[] {
  // Rank emotions by total pebbles_with_emotion across the snapshot range so
  // the legend and stack order match the snapshot bars.
  const totals = new Map<
    string,
    { slug: string; name: string; color: string; total: number }
  >()
  const seed = (rows: EmotionShareWeeklyRow[]) => {
    for (const r of rows) {
      if (!r.emotion_slug) continue
      const prev = totals.get(r.emotion_slug)
      const count = r.pebbles_with_emotion ?? 0
      if (prev) {
        prev.total += count
      } else {
        totals.set(r.emotion_slug, {
          slug: r.emotion_slug,
          name: r.emotion_name ?? "—",
          color: r.color ?? "var(--muted)",
          total: count,
        })
      }
    }
  }
  seed(snapshot)
  // Include any emotion present only in the 12-week window so the area chart
  // doesn't drop weeks it would otherwise render.
  seed(weekly)
  return Array.from(totals.values())
    .sort((a, b) => b.total - a.total)
    .map(({ slug, name, color }) => ({ slug, name, color }))
}

function round2(n: number): number {
  return Math.round(n * 100) / 100
}
