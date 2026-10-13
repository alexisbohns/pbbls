import type { PebbleEnrichmentRow } from "@/lib/analytics/types"

export const denseEnrichmentFixture: PebbleEnrichmentRow = {
  total_pebbles: 142,
  pct_with_picture: 41,
  pct_in_collection: 56,
  pct_with_custom_glyph: 32,
  pct_with_thought: 73,
  pct_with_soul: 48,
  pct_with_intensity: 100,
  total_moments: 180,
  pct_moments_completed: 78.9,
}

export const sparseEnrichmentFixture: PebbleEnrichmentRow = {
  total_pebbles: 4,
  pct_with_picture: 0,
  pct_in_collection: 25,
  pct_with_custom_glyph: 25,
  pct_with_thought: 50,
  pct_with_soul: 0,
  pct_with_intensity: 100,
  total_moments: 9,
  pct_moments_completed: 44.4,
}

/** Moments were recorded in the window but none has been completed yet. */
export const bareMomentsEnrichmentFixture: PebbleEnrichmentRow = {
  total_pebbles: 0,
  pct_with_picture: null,
  pct_in_collection: null,
  pct_with_custom_glyph: null,
  pct_with_thought: null,
  pct_with_soul: null,
  pct_with_intensity: null,
  total_moments: 3,
  pct_moments_completed: 0,
}

export const emptyEnrichmentFixture: PebbleEnrichmentRow | null = null
