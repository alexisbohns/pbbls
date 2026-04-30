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

export type TimeRange = "7d" | "30d" | "90d" | "1y" | "all"

export type ActivityMetric = "dau" | "wau" | "mau" | "all"

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
