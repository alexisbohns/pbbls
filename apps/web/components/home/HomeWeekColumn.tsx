import Link from "next/link"
import { useTranslations } from "next-intl"
import type { StonePalette } from "@/lib/config/stone-tones"
import type { WeekColumn } from "@/lib/home/month-columns"
import { weekDayNumber, weekMonthAbbr, weekRange } from "@/lib/home/month-labels"
import { CairnMark } from "./CairnMark"
import { DaySeals } from "./DaySeals"

type HomeWeekColumnProps = {
  column: WeekColumn
  month: string
  locale: string
  paletteFor: (emotionId: string | null) => StonePalette | null
}

/**
 * One week of Home's month (#1384, spec D9, §Accessibility): a single link to
 * that week on the Path, named by its range and its pebbles. Everything drawn
 * inside (Monday's date, the days' sockets and seals, the cairn) is hidden
 * from assistive technology; the name says it.
 */
export function HomeWeekColumn({ column, month, locale, paletteFor }: HomeWeekColumnProps) {
  const t = useTranslations("home.month")
  const range = weekRange(month, column.from, column.to, locale)
  const label = column.count > 0 ? t("weekAria", { range, count: column.count }) : t("weekAriaEmpty", { range })
  return (
    <Link
      href={`/path?week=${column.weekStart}`}
      aria-label={label}
      className="flex min-h-11 flex-col items-center rounded-xl outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background"
    >
      <div aria-hidden="true" className="flex w-full flex-col items-center">
        <div className="mb-2 flex h-[34px] flex-col items-center">
          <span className="pb-type-day text-m3-on-surface">{weekDayNumber(column.weekStart)}</span>
          <span className="pb-type-annotation mt-[3px] text-m3-outline">{weekMonthAbbr(column.weekStart, locale)}</span>
        </div>
        {column.days.map((day, k) => (
          <div key={k} className="flex h-(--cell) items-center justify-center gap-0.5">
            {day.kind === "empty" && <span className="size-[6.8px] rounded-full border-[1.1px] border-m3-outline opacity-70" />}
            {day.kind === "pebbles" && <DaySeals pebbles={day.pebbles} paletteFor={paletteFor} />}
          </div>
        ))}
        <div className="mt-2 flex h-[46px] items-start">
          <CairnMark cairn={column.cairn} />
        </div>
      </div>
    </Link>
  )
}
