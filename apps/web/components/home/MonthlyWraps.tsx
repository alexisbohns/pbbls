"use client"

import { useHomeWraps } from "@/lib/data/useHomeWraps"
import { currentMonthStart, readerTimeZone } from "@/lib/home/timezone"
import { useLocale } from "@/lib/i18n"
import { MonthlyWrapsList } from "./MonthlyWrapsList"

/** Home's monthly wraps (#1384, spec D13), read through `home_wraps` and unfolded through `reveal_wrap`. */
export function MonthlyWraps() {
  const { locale } = useLocale()
  const { data, error, retry, reveal } = useHomeWraps()
  return (
    <MonthlyWrapsList
      wraps={data}
      currentMonth={currentMonthStart(readerTimeZone())}
      locale={locale}
      error={error !== null}
      onRetry={retry}
      onReveal={reveal}
    />
  )
}
