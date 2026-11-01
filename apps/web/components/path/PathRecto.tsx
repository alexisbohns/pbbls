"use client"

import { useTranslations } from "next-intl"
import { WeekPager } from "@/components/path/WeekPager"
import { WeekRoll } from "@/components/path/WeekRoll"
import { usePathFocus, usePathReading } from "@/lib/hooks/usePathFocus"
import { isoWeekNumber } from "@/lib/utils/week-roll-entries"

/**
 * The Path's recto drawn on its own (#1384, two-area spec D3, D4): the week
 * roll over the pager of lines, each week's stones, cards and mouth. It is the
 * right area's default on wide windows, beside whatever tab is on the left,
 * and publishes the day it is reading for a verso drawn elsewhere. A stone
 * opens its pebble over the right area (two-area spec D5). Needs
 * `StoneSprite` once on the page.
 */
export function PathRecto() {
  const t = useTranslations("path")
  const focus = usePathFocus()
  const { setReadingDay } = usePathReading()

  return (
    <div className="spine-paper relative flex h-full w-full flex-col overflow-x-hidden text-(--spine-ink)">
      <WeekRoll entries={focus.entries} focused={focus.focusedWeekStart} onFocus={focus.focusOn} className="shrink-0" />
      <p aria-live="polite" className="sr-only">
        {t("weekLabel", { week: focus.focusedEntry?.isoWeek ?? isoWeekNumber(focus.focusedWeekStart) })}
      </p>
      <div className="min-h-0 flex-1">
        <WeekPager
          only="recto"
          entries={focus.entries}
          focused={focus.focusedWeekStart}
          today={focus.today}
          souls={focus.souls}
          onFocusChange={focus.focusOn}
          onOpen={focus.openMoment}
          onReadingDay={setReadingDay}
        />
      </div>
    </div>
  )
}
