import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import {
  BounceDistributionChart,
  type BounceDistributionDatum,
} from "./BounceDistributionChart"

export type BounceDistributionStats = {
  /** Median current bounce score across all users. Null when zero users. */
  medianScore: number | null
  /** % of users whose current bounce >= bounce 7 days ago. 0–100, null when no users. */
  pctMaintaining: number | null
  /** Avg distinct active days/week across MAU. 0–7, null when no MAU. */
  avgActiveDaysPerWeek: number | null
}

export type BounceDistributionProps = {
  buckets: BounceDistributionDatum[]
  totalUsers: number
  stats: BounceDistributionStats
}

export function BounceDistribution({
  buckets,
  totalUsers,
  stats,
}: BounceDistributionProps) {
  if (totalUsers === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        No users yet — bounce karma distribution appears once users earn karma.
      </p>
    )
  }

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Stat
          label="Median bounce"
          value={formatNumber(stats.medianScore)}
          description={describeMedian(stats.medianScore, totalUsers)}
        />
        <Stat
          label="% maintaining"
          value={formatPct(stats.pctMaintaining)}
          description={describeMaintaining(stats.pctMaintaining, totalUsers)}
        />
        <Stat
          label="Avg active days / week"
          value={formatNumber(stats.avgActiveDaysPerWeek)}
          description={describeActiveDays(stats.avgActiveDaysPerWeek)}
        />
      </div>

      <BounceDistributionChart data={buckets} />
    </div>
  )
}

function Stat({
  label,
  value,
  description,
}: {
  label: string
  value: string
  description: string
}) {
  return (
    <div className="space-y-1">
      <div className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {label}
      </div>
      <Tooltip>
        <TooltipTrigger
          render={
            <span
              className="cursor-help text-2xl font-semibold tabular-nums underline decoration-dotted decoration-muted-foreground/50 underline-offset-4"
              tabIndex={0}
            >
              {value}
            </span>
          }
        />
        <TooltipContent>{description}</TooltipContent>
      </Tooltip>
    </div>
  )
}

function formatNumber(n: number | null): string {
  if (n === null) return "—"
  return Math.round(n * 100) / 100 % 1 === 0 ? n.toFixed(0) : n.toFixed(2)
}

function formatPct(n: number | null): string {
  if (n === null) return "—"
  return `${n.toFixed(1)}%`
}

function describeMedian(n: number | null, totalUsers: number): string {
  if (n === null) return "Median needs at least one user with a bounce score."
  const userPhrase = totalUsers === 1 ? "the 1 user" : `the ${totalUsers} users`
  return `Today, ${userPhrase} sit at a median bounce of ${formatNumber(n)} karma.`
}

function describeMaintaining(n: number | null, totalUsers: number): string {
  if (n === null) return "% maintaining needs at least one user with a bounce score."
  const userPhrase = totalUsers === 1 ? "the 1 user" : `the ${totalUsers} users`
  return `${n.toFixed(1)}% of ${userPhrase} have a current bounce at or above where it was 7 days ago.`
}

function describeActiveDays(n: number | null): string {
  if (n === null) return "Avg active days needs at least one MAU."
  return `Across the last 30 days' active users (MAU), each one logged on average ${formatNumber(n)} distinct days with a pebble in the last 7 days.`
}
