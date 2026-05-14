"use client"

import { ChevronLeft, ChevronRight } from "lucide-react"
import { useLocale, useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import {
  formatWeekRange,
  weekIndex,
  type WeekRollEntry,
} from "@/lib/utils/week-roll-entries"

type WeekHeaderProps = {
  entries: WeekRollEntry[]
  focused: Date
  today: Date
  onPrev: () => void
  onNext: () => void
}

export function WeekHeader({ entries, focused, today, onPrev, onNext }: WeekHeaderProps) {
  const locale = useLocale()
  const t = useTranslations("path")
  const idx = weekIndex(entries, focused)
  const atStart = idx <= 0
  const atEnd = idx >= entries.length - 1

  return (
    <div className="flex h-10 items-center justify-between rounded-xl border border-muted px-2 dark:border-foreground">
      <Button
        variant="ghost"
        size="icon"
        disabled={atStart}
        onClick={onPrev}
        aria-label={t("weekHeader.previous")}
      >
        <ChevronLeft className="size-5 text-primary" />
      </Button>
      <span className="font-heading text-[17px] font-semibold uppercase tracking-[0.02em] text-muted-foreground dark:text-muted">
        {formatWeekRange(focused, today, locale)}
      </span>
      <Button
        variant="ghost"
        size="icon"
        disabled={atEnd}
        onClick={onNext}
        aria-label={t("weekHeader.next")}
      >
        <ChevronRight className="size-5 text-primary" />
      </Button>
    </div>
  )
}
