"use client"

import { Bar, BarChart, CartesianGrid, LabelList, XAxis, YAxis } from "recharts"
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart"

export type BounceDistributionDatum = {
  bucket_label: string
  users: number
}

const config: ChartConfig = {
  users: { label: "Users", color: "var(--chart-1)" },
}

type Props = {
  data: BounceDistributionDatum[]
}

export function BounceDistributionChart({ data }: Props) {
  const total = data.reduce((acc, r) => acc + r.users, 0)
  if (total === 0) {
    return (
      <div
        className="flex h-56 items-center justify-center text-sm text-muted-foreground"
        aria-live="polite"
      >
        No users yet — bounce karma appears once users start collecting.
      </div>
    )
  }

  return (
    <ChartContainer config={config} className="h-56 w-full">
      <BarChart data={data} margin={{ left: 4, right: 12, top: 16, bottom: 4 }}>
        <CartesianGrid vertical={false} strokeDasharray="3 3" />
        <XAxis
          dataKey="bucket_label"
          tickLine={false}
          axisLine={false}
          tickMargin={8}
        />
        <YAxis tickLine={false} axisLine={false} width={32} allowDecimals={false} />
        <ChartTooltip content={<ChartTooltipContent />} />
        <Bar
          dataKey="users"
          fill="var(--color-users)"
          radius={[2, 2, 0, 0]}
        >
          <LabelList
            dataKey="users"
            position="top"
            className="fill-foreground text-xs tabular-nums"
          />
        </Bar>
      </BarChart>
    </ChartContainer>
  )
}
