import type {
  EmotionShareSnapshot,
  EmotionShareWeeklyDatum,
} from "../EmotionShareChart"

const TODAY = new Date()

function isoWeekStart(weeksAgo: number): string {
  const d = new Date(TODAY)
  d.setUTCDate(d.getUTCDate() - weeksAgo * 7)
  const dow = d.getUTCDay()
  const offset = (dow + 6) % 7
  d.setUTCDate(d.getUTCDate() - offset)
  return d.toISOString().slice(0, 10)
}

const CATALOG_BASE: { slug: string; name: string; color: string; weight: number }[] =
  [
    { slug: "joy", name: "Joy", color: "#FACC15", weight: 28 },
    { slug: "gratitude", name: "Gratitude", color: "#34D399", weight: 18 },
    { slug: "love", name: "Love", color: "#EC4899", weight: 14 },
    { slug: "anxiety", name: "Anxiety", color: "#8B5CF6", weight: 12 },
    { slug: "sadness", name: "Sadness", color: "#60A5FA", weight: 9 },
    { slug: "anger", name: "Anger", color: "#EF4444", weight: 7 },
    { slug: "nostalgia", name: "Nostalgia", color: "#D4A373", weight: 6 },
    { slug: "awe", name: "Awe", color: "#818CF8", weight: 6 },
  ]

export const denseEmotionShareCatalog = CATALOG_BASE.map(
  ({ slug, name, color }) => ({ slug, name, color }),
)

export const denseEmotionShareSnapshot: EmotionShareSnapshot[] = (() => {
  const totalWeight = CATALOG_BASE.reduce((acc, c) => acc + c.weight, 0)
  const totalPebbles = 1820
  return CATALOG_BASE.map((c) => {
    const share = (c.weight / totalWeight) * 100
    return {
      emotion_id: c.slug,
      emotion_slug: c.slug,
      emotion_name: c.name,
      color: c.color,
      pebbles_with_emotion: Math.round((c.weight / totalWeight) * totalPebbles),
      total_pebbles: totalPebbles,
      share_pct: round2(share),
    }
  }).sort((a, b) => b.share_pct - a.share_pct)
})()

export const denseEmotionShareWeekly: EmotionShareWeeklyDatum[] = Array.from(
  { length: 12 },
  (_, i) => {
    const weeksAgo = 11 - i
    const wave = Math.sin(i / 2.5)
    const weights = CATALOG_BASE.map((c, j) => {
      const drift = wave * (j % 2 === 0 ? 1 : -1) * 1.5
      return Math.max(0.5, c.weight + drift)
    })
    const sum = weights.reduce((a, b) => a + b, 0)
    const shares: Record<string, number> = {}
    CATALOG_BASE.forEach((c, j) => {
      shares[c.slug] = round2((weights[j] / sum) * 100)
    })
    return { bucket_week: isoWeekStart(weeksAgo), shares }
  },
)

export const denseEmotionShareTotalPebbles = denseEmotionShareSnapshot.reduce(
  (acc, r) => acc + r.pebbles_with_emotion,
  0,
)

export const sparseEmotionShareCatalog = denseEmotionShareCatalog.slice(0, 3)

export const sparseEmotionShareSnapshot: EmotionShareSnapshot[] = (() => {
  const total = 42
  const rows = [
    { ...CATALOG_BASE[0], weight: 5 },
    { ...CATALOG_BASE[1], weight: 3 },
    { ...CATALOG_BASE[2], weight: 2 },
  ]
  const weightSum = rows.reduce((a, r) => a + r.weight, 0)
  return rows
    .map((r) => ({
      emotion_id: r.slug,
      emotion_slug: r.slug,
      emotion_name: r.name,
      color: r.color,
      pebbles_with_emotion: Math.round((r.weight / weightSum) * total),
      total_pebbles: total,
      share_pct: round2((r.weight / weightSum) * 100),
    }))
    .sort((a, b) => b.share_pct - a.share_pct)
})()

export const sparseEmotionShareWeekly: EmotionShareWeeklyDatum[] = Array.from(
  { length: 4 },
  (_, i) => {
    const weeksAgo = 3 - i
    const shares: Record<string, number> = {
      joy: 50 - i * 4,
      gratitude: 30 + i * 2,
      love: 20 + i * 2,
    }
    return { bucket_week: isoWeekStart(weeksAgo), shares }
  },
)

export const sparseEmotionShareTotalPebbles = 42

export const emptyEmotionShareCatalog: typeof denseEmotionShareCatalog = []
export const emptyEmotionShareSnapshot: EmotionShareSnapshot[] = []
export const emptyEmotionShareWeekly: EmotionShareWeeklyDatum[] = []
export const emptyEmotionShareTotalPebbles = 0

function round2(n: number): number {
  return Math.round(n * 100) / 100
}
