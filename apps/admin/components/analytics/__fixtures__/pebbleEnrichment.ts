import type { PebbleEnrichmentRow } from "@/lib/analytics/types"

const TODAY = new Date().toISOString().slice(0, 10)

export const denseEnrichmentFixture: PebbleEnrichmentRow = {
  bucket_date: TODAY,
  total_pebbles: 142,
  pct_with_picture: 41,
  pct_in_collection: 56,
  pct_with_thought: 73,
  pct_with_soul: 48,
  pct_with_intensity: 100,
}

export const sparseEnrichmentFixture: PebbleEnrichmentRow = {
  bucket_date: TODAY,
  total_pebbles: 4,
  pct_with_picture: 0,
  pct_in_collection: 25,
  pct_with_thought: 50,
  pct_with_soul: 0,
  pct_with_intensity: 100,
}

export const emptyEnrichmentFixture: PebbleEnrichmentRow | null = null
