"use client"

import { useTranslations } from "next-intl"
import { useLocale } from "@/lib/i18n"
import { longDay, shortWeekday } from "@/lib/utils/spine/spine-dates"

type DaySeamProps = {
  date: Date
  headingId: string
  still: boolean
}

/**
 * Where a day begins (#1052): a hairline seam across the page with riffle
 * teeth, the date set in the margin on the line, and a hidden heading for the
 * day's section. A day with no pebble says *still* and takes no more room.
 */
export function DaySeam({ date, headingId, still }: DaySeamProps) {
  const t = useTranslations("path.spine")
  const { locale } = useLocale()
  return (
    <>
      <div aria-hidden className="spine-seam absolute inset-x-0 top-0 z-1">
        <p className="spine-paper absolute top-[-14px] left-[calc(var(--spine-x)-23px)] z-2 flex w-[46px] flex-col items-center rounded-b-[10px] pb-[5px] spread:top-[-17px] spread:left-[calc(var(--spine-x)-26px)] spread:w-[52px]">
          <b className="px-1 pt-px font-spine text-[25px] leading-none font-[330] tracking-[-.02em] text-(--spine-ink) spread:text-[30px]">
            {date.getDate()}
          </b>
          <span className="mt-[3px] pl-[.2em] font-spine-sc text-[10px] font-semibold tracking-[.2em] text-(--spine-ink-2)">
            {shortWeekday(date, locale)}
          </span>
        </p>
      </div>
      <h2 id={headingId} className="sr-only">
        {longDay(date, locale)}
      </h2>
      {still && (
        <p className="relative z-2 pl-(--spine-col-x) font-spine text-base italic text-(--spine-ink-3)">{t("still")}</p>
      )}
    </>
  )
}
