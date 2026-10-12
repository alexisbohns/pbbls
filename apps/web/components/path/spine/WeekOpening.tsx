"use client"

import { useTranslations } from "next-intl"
import { useLocale } from "@/lib/i18n"
import { sentenceCase, summaryWords, type WeekWords } from "@/lib/utils/spine/week-opening"

type WeekOpeningProps = {
  words: WeekWords
  titleId: string
  pebbleCount: number
  peopleCount: number
  /** Absent on the first week there is. */
  onPreviousWeek?: () => void
}

/**
 * How a week opens (#1052): the masthead with the way back a week, then the
 * kicker, the title in words and a one-line summary. The title is the week's
 * `<h1>`; there is no other week header.
 */
export function WeekOpening({ words, titleId, pebbleCount, peopleCount, onPreviousWeek }: WeekOpeningProps) {
  const t = useTranslations("path.spine")
  const brand = useTranslations("brand")
  const { locale } = useLocale()
  const { title } = words

  return (
    <>
      <header className="flex items-baseline justify-between text-[11.5px] text-(--spine-ink-2)">
        <span className="font-spine-sc tracking-[.18em] lowercase">
          <b className="font-[650] text-(--spine-ink)">{brand("name")}</b>
        </span>
        {onPreviousWeek && (
          <button
            type="button"
            onClick={onPreviousWeek}
            className="cursor-pointer py-2 font-spine-sc tracking-[.18em] hover:text-(--spine-ink)"
          >
            <span aria-hidden>← </span>
            {t("prevWeek", { n: words.previous.number })}
          </button>
        )}
      </header>
      <div className="mt-[46px] spread:mt-16">
        <p className="font-spine-sc text-xs font-semibold tracking-[.2em] text-(--spine-ink-2)">
          {t("kicker", { ordinal: words.ordinal, year: words.year })}
        </p>
        <h1
          id={titleId}
          className="mt-4 font-spine text-[40px] leading-[1.02] font-light tracking-[-.026em] text-balance [font-variant-numeric:lining-nums] spread:text-[44px]"
        >
          {title.kind === "sameMonth"
            ? t("titleSameMonth", { from: title.from, to: title.to, month: title.month })
            : t("titleCrossMonth", { from: title.from, fromMonth: title.fromMonth, to: title.to, toMonth: title.toMonth })}
        </h1>
        <p className="mt-3 font-spine text-lg font-[380] text-(--spine-ink-2) italic">
          {sentenceCase(
            t("summary", { pebbles: pebbleCount, people: peopleCount, ...summaryWords(pebbleCount, peopleCount, locale) }),
            locale,
          )}
        </p>
      </div>
    </>
  )
}
