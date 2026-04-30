import { ArrowDown, ArrowUp, Minus } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { cn } from "@/lib/utils"
import { Sparkline } from "./Sparkline"

export type KpiCardProps = {
  label: string
  value: number | string
  unit?: string
  delta?: { absolute: number; direction: "up" | "down" | "flat"; unit?: string }
  subLabel?: string
  sparkline?: number[]
}

export function KpiCard({
  label,
  value,
  unit,
  delta,
  subLabel,
  sparkline,
}: KpiCardProps) {
  const ariaParts = [`${label}: ${value}${unit ? ` ${unit}` : ""}`]
  if (delta) {
    const dirWord = delta.direction === "flat" ? "unchanged" : delta.direction
    ariaParts.push(
      `${dirWord} by ${Math.abs(delta.absolute)}${delta.unit ?? ""} from prior period`,
    )
  }

  return (
    <article aria-label={ariaParts.join(", ")}>
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
            {label}
          </CardTitle>
        </CardHeader>
        <CardContent className="flex items-end justify-between gap-2">
          <div className="space-y-1">
            <div className="text-2xl font-semibold tabular-nums">
              {value}
              {unit ? (
                <span className="ml-1 text-base font-normal text-muted-foreground">
                  {unit}
                </span>
              ) : null}
            </div>
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              {delta ? <DeltaBadge delta={delta} /> : null}
              {subLabel ? <span>{subLabel}</span> : null}
            </div>
          </div>
          {sparkline && sparkline.length >= 2 ? (
            <Sparkline
              values={sparkline}
              className="text-foreground/60"
              ariaLabel={`${label} trend`}
            />
          ) : null}
        </CardContent>
      </Card>
    </article>
  )
}

function DeltaBadge({ delta }: { delta: NonNullable<KpiCardProps["delta"]> }) {
  const Icon =
    delta.direction === "up" ? ArrowUp : delta.direction === "down" ? ArrowDown : Minus
  const sign = delta.absolute > 0 ? "+" : ""
  return (
    <Badge
      variant="secondary"
      className={cn(
        "gap-1",
        delta.direction === "up" && "text-emerald-700 dark:text-emerald-400",
        delta.direction === "down" && "text-rose-700 dark:text-rose-400",
      )}
    >
      <Icon className="size-3" aria-hidden />
      <span>
        {sign}
        {delta.absolute}
        {delta.unit ?? ""}
      </span>
    </Badge>
  )
}
