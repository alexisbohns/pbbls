import { ArrowDown, ArrowUp, Minus } from "lucide-react"
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import { cn } from "@/lib/utils"

export type DomainShareSnapshot = {
  domain_id: string
  domain_slug: string
  domain_name: string
  domain_label: string
  domain_level: number | null
  pebbles_in_domain: number
  share_pct: number
}

export type DomainShareMover = {
  domain_id: string
  domain_name: string
  current_pct: number
  previous_pct: number
  /** Δ in percentage points. */
  delta_pp: number
}

type DomainShareProps = {
  rows: DomainShareSnapshot[]
  totalPebbles: number
  rangeLabel?: string
  /** Null when the period is "all" (no comparable prior period). */
  topMover: DomainShareMover | null
  bottomMover: DomainShareMover | null
}

const DOMAIN_BAR_COLOR = "var(--chart-1)"

export function DomainShare({
  rows,
  totalPebbles,
  rangeLabel,
  topMover,
  bottomMover,
}: DomainShareProps) {
  if (totalPebbles === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        No pebbles {rangeLabel ? `in the last ${rangeLabel.toLowerCase()}` : "yet"} —
        domain share appears once users start collecting.
      </p>
    )
  }

  return (
    <div className="space-y-5">
      <ul className="space-y-2">
        {rows.map((r) => (
          <li
            key={r.domain_id}
            className="grid grid-cols-[8rem_1fr_3.5rem] items-center gap-3"
          >
            <span className="truncate text-sm">
              {r.domain_name}
              <span className="ml-1 text-xs text-muted-foreground/70">
                {r.domain_label}
              </span>
            </span>
            <span className="block h-2 overflow-hidden rounded-full bg-muted">
              <span
                className="block h-full rounded-full"
                style={{
                  width: `${Math.max(0, Math.min(100, r.share_pct))}%`,
                  backgroundColor: DOMAIN_BAR_COLOR,
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

      <div className="grid grid-cols-2 gap-3 border-t pt-4">
        <MoverStat
          label="Most-evolving"
          mover={topMover}
          fallback="No prior period"
        />
        <MoverStat
          label="Most-decreasing"
          mover={bottomMover}
          fallback="No prior period"
        />
      </div>

      <p className="text-xs text-muted-foreground">
        Based on {totalPebbles.toLocaleString()} pebble
        {totalPebbles === 1 ? "" : "s"}
        {rangeLabel ? ` in the last ${rangeLabel.toLowerCase()}` : ""}.
        Pebbles can be linked to multiple domains, so shares may exceed 100%.
      </p>
    </div>
  )
}

function MoverStat({
  label,
  mover,
  fallback,
}: {
  label: string
  mover: DomainShareMover | null
  fallback: string
}) {
  if (!mover) {
    return (
      <div className="space-y-1">
        <div className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
          {label}
        </div>
        <div className="text-sm text-muted-foreground">{fallback}</div>
      </div>
    )
  }

  const direction: "up" | "down" | "flat" =
    mover.delta_pp === 0 ? "flat" : mover.delta_pp > 0 ? "up" : "down"
  const Icon = direction === "up" ? ArrowUp : direction === "down" ? ArrowDown : Minus
  const sign = mover.delta_pp > 0 ? "+" : ""
  const description = `${mover.domain_name} moved from ${mover.previous_pct.toFixed(
    1,
  )}% to ${mover.current_pct.toFixed(1)}% vs. the previous period (${sign}${mover.delta_pp.toFixed(
    1,
  )} pp).`

  return (
    <div className="space-y-1">
      <div className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {label}
      </div>
      <Tooltip>
        <TooltipTrigger
          render={
            <div
              className="cursor-help space-y-1"
              tabIndex={0}
            >
              <div className="truncate text-sm font-medium">
                {mover.domain_name}
              </div>
              <div
                className={cn(
                  "flex items-center gap-1 text-sm font-medium tabular-nums underline decoration-dotted decoration-muted-foreground/50 underline-offset-4",
                  direction === "up" && "text-emerald-700 dark:text-emerald-400",
                  direction === "down" && "text-rose-700 dark:text-rose-400",
                )}
              >
                <Icon className="size-3" aria-hidden />
                <span>
                  {sign}
                  {mover.delta_pp.toFixed(1)} pp
                </span>
              </div>
            </div>
          }
        />
        <TooltipContent>{description}</TooltipContent>
      </Tooltip>
    </div>
  )
}
