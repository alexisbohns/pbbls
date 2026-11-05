import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import type { OnboardingAnswerRow } from "@/lib/analytics/types"

const REASON_LABELS: Record<string, string> = {
  "notice-feelings": "Notice what I feel",
  "keep-people": "Keep the people",
  "hard-season": "Get through a hard season",
  "good-days": "Keep the good days",
  "own-patterns": "See my own patterns",
  "keep-record": "Keep a record for later",
}
const PACE_LABELS: Record<string, string> = {
  daily: "Every day",
  "few-a-week": "A few times a week",
  "when-it-matters": "Only when it matters",
  "weekly-wrap": "Wrapping the end of the week",
}

function Bars({
  title,
  rows,
  labels,
  completed,
}: {
  title: string
  rows: OnboardingAnswerRow[]
  labels: Record<string, string>
  completed: number
}) {
  const max = Math.max(1, ...rows.map((r) => r.users))
  if (rows.length === 0) return <p className="text-muted-foreground text-sm">{title}: no answer yet.</p>
  return (
    <div className="space-y-2">
      <h3 className="text-sm font-medium">{title}</h3>
      <ul className="space-y-1">
        {[...rows].sort((a, b) => b.users - a.users).map((r) => {
          const label = labels[r.value] ?? r.value
          return (
            <li key={r.value} className="space-y-1 text-sm">
              <div className="flex justify-between gap-3">
                <span>{label}</span>
                <Tooltip>
                  <TooltipTrigger
                    render={
                      <span
                        className="cursor-help tabular-nums underline decoration-dotted decoration-muted-foreground/50 underline-offset-4"
                        tabIndex={0}
                      >
                        {r.users}
                      </span>
                    }
                  />
                  <TooltipContent>
                    {r.users} of the {completed} accounts that completed the onboarding picked &ldquo;{label}&rdquo;.
                  </TooltipContent>
                </Tooltip>
              </div>
              <div className="bg-muted h-1.5 rounded">
                <div className="bg-primary h-1.5 rounded" style={{ width: `${(100 * r.users) / max}%` }} />
              </div>
            </li>
          )
        })}
      </ul>
    </div>
  )
}

export function OnboardingAnswers({ rows }: { rows: OnboardingAnswerRow[] }) {
  const completed = rows.find((r) => r.kind === "completed" && r.value === "yes")?.users ?? 0
  const notYet = rows.find((r) => r.kind === "completed" && r.value === "no")?.users ?? 0
  return (
    <div className="space-y-6">
      <p className="text-muted-foreground text-sm">
        {completed} accounts completed the onboarding, {notYet} have not. Distribution only, never per user.
      </p>
      <Bars title="What brings you here" rows={rows.filter((r) => r.kind === "reason")} labels={REASON_LABELS} completed={completed} />
      <Bars title="Pace" rows={rows.filter((r) => r.kind === "pace")} labels={PACE_LABELS} completed={completed} />
    </div>
  )
}
