/**
 * Admin · Analytics · Data contracts
 * ----------------------------------------------------------------------------
 * One TypeScript type per materialized view, mirroring the SQL row shape
 * exactly. These are the contracts each chart consumes.
 *
 * Source of truth: docs/specs/admin-analytics.md
 *                  supabase/migrations/20260430_analytics_mvs.sql
 *
 * Naming:
 *   - Row types end with `Row` (e.g. `KpiDailyRow`).
 *   - View name ↔ row type mapping is exported as `ViewRow` for codegen-style
 *     access elsewhere.
 *
 * NOTE: Do not put presentation logic here. Only data shapes.
 */

// ---------------------------------------------------------------------------
// Shared primitives
// ---------------------------------------------------------------------------

/** ISO date string `YYYY-MM-DD`, always UTC. */
export type IsoDate = string;

/** Visibility levels for a pebble. */
export type Visibility = "public" | "private" | "secret";

/** Cairn period. */
export type CairnPeriod = "weekly" | "monthly";

/** Cairn outcome for a given user × period. */
export type CairnStatus = "completed" | "partial" | "missed";

/** Bounce histogram bucket label, ordered low→high. */
export type BounceBucketLabel =
  | "0"
  | "1-10"
  | "11-25"
  | "26-50"
  | "51-100"
  | "100+";

// ---------------------------------------------------------------------------
// MV row types — one per materialized view
// ---------------------------------------------------------------------------

/** mv_kpi_daily — one row per day. Powers the KPI strip. */
export interface KpiDailyRow {
  bucket_date: IsoDate;
  total_users: number;
  dau: number;
  pebbles_today: number;
  wau: number;
  mau: number;
  /** DAU / MAU as a 0–100 percent value (not 0–1). */
  dau_mau_pct: number | null;
}

/** mv_active_users_daily — daily DAU/WAU/MAU series for the line chart. */
export interface ActiveUsersDailyRow {
  bucket_date: IsoDate;
  dau: number;
  wau: number;
  mau: number;
}

/** mv_retention_cohorts_weekly — one row per (cohort, week_offset). */
export interface RetentionCohortRow {
  /** Monday of the signup week (UTC). */
  cohort_week: IsoDate;
  /** Weeks since signup. 0 = signup week. */
  week_offset: number;
  cohort_size: number;
  active_users: number;
  /** % of cohort active that week. 0–100. */
  retention_pct: number;
}

/** mv_pebble_volume_daily — pebble counts and enrichment counts per day. */
export interface PebbleVolumeDailyRow {
  bucket_date: IsoDate;
  pebbles: number;
  pebbles_with_picture: number;
  pebbles_with_custom_glyph: number;
  pebbles_in_collection: number;
  active_users: number;
}

/** mv_pebble_enrichment_daily — pre-computed enrichment shares per day. */
export interface PebbleEnrichmentDailyRow {
  bucket_date: IsoDate;
  total_pebbles: number;
  /** All pct fields are 0–100. */
  pct_with_picture: number;
  pct_with_custom_glyph: number;
  pct_in_collection: number;
  pct_with_emotion: number;
  pct_with_soul: number;
  pct_with_thought: number;
  pct_with_intensity: number;
}

/** mv_user_averages_weekly — averages of glyphs/souls/collections per active user, weekly. */
export interface UserAveragesWeeklyRow {
  /** Monday of the bucket week (UTC). */
  bucket_week: IsoDate;
  active_users: number;
  avg_glyphs: number;
  avg_souls: number;
  avg_collections: number;
}

/** mv_bounce_distribution_daily — one row per histogram bucket per day. */
export interface BounceDistributionRow {
  bucket_date: IsoDate;
  bucket_order: number;
  bucket_label: BounceBucketLabel;
  users: number;
  median_score: number;
  /** % of users whose bounce did not decrease vs 7 days ago. 0–100. */
  pct_maintaining: number;
  avg_active_days_per_week: number;
}

/** mv_emotion_share_weekly — share of pebbles tagged with each emotion, per week. */
export interface EmotionShareRow {
  bucket_week: IsoDate;
  emotion_id: string;
  emotion_name: string;
  /** Hex color from the emotions catalog. */
  color: string;
  pebbles_with_emotion: number;
  total_pebbles: number;
  /** Share of pebbles in the week with ≥1 pearl of this emotion. 0–100. May NOT sum to 100 across emotions. */
  share_pct: number;
}

/** mv_domain_share_weekly — share of pebbles linked to each Maslow domain, per week. */
export interface DomainShareRow {
  bucket_week: IsoDate;
  domain_id: string;
  domain_name: string;
  /** Maslow level (1=Physiological, 8=Transcendence) — used for sort order. */
  domain_level: number;
  pebbles_in_domain: number;
  total_pebbles: number;
  share_pct: number;
}

/** mv_cairn_participation_weekly — one row per (period, period_start). */
export interface CairnParticipationRow {
  period: CairnPeriod;
  period_start: IsoDate;
  completed: number;
  partial: number;
  missed: number;
  total: number;
  completed_pct: number;
  partial_pct: number;
  missed_pct: number;
}

/** mv_visibility_mix_daily — pebble count by visibility per day. */
export interface VisibilityMixRow {
  bucket_date: IsoDate;
  visibility: Visibility;
  pebbles: number;
}

/** mv_quality_signals_daily — daily snapshot of habit-health metrics. */
export interface QualitySignalsRow {
  bucket_date: IsoDate;
  median_session_seconds: number | null;
  sessions_per_wau: number | null;
  pebbles_per_wau: number | null;
  pct_revisits_to_past_pebbles: number | null;
  d1_retention: number | null;
  d7_retention: number | null;
  d30_retention: number | null;
  friction_events_per_session: number | null;
}

// ---------------------------------------------------------------------------
// View name ↔ row type registry
// ---------------------------------------------------------------------------

/** Map of MV name → row type. Useful for typing generic fetchers. */
export interface ViewRow {
  mv_kpi_daily:                  KpiDailyRow;
  mv_active_users_daily:         ActiveUsersDailyRow;
  mv_retention_cohorts_weekly:   RetentionCohortRow;
  mv_pebble_volume_daily:        PebbleVolumeDailyRow;
  mv_pebble_enrichment_daily:    PebbleEnrichmentDailyRow;
  mv_user_averages_weekly:       UserAveragesWeeklyRow;
  mv_bounce_distribution_daily:  BounceDistributionRow;
  mv_emotion_share_weekly:       EmotionShareRow;
  mv_domain_share_weekly:        DomainShareRow;
  mv_cairn_participation_weekly: CairnParticipationRow;
  mv_visibility_mix_daily:       VisibilityMixRow;
  mv_quality_signals_daily:      QualitySignalsRow;
}

export type ViewName = keyof ViewRow;

// ---------------------------------------------------------------------------
// Page-level types
// ---------------------------------------------------------------------------

/** Time range tab on the analytics page. Applies globally. */
export type TimeRange = "7d" | "30d" | "90d" | "1y" | "all";

/** Period bucket for the Pebbles volume chart. */
export type VolumeBucket = "day" | "week" | "month" | "year";

/** Toggle inside Active users over time. */
export type ActivityMetric = "dau" | "wau" | "mau" | "all";

/** Snapshot vs over-time toggle inside Emotions card. */
export type EmotionsView = "snapshot" | "over_time";

/** Returns the inclusive date range for a TimeRange tab, ending today (UTC). */
export function dateRangeFor(range: TimeRange, today: Date = new Date()): { start: IsoDate; end: IsoDate } {
  const end = today.toISOString().slice(0, 10);
  if (range === "all") return { start: "1970-01-01", end };
  const days = { "7d": 7, "30d": 30, "90d": 90, "1y": 365 }[range];
  const startDate = new Date(today);
  startDate.setUTCDate(startDate.getUTCDate() - days + 1);
  return { start: startDate.toISOString().slice(0, 10), end };
}
