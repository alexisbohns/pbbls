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
          // Completed moments only (PD-W12).
          count: entry.pebbleCount,
        })}
        className={cn(
          "flex w-12 shrink-0 cursor-pointer flex-col items-center py-2.5 font-spine-sc text-[13px] font-semibold tracking-[.12em] [font-variant-numeric:lining-nums_tabular-nums]",
          "transition-colors duration-200 motion-reduce:transition-none",
          isFocused
            ? "text-(--spine-ink) underline decoration-1 underline-offset-[6px]"
            : "text-(--spine-ink-3) hover:text-(--spine-ink-2)",
        )}
      >
        <span>{entry.isoWeek}</span>
      </button>
    </li>
  )
}
