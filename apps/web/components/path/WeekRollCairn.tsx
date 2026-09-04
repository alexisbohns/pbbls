"use client"

import { useTranslations } from "next-intl"
import { cn } from "@/lib/utils"
import type { WeekRollEntry } from "@/lib/utils/week-roll-entries"

type WeekRollCairnProps = {
  entry: WeekRollEntry
  isFocused: boolean
  onClick: () => void
}

export function WeekRollCairn({ entry, isFocused, onClick }: WeekRollCairnProps) {
  const t = useTranslations("path")

  return (
    <li className="shrink-0">
      <button
        type="button"
        data-week={entry.weekStartIso}
        onClick={onClick}
        aria-pressed={isFocused}
        aria-label={t("weekHeader.weekAria", {
          iso: entry.isoWeek,
          count: entry.pebbles.length,
        })}
        className={cn(
          "flex w-[72px] shrink-0 flex-col items-center gap-1 rounded-t-lg py-2 text-sm font-semibold",
          isFocused
            ? "border-b-2 border-primary bg-surface text-primary"
            : "text-muted-foreground",
        )}
      >
        <span>{entry.isoWeek}</span>
      </button>
    </li>
  )
}
