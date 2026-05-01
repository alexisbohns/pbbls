"use client"

import { useMemo, useState } from "react"
import { Bar, CartesianGrid, ComposedChart, Line, XAxis, YAxis } from "recharts"
import {
  ChartContainer,
  ChartLegend,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart"
import { Button } from "@/components/ui/button"
import {
  VOLUME_BUCKETS,
  VOLUME_BUCKET_LABELS,
  type VolumeBucket,
} from "@/lib/analytics/types"

/** PebbleVolumeChart receives daily rows with non-null bucket_date (filtered upstream). */
export type PebbleVolumeChartDatum = {
  bucket_date: string
  pebbles: number
  pebbles_with_picture: number
  pebbles_in_collection: number
}

type PebbleVolumeChartProps = {
  data: PebbleVolumeChartDatum[]
  initialBucket?: VolumeBucket
}

const config: ChartConfig = {
  pebbles: { label: "Pebbles", color: "var(--chart-1)" },
  pebbles_with_picture: { label: "With picture", color: "var(--chart-2)" },
  pebbles_in_collection: { label: "In collection", color: "var(--chart-3)" },
}

/** Truncate an ISO date string to the start of the requested bucket (UTC). */
function bucketStart(iso: string, bucket: VolumeBucket): string {
  const d = new Date(`${iso}T00:00:00Z`)
  switch (bucket) {
    case "day":
      return iso
    case "week": {
      // Monday (UTC). getUTCDay(): 0 = Sun, 1 = Mon, …
      const dow = d.getUTCDay()
      const offset = (dow + 6) % 7
      d.setUTCDate(d.getUTCDate() - offset)
      return d.toISOString().slice(0, 10)
    }
    case "month":
      return `${iso.slice(0, 7)}-01`
    case "year":
      return `${iso.slice(0, 4)}-01-01`
  }
}

function aggregate(
  data: PebbleVolumeChartDatum[],
  bucket: VolumeBucket,
): PebbleVolumeChartDatum[] {
  if (bucket === "day") return data
  const byKey = new Map<string, PebbleVolumeChartDatum>()
  for (const row of data) {
    const key = bucketStart(row.bucket_date, bucket)
    const acc = byKey.get(key)
    if (acc) {
      acc.pebbles += row.pebbles
      acc.pebbles_with_picture += row.pebbles_with_picture
      acc.pebbles_in_collection += row.pebbles_in_collection
    } else {
      byKey.set(key, {
        bucket_date: key,
        pebbles: row.pebbles,
        pebbles_with_picture: row.pebbles_with_picture,
        pebbles_in_collection: row.pebbles_in_collection,
      })
    }
  }
  return Array.from(byKey.values()).sort((a, b) =>
    a.bucket_date < b.bucket_date ? -1 : 1,
  )
}

function formatTick(iso: string, bucket: VolumeBucket): string {
  const d = new Date(`${iso}T00:00:00Z`)
  if (bucket === "year") return iso.slice(0, 4)
  if (bucket === "month") {
    return d.toLocaleDateString("en-US", {
      month: "short",
      year: "2-digit",
      timeZone: "UTC",
    })
  }
  // day / week — month-day
  return iso.slice(5)
}

export function PebbleVolumeChart({
  data,
  initialBucket = "day",
}: PebbleVolumeChartProps) {
  const [bucket, setBucket] = useState<VolumeBucket>(initialBucket)
  const series = useMemo(() => aggregate(data, bucket), [data, bucket])

  return (
    <div>
      <div
        className="mb-3 flex items-center gap-1"
        role="tablist"
        aria-label="Bucket"
      >
        {VOLUME_BUCKETS.map((b) => (
          <Button
            key={b}
            variant={b === bucket ? "default" : "ghost"}
            size="sm"
            role="tab"
            aria-selected={b === bucket}
            onClick={() => setBucket(b)}
          >
            {VOLUME_BUCKET_LABELS[b]}
          </Button>
        ))}
      </div>

      {series.length === 0 ? (
        <div
          className="flex h-72 items-center justify-center text-sm text-muted-foreground"
          aria-live="polite"
        >
          No pebbles in this range
        </div>
      ) : (
        <ChartContainer config={config} className="h-72 w-full">
          <ComposedChart
            data={series}
            margin={{ left: 4, right: 12, top: 8, bottom: 4 }}
          >
            <CartesianGrid vertical={false} strokeDasharray="3 3" />
            <XAxis
              dataKey="bucket_date"
              tickLine={false}
              axisLine={false}
              tickMargin={8}
              minTickGap={32}
              tickFormatter={(v: string) => formatTick(v, bucket)}
            />
            <YAxis tickLine={false} axisLine={false} width={36} />
            <ChartTooltip content={<ChartTooltipContent />} />
            <ChartLegend content={<ChartLegendContent />} />
            <Bar
              dataKey="pebbles"
              fill="var(--color-pebbles)"
              radius={[2, 2, 0, 0]}
            />
            <Line
              dataKey="pebbles_with_picture"
              type="monotone"
              stroke="var(--color-pebbles_with_picture)"
              strokeWidth={2}
              dot={false}
            />
            <Line
              dataKey="pebbles_in_collection"
              type="monotone"
              stroke="var(--color-pebbles_in_collection)"
              strokeWidth={2}
              dot={false}
            />
          </ComposedChart>
        </ChartContainer>
      )}
    </div>
  )
}
