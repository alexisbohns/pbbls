import type { QualitySignalRow } from "@/lib/analytics/types"

const TODAY = "2026-05-01"

const PLACEHOLDERS: Pick<
  QualitySignalRow,
  "indicator_order" | "indicator_key" | "indicator_label" | "unit"
>[] = [
  { indicator_order: 1, indicator_key: "median_session_seconds",      indicator_label: "Median session duration",         unit: "seconds" },
  { indicator_order: 2, indicator_key: "sessions_per_wau",            indicator_label: "Sessions per active user / week", unit: "sessions" },
  { indicator_order: 4, indicator_key: "pct_revisits_to_past_pebbles",indicator_label: "% revisits to past pebbles",       unit: "percent" },
  { indicator_order: 8, indicator_key: "friction_events_per_session", indicator_label: "Friction events / session",        unit: "events" },
]

function placeholder(order: number): QualitySignalRow {
  const p = PLACEHOLDERS.find((x) => x.indicator_order === order)
  if (!p) throw new Error(`No placeholder for order ${order}`)
  return {
    bucket_date: TODAY,
    indicator_order: p.indicator_order,
    indicator_key: p.indicator_key,
    indicator_label: p.indicator_label,
    unit: p.unit,
    value: null,
    previous_value: null,
    available: false,
  }
}

export const denseQualitySignalsFixture: QualitySignalRow[] = [
  placeholder(1),
  placeholder(2),
  {
    bucket_date: TODAY,
    indicator_order: 3,
    indicator_key: "pebbles_per_wau",
    indicator_label: "Pebbles per active user / week",
    unit: "pebbles",
    value: 8.42,
    previous_value: 7.96,
    available: true,
  },
  placeholder(4),
  {
    bucket_date: TODAY,
    indicator_order: 5,
    indicator_key: "d1_retention",
    indicator_label: "D1 retention",
    unit: "percent",
    value: 42.9,
    previous_value: 38.1,
    available: true,
  },
  {
    bucket_date: TODAY,
    indicator_order: 6,
    indicator_key: "d7_retention",
    indicator_label: "D7 retention",
    unit: "percent",
    value: 27.3,
    previous_value: 31.0,
    available: true,
  },
  {
    bucket_date: TODAY,
    indicator_order: 7,
    indicator_key: "d30_retention",
    indicator_label: "D30 retention",
    unit: "percent",
    value: 14.0,
    previous_value: 14.0,
    available: true,
  },
  placeholder(8),
]

export const sparseQualitySignalsFixture: QualitySignalRow[] = [
  placeholder(1),
  placeholder(2),
  {
    bucket_date: TODAY,
    indicator_order: 3,
    indicator_key: "pebbles_per_wau",
    indicator_label: "Pebbles per active user / week",
    unit: "pebbles",
    value: 1.5,
    previous_value: null,
    available: true,
  },
  placeholder(4),
  {
    bucket_date: TODAY,
    indicator_order: 5,
    indicator_key: "d1_retention",
    indicator_label: "D1 retention",
    unit: "percent",
    value: null,
    previous_value: null,
    available: true,
  },
  {
    bucket_date: TODAY,
    indicator_order: 6,
    indicator_key: "d7_retention",
    indicator_label: "D7 retention",
    unit: "percent",
    value: null,
    previous_value: null,
    available: true,
  },
  {
    bucket_date: TODAY,
    indicator_order: 7,
    indicator_key: "d30_retention",
    indicator_label: "D30 retention",
    unit: "percent",
    value: null,
    previous_value: null,
    available: true,
  },
  placeholder(8),
]

export const emptyQualitySignalsFixture: QualitySignalRow[] = []
