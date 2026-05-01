import { ArrowDown, ArrowUp, Minus } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import { cn } from "@/lib/utils"
import {
  UserAveragesChart,
  type UserAveragesChartDatum,
} from "./UserAveragesChart"

type UserAveragesProps = {
  data: UserAveragesChartDatum[]
}

type MetricKey = "avg_glyphs" | "avg_souls" | "avg_collections"

const METRIC_LABELS: Record<MetricKey, string> = {
  avg_glyphs: "Avg glyphs / user",
  avg_souls: "Avg souls / user",
  avg_collections: "Avg collections / user",
}

const METRIC_NOUN: Record<MetricKey, string> = {
  avg_glyphs: "glyphs",
  avg_souls: "souls",
  avg_collections: "collections",
}

export function UserAverages({ data }: UserAveragesProps) {
  if (data.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        No active users in the last 12 weeks — averages appear once users start
        collecting.
      </p>
    )
  }

  const last = data[data.length - 1]
  const prev = data.length >= 2 ? data[data.length - 2] : null

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        {(Object.keys(METRIC_LABELS) as MetricKey[]).map((key) => (
          <Stat
            key={key}
            label={METRIC_LABELS[key]}
            value={last[key]}
            previous={prev ? prev[key] : null}
            description={describe(key, last[key], last.active_users)}
          />
        ))}
      </div>
      <UserAveragesChart data={data} />
    </div>
  )
}

function describe(
  key: MetricKey,
  value: number,
  activeUsers: number,
): string {
  const noun = METRIC_NOUN[key]
  const userPhrase =
    activeUsers === 1 ? "the 1 active user" : `the ${activeUsers} active users`
  return `Last week, ${userPhrase} owned an average of ${value.toFixed(2)} ${noun} each.`
}

function Stat({
  label,
  value,
  previous,
  description,
}: {
  label: string
  value: number
  previous: number | null
  description: string
}) {
  const delta = previous === null ? null : round2(value - previous)
  const direction: "up" | "down" | "flat" =
    delta === null || delta === 0 ? "flat" : delta > 0 ? "up" : "down"

  return (
    <div className="space-y-1">
      <div className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {label}
      </div>
      <div className="flex items-baseline gap-2">
        <Tooltip>
          <TooltipTrigger
            render={
              <span
                className="cursor-help text-2xl font-semibold tabular-nums underline decoration-dotted decoration-muted-foreground/50 underline-offset-4"
                tabIndex={0}
              >
                {value.toFixed(2)}
              </span>
            }
          />
          <TooltipContent>{description}</TooltipContent>
        </Tooltip>
        {delta !== null ? <DeltaBadge delta={delta} direction={direction} /> : null}
      </div>
    </div>
  )
}

function DeltaBadge({
  delta,
  direction,
}: {
  delta: number
  direction: "up" | "down" | "flat"
}) {
  const Icon = direction === "up" ? ArrowUp : direction === "down" ? ArrowDown : Minus
  const sign = delta > 0 ? "+" : ""
  return (
    <Badge
      variant="secondary"
      className={cn(
        "gap-1",
        direction === "up" && "text-emerald-700 dark:text-emerald-400",
        direction === "down" && "text-rose-700 dark:text-rose-400",
      )}
    >
      <Icon className="size-3" aria-hidden />
      <span>
        {sign}
        {delta.toFixed(2)}
      </span>
    </Badge>
  )
}

function round2(n: number): number {
  return Math.round(n * 100) / 100
}
