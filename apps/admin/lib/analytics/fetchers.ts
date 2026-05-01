import { createServerSupabaseClient } from "@/lib/supabase/server"
import { dateRangeFor } from "./date"
import type {
  ActiveUsersDailyRow,
  KpiDailyRow,
  PebbleEnrichmentRow,
  PebbleVolumeRow,
  RetentionCohortRow,
  TimeRange,
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
 * Latest enrichment snapshot (most recent day with ≥1 pebble). One row.
 * The RPC enforces `is_admin(auth.uid())`.
 */
export async function getPebbleEnrichmentToday(): Promise<PebbleEnrichmentRow | null> {
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.rpc("get_pebble_enrichment_today")
  if (error) {
    console.error("[analytics] getPebbleEnrichmentToday failed:", error.message)
    throw new Error(error.message)
  }
  return data?.[0] ?? null
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
