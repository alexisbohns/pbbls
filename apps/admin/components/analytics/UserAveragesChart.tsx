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

export type UserAveragesChartDatum = {
  bucket_week: string
  active_users: number
  avg_glyphs: number
  avg_souls: number
  avg_collections: number
}

export type UserAveragesMetric = "all" | "avg_glyphs" | "avg_souls" | "avg_collections"

const METRICS: UserAveragesMetric[] = ["all", "avg_glyphs", "avg_souls", "avg_collections"]
const METRIC_LABELS: Record<UserAveragesMetric, string> = {
  all: "All",
  avg_glyphs: "Glyphs",
  avg_souls: "Souls",
  avg_collections: "Collections",
}

const config: ChartConfig = {
  avg_glyphs: { label: "Avg glyphs / user", color: "var(--chart-1)" },
  avg_souls: { label: "Avg souls / user", color: "var(--chart-2)" },
  avg_collections: { label: "Avg collections / user", color: "var(--chart-3)" },
}

type UserAveragesChartProps = {
  data: UserAveragesChartDatum[]
  initialMetric?: UserAveragesMetric
}

export function UserAveragesChart({
  data,
  initialMetric = "all",
}: UserAveragesChartProps) {
  const [metric, setMetric] = useState<UserAveragesMetric>(initialMetric)

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

  const showGlyphs = metric === "all" || metric === "avg_glyphs"
  const showSouls = metric === "all" || metric === "avg_souls"
  const showCollections = metric === "all" || metric === "avg_collections"

  const last = data[data.length - 1]
  const ariaSummary = `Per-user averages for week of ${last.bucket_week}: glyphs ${last.avg_glyphs}, souls ${last.avg_souls}, collections ${last.avg_collections}.`

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
        <LineChart data={data} margin={{ left: 4, right: 12, top: 8, bottom: 4 }}>
          <CartesianGrid vertical={false} strokeDasharray="3 3" />
          <XAxis
            dataKey="bucket_week"
            tickLine={false}
            axisLine={false}
            tickMargin={8}
            minTickGap={32}
            tickFormatter={(v: string) => v.slice(5)}
          />
          <YAxis tickLine={false} axisLine={false} width={36} />
          <ChartTooltip content={<ChartTooltipContent />} />
          <ChartLegend content={<ChartLegendContent />} />
          {showGlyphs ? (
            <Line
              dataKey="avg_glyphs"
              type="monotone"
              stroke="var(--color-avg_glyphs)"
              strokeWidth={2}
              dot={false}
            />
          ) : null}
          {showSouls ? (
            <Line
              dataKey="avg_souls"
              type="monotone"
              stroke="var(--color-avg_souls)"
              strokeWidth={2}
              dot={false}
            />
          ) : null}
          {showCollections ? (
            <Line
              dataKey="avg_collections"
              type="monotone"
              stroke="var(--color-avg_collections)"
              strokeWidth={2}
              dot={false}
            />
          ) : null}
        </LineChart>
      </ChartContainer>
    </div>
  )
}
