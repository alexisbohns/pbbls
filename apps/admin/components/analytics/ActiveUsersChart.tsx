"use client"

import { useState } from "react"
import { CartesianGrid, Line, LineChart, XAxis, YAxis } from "recharts"
import {
  ChartContainer,
  ChartLegend,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart"
import { Button } from "@/components/ui/button"
import type { ActivityMetric } from "@/lib/analytics/types"

/** ActiveUsersChart receives rows with non-null bucket_date (filtered upstream). */
export type ActiveUsersChartDatum = {
  bucket_date: string
  dau: number | null
  wau: number | null
  mau: number | null
}

type ActiveUsersChartProps = {
  data: ActiveUsersChartDatum[]
  initialMetric?: ActivityMetric
}

const METRICS: ActivityMetric[] = ["all", "dau", "wau", "mau"]
const METRIC_LABELS: Record<ActivityMetric, string> = {
  all: "All",
  dau: "DAU",
  wau: "WAU",
  mau: "MAU",
}

const config: ChartConfig = {
  dau: { label: "DAU", color: "var(--chart-1)" },
  wau: { label: "WAU", color: "var(--chart-2)" },
  mau: { label: "MAU", color: "var(--chart-3)" },
}

export function ActiveUsersChart({
  data,
  initialMetric = "all",
}: ActiveUsersChartProps) {
  const [metric, setMetric] = useState<ActivityMetric>(initialMetric)

  if (data.length === 0) {
    return (
      <div
        className="flex h-72 items-center justify-center text-sm text-muted-foreground"
        aria-live="polite"
      >
        No activity in this range
      </div>
    )
  }

  const showDau = metric === "all" || metric === "dau"
  const showWau = metric === "all" || metric === "wau"
  const showMau = metric === "all" || metric === "mau"

  // Coalesce nulls to 0 for recharts
  const series = data.map((d) => ({
    bucket_date: d.bucket_date,
    dau: d.dau ?? 0,
    wau: d.wau ?? 0,
    mau: d.mau ?? 0,
  }))

  const last = series[series.length - 1]
  const ariaSummary = `Active users on ${last.bucket_date}: DAU ${last.dau}, WAU ${last.wau}, MAU ${last.mau}.`

  return (
    <div aria-label={ariaSummary}>
      <div className="mb-3 flex items-center gap-1" role="tablist" aria-label="Metric">
        {METRICS.map((m) => (
          <Button
            key={m}
            variant={m === metric ? "default" : "ghost"}
            size="sm"
            role="tab"
            aria-selected={m === metric}
            onClick={() => setMetric(m)}
          >
            {METRIC_LABELS[m]}
          </Button>
        ))}
      </div>

      <ChartContainer config={config} className="h-72 w-full">
        <LineChart data={series} margin={{ left: 4, right: 12, top: 8, bottom: 4 }}>
          <CartesianGrid vertical={false} strokeDasharray="3 3" />
          <XAxis
            dataKey="bucket_date"
            tickLine={false}
            axisLine={false}
            tickMargin={8}
            minTickGap={32}
            tickFormatter={(v: string) => v.slice(5)}
          />
          <YAxis tickLine={false} axisLine={false} width={36} />
          <ChartTooltip content={<ChartTooltipContent />} />
          <ChartLegend content={<ChartLegendContent />} />
          {showDau ? (
            <Line dataKey="dau" type="monotone" stroke="var(--color-dau)" strokeWidth={2} dot={false} />
          ) : null}
          {showWau ? (
            <Line dataKey="wau" type="monotone" stroke="var(--color-wau)" strokeWidth={2} dot={false} />
          ) : null}
          {showMau ? (
            <Line dataKey="mau" type="monotone" stroke="var(--color-mau)" strokeWidth={2} dot={false} />
          ) : null}
        </LineChart>
      </ChartContainer>
    </div>
  )
}
