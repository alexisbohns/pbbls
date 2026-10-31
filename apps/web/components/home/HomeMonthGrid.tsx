import { useId } from "react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import type { StonePalette } from "@/lib/config/stone-tones"
import { monthColumns } from "@/lib/home/month-columns"
import { monthTitle, weekdayLetters } from "@/lib/home/month-labels"
import type { HomeMonth } from "@/lib/types"
import { HomeWeekColumn } from "./HomeWeekColumn"

type HomeMonthGridProps = {
  month: HomeMonth
  locale: string
  paletteFor: (emotionId: string | null) => StonePalette | null
  /** Shown as one quiet line with a retry under the grid (spec D23). */
  error?: boolean
  onRetry?: () => void
}

/**
 * The current month as week columns (#1384, spec D9, D10): a navigation named
 * by the month, a weekday legend for the eye only, and one link per week.
 */
export function HomeMonthGrid({ month, locale, paletteFor, error = false, onRetry }: HomeMonthGridProps) {
  const t = useTranslations("home")
  const labelId = useId()
  const columns = monthColumns(month)
  return (
    <nav aria-labelledby={labelId}>
      <span id={labelId} className="sr-only">
        {monthTitle(month.month, locale)}
      </span>
      <div className="flex [--cell:1.75rem]">
        <div aria-hidden="true" className="flex w-4 flex-col pt-[42px]">
          {weekdayLetters(locale).map((letter, k) => (
            <span key={k} className="pb-type-annotation grid h-(--cell) place-items-center text-m3-outline">
              {letter}
            </span>
          ))}
        </div>
        <ol className="flex flex-1">
          {columns.map((column) => (
            <li key={column.weekStart} className="flex-1">
              <HomeWeekColumn column={column} month={month.month} locale={locale} paletteFor={paletteFor} />
            </li>
          ))}
        </ol>
      </div>
      {error && (
        <p className="mt-2 flex items-center gap-2 font-heading text-base italic text-m3-on-surface-variant">
          {t("month.error")}
          {onRetry && (
            <Button variant="link" size="sm" onClick={onRetry}>
              {t("retry")}
            </Button>
          )}
        </p>
      )}
    </nav>
  )
}
