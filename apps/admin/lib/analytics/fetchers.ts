import { createServerSupabaseClient } from "@/lib/supabase/server"
import { dateRangeFor } from "./date"
import type {
  ActiveUsersDailyRow,
  KpiDailyRow,
  RetentionCohortRow,
  TimeRange,
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
