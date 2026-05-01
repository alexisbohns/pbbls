/**
 * Admin · Analytics · TS contracts (thin slice).
 *
 * Row types mirror the SQL views in
 * packages/supabase/supabase/migrations/20260430000000_analytics_thin_slice.sql
 *
 * Fields are nullable where the underlying view can produce NULLs (e.g. when
 * no data exists for a bucket). Callers are responsible for handling nulls
 * before rendering.
 */

export type IsoDate = string

export interface KpiDailyRow {
  bucket_date: IsoDate | null
  total_users: number | null
  dau: number | null
  pebbles_today: number | null
  wau: number | null
  mau: number | null
  /** DAU / MAU as a 0–100 percent value. Null when MAU = 0. */
  dau_mau_pct: number | null
}

export interface ActiveUsersDailyRow {
  bucket_date: IsoDate | null
  dau: number | null
  wau: number | null
  mau: number | null
}

export interface RetentionCohortRow {
  /** Monday of the cohort signup week (UTC), ISO date string. */
  cohort_week: IsoDate | null
  /** Weeks since signup (0 = signup week). */
  week_offset: number | null
  cohort_size: number | null
  active_users: number | null
  /** 0–100 retention percent for this (cohort, week_offset) cell. */
  retention_pct: number | null
}

export interface PebbleVolumeRow {
  bucket_date: IsoDate | null
  pebbles: number | null
  pebbles_with_picture: number | null
  pebbles_in_collection: number | null
  pebbles_with_custom_glyph: number | null
  active_users: number | null
}

export interface PebbleEnrichmentRow {
  total_pebbles: number | null
  /** 0–100 percent values. Null when total_pebbles = 0. */
  pct_with_picture: number | null
  pct_in_collection: number | null
  pct_with_custom_glyph: number | null
  pct_with_thought: number | null
  pct_with_soul: number | null
  pct_with_intensity: number | null
}

export interface EmotionShareWeeklyRow {
  /** Monday of the ISO week (UTC), ISO date string. */
  bucket_week: IsoDate | null
  emotion_id: string | null
  emotion_slug: string | null
  emotion_name: string | null
  /** Hex color from the emotions reference table. */
  color: string | null
  pebbles_with_emotion: number | null
  total_pebbles: number | null
  /** 0–100 percent share of pebbles in this week assigned this emotion. */
  share_pct: number | null
}

export interface DomainShareWeeklyRow {
  /** Monday of the ISO week (UTC), ISO date string. */
  bucket_week: IsoDate | null
  domain_id: string | null
  domain_slug: string | null
  domain_name: string | null
  domain_label: string | null
  /** Maslow rank derived from seeded slug order. NULL for unknown slugs. */
  domain_level: number | null
  pebbles_in_domain: number | null
  total_pebbles: number | null
  /** 0–100 percent share of pebbles in this week linked to this domain. */
  share_pct: number | null
}

export interface BounceDistributionRow {
  bucket_date: IsoDate | null
  /** 0..5 ordering, matches the canonical bucket sequence. */
  bucket_order: number | null
  /** Human-readable bucket: "0", "1-10", "11-25", "26-50", "51-100", "100+". */
  bucket_label: string | null
  users: number | null
  /** Median current bounce score across all users. Null when zero users. */
  median_score: number | null
  /** % of users whose current bounce >= their bounce 7 days ago. 0–100. */
  pct_maintaining: number | null
  /** Avg distinct active days in the last 7 days, across all MAU. 0–7. */
  avg_active_days_per_week: number | null
}

export type QualitySignalUnit = "percent" | "seconds" | "pebbles" | "sessions" | "events"

export interface QualitySignalRow {
  bucket_date: IsoDate | null
  /** 1..8 ordering, matches the spec / mockup row order. */
  indicator_order: number | null
  /** Stable machine key (e.g. "d1_retention"). */
  indicator_key: string | null
  /** Human label for the row (e.g. "D1 retention"). */
  indicator_label: string | null
  unit: QualitySignalUnit | null
  /** Current-period value. Null when `available = false` or computation has no denominator. */
  value: number | null
  /** Same metric for the matching prior period. Null when `available = false`. */
  previous_value: number | null
  /** True when the metric is computable from existing data; false for Phase B/C placeholders. */
  available: boolean | null
}

export interface UserAveragesWeeklyRow {
  /** Monday of the ISO week (UTC), ISO date string. */
  bucket_week: IsoDate | null
  active_users: number | null
  /** Per-active-user averages rounded to 2 decimals. 0 when active_users = 0. */
  avg_glyphs: number | null
  avg_souls: number | null
  avg_collections: number | null
}

export type TimeRange = "7d" | "30d" | "90d" | "1y" | "all"

export type ActivityMetric = "dau" | "wau" | "mau" | "all"

export type VolumeBucket = "day" | "week" | "month" | "year"

export const VOLUME_BUCKETS: readonly VolumeBucket[] = ["day", "week", "month", "year"] as const

export const VOLUME_BUCKET_LABELS: Record<VolumeBucket, string> = {
  day: "Day",
  week: "Week",
  month: "Month",
  year: "Year",
}

export const TIME_RANGES: readonly TimeRange[] = ["7d", "30d", "90d", "1y", "all"] as const

export const TIME_RANGE_LABELS: Record<TimeRange, string> = {
  "7d": "7 days",
  "30d": "30 days",
  "90d": "90 days",
  "1y": "1 year",
  all: "All time",
}

export function isTimeRange(value: string | undefined): value is TimeRange {
  return value !== undefined && (TIME_RANGES as readonly string[]).includes(value)
}
