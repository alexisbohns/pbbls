import { createServerSupabaseClient } from "@/lib/supabase/server"
import { dateRangeFor, periodLengthDays, shiftIsoDate } from "./date"
import type {
  ActiveUsersDailyRow,
  DomainShareWeeklyRow,
  EmotionShareWeeklyRow,
  IsoDate,
  KpiDailyRow,
  PebbleEnrichmentRow,
  PebbleVolumeRow,
  RetentionCohortRow,
  TimeRange,
  UserAveragesWeeklyRow,
  VolumeBucket,
} from "./types"

/**
 * Fetch the rows needed by the KPI strip:
 *   - the latest row (current values + delta source)
 *   - the row from `period_length` days earlier (for delta)
 *   - the last 30 days (for sparklines)
 *
 * Returned by `get_kpi_daily(p_range)` in the migration. The RPC enforces
 * `is_admin(auth.uid())`; callers must be admin or this throws.
 */
export async function getKpiDaily(range: TimeRange): Promise<KpiDailyRow[]> {
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.rpc("get_kpi_daily", { p_range: range })
  if (error) {
    console.error("[analytics] getKpiDaily failed:", error.message)
    throw error
  }
  return data ?? []
}

/**
 * Daily DAU/WAU/MAU series for the active-users chart, scoped to the global
 * time range tab.
 */
export async function getActiveUsersSeries(
  range: TimeRange,
): Promise<ActiveUsersDailyRow[]> {
  const { start, end } = dateRangeFor(range)
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.rpc("get_active_users_series", {
    p_start: start,
    p_end: end,
  })
  if (error) {
    console.error("[analytics] getActiveUsersSeries failed:", error.message)
    throw error
  }
  return data ?? []
}

/**
 * Pebble volume bars + overlay-line counts (`with picture`, `in collection`)
 * for the global time range, aggregated at the requested bucket granularity.
 * The RPC enforces `is_admin(auth.uid())`.
 */
export async function getPebbleVolumeSeries(
  range: TimeRange,
  bucket: VolumeBucket = "day",
): Promise<PebbleVolumeRow[]> {
  const { start, end } = dateRangeFor(range)
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.rpc("get_pebble_volume_series", {
    p_start: start,
    p_end: end,
    p_bucket: bucket,
  })
  if (error) {
    console.error("[analytics] getPebbleVolumeSeries failed:", error.message)
    throw new Error(error.message)
  }
  return data ?? []
}

/**
 * Enrichment shares (donuts + secondary ratios) aggregated over the global
 * time range. Returns null when zero pebbles were created in the window.
 * The RPC enforces `is_admin(auth.uid())`.
 */
export async function getPebbleEnrichment(
  range: TimeRange,
): Promise<PebbleEnrichmentRow | null> {
  const { start, end } = dateRangeFor(range)
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.rpc("get_pebble_enrichment", {
    p_start: start,
    p_end: end,
  })
  if (error) {
    console.error("[analytics] getPebbleEnrichment failed:", error.message)
    throw new Error(error.message)
  }
  return data?.[0] ?? null
}

/**
 * Per-user weekly averages (glyphs / souls / collections) for the most recent
 * `weeks` ISO weeks, ordered ascending. Default 12 weeks.
 * The RPC enforces `is_admin(auth.uid())`.
 */
export async function getUserAveragesSeries(
  weeks = 12,
): Promise<UserAveragesWeeklyRow[]> {
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.rpc("get_user_averages_series", {
    p_weeks: weeks,
  })
  if (error) {
    console.error("[analytics] getUserAveragesSeries failed:", error.message)
    throw new Error(error.message)
  }
  return data ?? []
}

/**
 * Weekly emotion share rows over the snapshot range. Used by the emotion-share
 * card for both the snapshot view (aggregated across weeks) and the stacked-
 * area over-time view. The RPC enforces `is_admin(auth.uid())`.
 */
export async function getEmotionShare(
  range: TimeRange,
): Promise<EmotionShareWeeklyRow[]> {
  const { start, end } = dateRangeFor(range)
  return fetchEmotionShare(start, end)
}

/**
 * Always-12-weeks emotion share rows for the over-time stacked area, regardless
 * of the active time-range tab. The over-time framing in the spec is fixed at
 * 12 weeks so the chart shape stays comparable across snapshots.
 */
export async function getEmotionShareLast12Weeks(): Promise<EmotionShareWeeklyRow[]> {
  const today = new Date()
  const end = today.toISOString().slice(0, 10)
  // 12 weeks back, week-start aligned by the SQL view (date_trunc('week', ...)).
  const start = shiftIsoDate(end, -7 * 11)
  return fetchEmotionShare(start, end)
}

async function fetchEmotionShare(
  start: IsoDate,
  end: IsoDate,
): Promise<EmotionShareWeeklyRow[]> {
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.rpc("get_emotion_share", {
    p_start: start,
    p_end: end,
  })
  if (error) {
    console.error("[analytics] getEmotionShare failed:", error.message)
    throw new Error(error.message)
  }
  return data ?? []
}

/**
 * Weekly domain share rows for the snapshot range, plus a parallel set for the
 * matching previous period (used to compute "biggest movers" deltas in pp).
 * Returns null for `previous` when the active range is "all" (no prior period).
 * Both RPC calls enforce `is_admin(auth.uid())`.
 */
export async function getDomainShare(
  range: TimeRange,
): Promise<{
  current: DomainShareWeeklyRow[]
  previous: DomainShareWeeklyRow[] | null
}> {
  const { start, end } = dateRangeFor(range)
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.rpc("get_domain_share", {
    p_start: start,
    p_end: end,
  })
  if (error) {
    console.error("[analytics] getDomainShare current failed:", error.message)
    throw new Error(error.message)
  }
  const current = data ?? []

  const days = periodLengthDays(range)
  if (days === null) {
    return { current, previous: null }
  }
  const prevEnd = shiftIsoDate(start, -1)
  const prevStart = shiftIsoDate(prevEnd, -(days - 1))
  const { data: prevData, error: prevError } = await supabase.rpc(
    "get_domain_share",
    { p_start: prevStart, p_end: prevEnd },
  )
  if (prevError) {
    console.error("[analytics] getDomainShare previous failed:", prevError.message)
    throw new Error(prevError.message)
  }
  return { current, previous: prevData ?? [] }
}

/**
 * Last 8 weekly signup cohorts and their per-week retention percentages.
 * The RPC enforces `is_admin(auth.uid())`.
 */
export async function getRetentionCohorts(): Promise<RetentionCohortRow[]> {
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.rpc("get_retention_cohorts")
  if (error) {
    console.error("[analytics] getRetentionCohorts failed:", error.message)
    // PostgrestError isn't an Error subclass; rewrap so consumers (e.g.
    // ErrorBlock with `err instanceof Error ? err.message : String(err)`)
    // get a readable string instead of "[object Object]".
    throw new Error(error.message)
  }
  return data ?? []
}
