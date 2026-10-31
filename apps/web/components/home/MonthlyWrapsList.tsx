"use client"

import { useState } from "react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { daysInMonth, lastDayLabel, monthName, monthYear } from "@/lib/home/month-labels"
import type { HomeWrap } from "@/lib/types"
import { UnfoldDialog } from "./UnfoldDialog"
import { WrapCard } from "./WrapCard"
import { WrapRing } from "./WrapRing"

type MonthlyWrapsListProps = {
  /** Null until the first read lands. */
  wraps: HomeWrap[] | null
  /** The current month, `YYYY-MM-01`, for the empty card. */
  currentMonth: string
  locale: string
  error?: boolean
  onRetry?: () => void
  onReveal: (month: string) => Promise<void>
}

/**
 * Monthly wraps (#1384, spec D13, D23): the finished months as cards in a row
 * that scrolls sideways, the count of cards as the head's figure; no finished
 * month yet, one dashed card saying when the first one folds.
 */
export function MonthlyWrapsList({ wraps, currentMonth, locale, error = false, onRetry, onReveal }: MonthlyWrapsListProps) {
  const t = useTranslations("home")
  const [unfolding, setUnfolding] = useState<HomeWrap | null>(null)
  const titleId = "home-wraps-title"
  const label = (month: string) => {
    const name = monthName(month, locale)
    return `${name.charAt(0).toLocaleUpperCase(locale)}${name.slice(1)} ${monthYear(month)}`
  }

  return (
    <section aria-labelledby={titleId}>
      <div className="mb-[18px] flex items-baseline justify-between gap-3 px-1">
        <h2 id={titleId} className="pb-type-section text-m3-on-surface">
          {t("wraps.title")}
        </h2>
        {wraps !== null && wraps.length > 0 && <span className="pb-type-figure text-m3-primary">{wraps.length}</span>}
      </div>
      {error && wraps === null ? (
        <p className="flex items-center gap-2 px-1 font-heading text-base italic text-m3-on-surface-variant">
          {t("wraps.error")}
          {onRetry && (
            <Button variant="link" size="sm" onClick={onRetry}>
              {t("retry")}
            </Button>
          )}
        </p>
      ) : wraps !== null && wraps.length === 0 ? (
        <Card className="w-[152px] gap-0 rounded-[18px] border border-dashed border-m3-outline-variant bg-transparent p-[18px_16px_18px_18px] text-m3-on-surface shadow-none">
          <div aria-hidden="true" className="mb-3.5 size-[116px]">
            <WrapRing daysInMonth={daysInMonth(currentMonth)} days={[]} />
          </div>
          <p className="pb-type-row-line text-m3-on-surface-variant">{t("wraps.empty", { date: lastDayLabel(currentMonth, locale) })}</p>
        </Card>
      ) : wraps !== null ? (
        <ul className="-mx-4 -mb-[22px] flex snap-x scroll-px-4 gap-3 overflow-x-auto px-4 pt-1 pb-[22px]">
          {wraps.map((wrap) => (
            <li key={wrap.month} className="shrink-0 snap-start">
              <WrapCard wrap={wrap} locale={locale} onUnfold={setUnfolding} />
            </li>
          ))}
        </ul>
      ) : null}
      <UnfoldDialog
        month={unfolding && { label: label(unfolding.month), count: unfolding.count }}
        onUnfold={() => (unfolding ? onReveal(unfolding.month) : Promise.resolve())}
        onClose={() => setUnfolding(null)}
      />
    </section>
  )
}
