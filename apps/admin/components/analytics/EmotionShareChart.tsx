"use client"

import { useMemo, useState } from "react"
import { Area, AreaChart, CartesianGrid, XAxis, YAxis } from "recharts"
import {
  ChartContainer,
  ChartLegend,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart"
import { Button } from "@/components/ui/button"

export type EmotionShareSnapshot = {
  emotion_id: string
  emotion_slug: string
  emotion_name: string
  color: string
  pebbles_with_emotion: number
  total_pebbles: number
  share_pct: number
}

export type EmotionShareWeeklyDatum = {
  bucket_week: string
  /** share_pct per emotion slug, normalized to sum to 100% within the week. */
  shares: Record<string, number>
}

export type EmotionShareView = "snapshot" | "over_time"

type EmotionShareChartProps = {
  snapshot: EmotionShareSnapshot[]
  weekly: EmotionShareWeeklyDatum[]
  /** Slug → display name + color, in stable rank order for the legend. */
  catalog: { slug: string; name: string; color: string }[]
  totalPebbles: number
  rangeLabel?: string
  initialView?: EmotionShareView
}

const VIEWS: { key: EmotionShareView; label: string }[] = [
  { key: "snapshot", label: "Snapshot" },
  { key: "over_time", label: "Over time (12w)" },
]

export function EmotionShareChart({
  snapshot,
  weekly,
  catalog,
  totalPebbles,
  rangeLabel,
  initialView = "snapshot",
}: EmotionShareChartProps) {
  const [view, setView] = useState<EmotionShareView>(initialView)

  const config = useMemo<ChartConfig>(() => {
    const out: ChartConfig = {}
    for (const c of catalog) {
      out[c.slug] = { label: c.name, color: c.color }
    }
    return out
  }, [catalog])

  // Recharts data shape: one object per week with keyed numeric values.
  const weeklyRows = useMemo(
    () =>
      weekly.map((w) => ({
        bucket_week: w.bucket_week,
        ...w.shares,
      })),
    [weekly],
  )

  const isEmpty =
    (view === "snapshot" && snapshot.length === 0) ||
    (view === "over_time" && weekly.length === 0)

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-1" role="tablist" aria-label="View">
        {VIEWS.map((v) => (
          <Button
            key={v.key}
            variant={v.key === view ? "default" : "ghost"}
            size="sm"
            role="tab"
            aria-selected={v.key === view}
            onClick={() => setView(v.key)}
          >
            {v.label}
          </Button>
        ))}
      </div>

      {isEmpty ? (
        <div
          className="flex h-72 items-center justify-center text-sm text-muted-foreground"
          aria-live="polite"
        >
          No pebbles in this range
        </div>
      ) : view === "snapshot" ? (
        <SnapshotBars
          rows={snapshot}
          totalPebbles={totalPebbles}
          rangeLabel={rangeLabel}
        />
      ) : (
        <ChartContainer config={config} className="h-72 w-full">
          <AreaChart
            data={weeklyRows}
            stackOffset="expand"
            margin={{ left: 4, right: 12, top: 8, bottom: 4 }}
          >
            <CartesianGrid vertical={false} strokeDasharray="3 3" />
            <XAxis
              dataKey="bucket_week"
              tickLine={false}
              axisLine={false}
              tickMargin={8}
              minTickGap={32}
              tickFormatter={(v: string) => v.slice(5)}
            />
            <YAxis
              tickLine={false}
              axisLine={false}
              width={36}
              tickFormatter={(v: number) => `${Math.round(v * 100)}%`}
            />
            <ChartTooltip
              content={
                <ChartTooltipContent
                  formatter={(value, name) => {
                    const pct = typeof value === "number" ? value : Number(value)
                    return [
                      `${pct.toFixed(1)}%`,
                      config[String(name)]?.label ?? name,
                    ]
                  }}
                />
              }
            />
            <ChartLegend content={<ChartLegendContent />} />
            {catalog.map((c) => (
              <Area
                key={c.slug}
                dataKey={c.slug}
                type="monotone"
                stackId="emotion"
                stroke={`var(--color-${c.slug})`}
                fill={`var(--color-${c.slug})`}
                fillOpacity={0.65}
                strokeWidth={1}
              />
            ))}
          </AreaChart>
        </ChartContainer>
      )}
    </div>
  )
}

function SnapshotBars({
  rows,
  totalPebbles,
  rangeLabel,
}: {
  rows: EmotionShareSnapshot[]
  totalPebbles: number
  rangeLabel?: string
}) {
  return (
    <div className="space-y-2">
      <ul className="space-y-2">
        {rows.map((r) => (
          <li key={r.emotion_id} className="grid grid-cols-[7rem_1fr_3.5rem] items-center gap-3">
            <span className="truncate text-sm">{r.emotion_name}</span>
            <span className="block h-2 overflow-hidden rounded-full bg-muted">
              <span
                className="block h-full rounded-full"
                style={{
                  width: `${Math.max(0, Math.min(100, r.share_pct))}%`,
                  backgroundColor: r.color,
                }}
                aria-hidden
              />
            </span>
            <span className="text-right text-sm font-medium tabular-nums">
              {r.share_pct.toFixed(1)}%
            </span>
          </li>
        ))}
      </ul>
      <p className="text-xs text-muted-foreground">
        Based on {totalPebbles.toLocaleString()} pebble
        {totalPebbles === 1 ? "" : "s"}
        {rangeLabel ? ` in the last ${rangeLabel.toLowerCase()}` : ""}.
      </p>
    </div>
  )
}
