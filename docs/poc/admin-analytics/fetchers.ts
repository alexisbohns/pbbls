/**
 * Admin · Analytics · Server-side fetchers
 * ----------------------------------------------------------------------------
 * One thin async function per chart. All functions:
 *   - run on the server (Server Components or Route Handlers).
 *   - assume the caller has already authenticated as admin (RLS enforces it
 *     too — defense in depth, not a substitute for the RLS policies in the
 *     20260430_analytics_mvs.sql migration).
 *   - return strongly-typed rows from the corresponding materialized view.
 *
 * Source of truth for shapes: ./types.ts
 */

import { createClient } from "@supabase/supabase-js";
import type {
  ActiveUsersDailyRow,
  BounceDistributionRow,
  CairnParticipationRow,
  DomainShareRow,
  EmotionShareRow,
  IsoDate,
  KpiDailyRow,
  PebbleEnrichmentDailyRow,
  PebbleVolumeDailyRow,
  QualitySignalsRow,
  RetentionCohortRow,
  TimeRange,
  UserAveragesWeeklyRow,
  VisibilityMixRow,
} from "./types";
import { dateRangeFor } from "./types";

// ---------------------------------------------------------------------------
// Client
// ---------------------------------------------------------------------------

/** Server-only Supabase client with admin JWT context propagated. */
function adminSupabase() {
  // The admin shell sets these in the request scope. If you wire SSR cookies
  // via @supabase/ssr, replace this with the cookie-aware client instead.
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL!;
  const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!;
  return createClient(url, anon, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
}

// ---------------------------------------------------------------------------
// Single-row "current value" fetcher (latest bucket_date in any daily MV)
// ---------------------------------------------------------------------------

export async function getKpiToday(): Promise<KpiDailyRow | null> {
  const sb = adminSupabase();
  const { data, error } = await sb
    .from("mv_kpi_daily")
    .select("*")
    .order("bucket_date", { ascending: false })
    .limit(1)
    .maybeSingle();
  if (error) throw error;
  return data;
}

// ---------------------------------------------------------------------------
// Time-series fetchers (one per chart)
// ---------------------------------------------------------------------------

export async function getActiveUsersSeries(
  range: TimeRange
): Promise<ActiveUsersDailyRow[]> {
  const { start, end } = dateRangeFor(range);
  const sb = adminSupabase();
  const { data, error } = await sb
    .from("mv_active_users_daily")
    .select("*")
    .gte("bucket_date", start)
    .lte("bucket_date", end)
    .order("bucket_date", { ascending: true });
  if (error) throw error;
  return data ?? [];
}

export async function getRetentionCohorts(): Promise<RetentionCohortRow[]> {
  // Always return the last 8 cohorts regardless of the global range tab —
  // retention cohorts are intrinsically tied to signup-week granularity.
  const sb = adminSupabase();
  const { data, error } = await sb
    .from("mv_retention_cohorts_weekly")
    .select("*")
    .gte("cohort_week", isoWeeksAgo(8))
    .order("cohort_week", { ascending: false })
    .order("week_offset", { ascending: true });
  if (error) throw error;
  return data ?? [];
}

export async function getPebbleVolumeSeries(
  range: TimeRange
): Promise<PebbleVolumeDailyRow[]> {
  const { start, end } = dateRangeFor(range);
  const sb = adminSupabase();
  const { data, error } = await sb
    .from("mv_pebble_volume_daily")
    .select("*")
    .gte("bucket_date", start)
    .lte("bucket_date", end)
    .order("bucket_date", { ascending: true });
  if (error) throw error;
  return data ?? [];
}

export async function getPebbleEnrichmentToday(): Promise<PebbleEnrichmentDailyRow | null> {
  const sb = adminSupabase();
  const { data, error } = await sb
    .from("mv_pebble_enrichment_daily")
    .select("*")
    .order("bucket_date", { ascending: false })
    .limit(1)
    .maybeSingle();
  if (error) throw error;
  return data;
}

export async function getUserAveragesSeries(
  weeks = 12
): Promise<UserAveragesWeeklyRow[]> {
  const sb = adminSupabase();
  const { data, error } = await sb
    .from("mv_user_averages_weekly")
    .select("*")
    .gte("bucket_week", isoWeeksAgo(weeks))
    .order("bucket_week", { ascending: true });
  if (error) throw error;
  return data ?? [];
}

export async function getBounceDistributionToday(): Promise<BounceDistributionRow[]> {
  const sb = adminSupabase();
  const { data, error } = await sb
    .from("mv_bounce_distribution_daily")
    .select("*")
    .eq("bucket_date", await latestBucketDate("mv_bounce_distribution_daily"))
    .order("bucket_order", { ascending: true });
  if (error) throw error;
  return data ?? [];
}

export async function getEmotionShare(
  range: TimeRange
): Promise<EmotionShareRow[]> {
  const { start, end } = dateRangeFor(range);
  const sb = adminSupabase();
  const { data, error } = await sb
    .from("mv_emotion_share_weekly")
    .select("*")
    .gte("bucket_week", start)
    .lte("bucket_week", end)
    .order("bucket_week", { ascending: true })
    .order("share_pct",  { ascending: false });
  if (error) throw error;
  return data ?? [];
}

export async function getDomainShare(
  range: TimeRange
): Promise<DomainShareRow[]> {
  const { start, end } = dateRangeFor(range);
  const sb = adminSupabase();
  const { data, error } = await sb
    .from("mv_domain_share_weekly")
    .select("*")
    .gte("bucket_week", start)
    .lte("bucket_week", end)
    .order("bucket_week", { ascending: true })
    .order("domain_level", { ascending: true });
  if (error) throw error;
  return data ?? [];
}

export async function getCairnParticipation(): Promise<CairnParticipationRow[]> {
  const sb = adminSupabase();
  const { data, error } = await sb
    .from("mv_cairn_participation_weekly")
    .select("*")
    .order("period",       { ascending: true })
    .order("period_start", { ascending: false });
  if (error) throw error;
  return data ?? [];
}

export async function getVisibilityMix(
  range: TimeRange
): Promise<VisibilityMixRow[]> {
  const { start, end } = dateRangeFor(range);
  const sb = adminSupabase();
  const { data, error } = await sb
    .from("mv_visibility_mix_daily")
    .select("*")
    .gte("bucket_date", start)
    .lte("bucket_date", end);
  if (error) throw error;
  return data ?? [];
}

export async function getQualitySignalsToday(): Promise<QualitySignalsRow | null> {
  const sb = adminSupabase();
  const { data, error } = await sb
    .from("mv_quality_signals_daily")
    .select("*")
    .order("bucket_date", { ascending: false })
    .limit(1)
    .maybeSingle();
  if (error) throw error;
  return data;
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function isoWeeksAgo(weeks: number): IsoDate {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() - weeks * 7);
  return d.toISOString().slice(0, 10);
}

async function latestBucketDate(view: string): Promise<IsoDate> {
  const sb = adminSupabase();
  const { data, error } = await sb
    .from(view)
    .select("bucket_date")
    .order("bucket_date", { ascending: false })
    .limit(1)
    .maybeSingle();
  if (error) throw error;
  return (data?.bucket_date as IsoDate) ?? new Date().toISOString().slice(0, 10);
}
