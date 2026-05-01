import { ArrowDown, ArrowUp, Minus } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import { cn } from "@/lib/utils"
import type { QualitySignalRow, QualitySignalUnit } from "@/lib/analytics/types"

export type QualitySignalsTableProps = {
  rows: QualitySignalRow[]
}

export function QualitySignalsTable({ rows }: QualitySignalsTableProps) {
  if (rows.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        No data yet — quality signals appear once users start collecting.
      </p>
    )
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Indicator</TableHead>
          <TableHead className="text-right">Value</TableHead>
          <TableHead className="text-right">Δ vs prior period</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row) => (
          <Row key={row.indicator_key ?? row.indicator_order} row={row} />
        ))}
      </TableBody>
    </Table>
  )
}

function Row({ row }: { row: QualitySignalRow }) {
  const available = row.available === true
  const unit = (row.unit ?? "pebbles") as QualitySignalUnit
  const label = row.indicator_label ?? row.indicator_key ?? "—"

  const valueDisplay = available ? formatValue(row.value, unit) : "—"
  const description = available
    ? describe(label, row.value, row.previous_value, unit)
    : "Data not collected yet — this metric becomes available in Phase B/C."

  return (
    <TableRow className={cn(!available && "opacity-60")}>
      <TableCell className="font-medium">{label}</TableCell>
      <TableCell className="text-right">
        <Tooltip>
          <TooltipTrigger
            render={
              <span
                className="cursor-help tabular-nums underline decoration-dotted decoration-muted-foreground/50 underline-offset-4"
                tabIndex={0}
              >
                {valueDisplay}
              </span>
            }
          />
          <TooltipContent>{description}</TooltipContent>
        </Tooltip>
      </TableCell>
      <TableCell className="text-right">
        {available ? (
          <DeltaCell value={row.value} previous={row.previous_value} unit={unit} />
        ) : (
          <span className="text-muted-foreground">—</span>
        )}
      </TableCell>
    </TableRow>
  )
}

function DeltaCell({
  value,
  previous,
  unit,
}: {
  value: number | null
  previous: number | null
  unit: QualitySignalUnit
}) {
  if (value === null || previous === null) {
    return <span className="text-muted-foreground">—</span>
  }
  const delta = round2(value - previous)
  const direction: "up" | "down" | "flat" =
    delta === 0 ? "flat" : delta > 0 ? "up" : "down"
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
      <span className="tabular-nums">
        {sign}
        {formatDelta(delta, unit)}
      </span>
    </Badge>
  )
}

function formatValue(value: number | null, unit: QualitySignalUnit): string {
  if (value === null) return "—"
  switch (unit) {
    case "percent":
      return `${value.toFixed(1)}%`
    case "seconds":
      return formatSeconds(value)
    default:
      return value.toFixed(2)
  }
}

function formatDelta(delta: number, unit: QualitySignalUnit): string {
  switch (unit) {
    case "percent":
      return `${delta.toFixed(1)}pp`
    case "seconds":
      return `${Math.round(delta)}s`
    default:
      return delta.toFixed(2)
  }
}

function formatSeconds(seconds: number): string {
  if (seconds < 60) return `${Math.round(seconds)}s`
  const m = Math.floor(seconds / 60)
  const s = Math.round(seconds - m * 60)
  return s === 0 ? `${m}m` : `${m}m ${s}s`
}

function describe(
  label: string,
  value: number | null,
  previous: number | null,
  unit: QualitySignalUnit,
): string {
  if (value === null) {
    return `${label} has no value yet — not enough data in the current window.`
  }
  const current = formatValue(value, unit)
  if (previous === null) {
    return `${label} sits at ${current} today; no comparable prior-period value yet.`
  }
  const prior = formatValue(previous, unit)
  return `${label} sits at ${current} today, vs ${prior} in the matching prior period.`
}

function round2(n: number): number {
  return Math.round(n * 100) / 100
}
