"use client"

import { useId } from "react"
import { useTranslations } from "next-intl"
import { useLocale } from "@/lib/i18n"
import { cardinalWords } from "@/lib/i18n/number-words"
import type { Mark } from "@/lib/types"
import { splitPersons, type PersonOfWeek } from "@/lib/utils/spine/persons-of-week"
import { dotDays, weekdayLetters } from "@/lib/utils/spine/week-opening"
import { cn } from "@/lib/utils"
import { SoulMark } from "./SoulMark"

type PersonsOfWeekProps = {
  persons: readonly PersonOfWeek[]
  weekStart: Date
  markMap: ReadonlyMap<string, Mark>
  /** The day being read, 0 = Monday: its letter in the header turns ink. */
  readingDay: number | undefined
}

/**
 * Who was there (#1052, spec §Persons of the week): each soul of the week with
 * its glyph, its name in small caps and seven dots for the days it appears on.
 * Eight rows, then the rest behind "and N others", so a fourteen-person picnic
 * does not push the contents off the first screen.
 */
export function PersonsOfWeek({ persons, weekStart, markMap, readingDay }: PersonsOfWeekProps) {
  const t = useTranslations("path.spine")
  const { locale } = useLocale()
  const headingId = useId()
  if (persons.length === 0) return null
  const { shown, rest } = splitPersons(persons)

  return (
    <section aria-labelledby={headingId} className="mt-10 spread:mt-11">
      <div className="flex items-baseline justify-between">
        <h2 id={headingId} className="font-spine-sc text-[11.5px] font-semibold tracking-[.2em] text-(--spine-ink-2)">
          {t("persons")}
        </h2>
        <p aria-hidden className="flex gap-[5px]">
          {weekdayLetters(weekStart, locale).map((letter, i) => (
            <i
              key={i}
              className={cn(
                "w-[7px] flex-none text-center font-spine-sc text-[9.5px] leading-3 font-semibold not-italic transition-colors duration-300 motion-reduce:transition-none",
                i === readingDay ? "text-(--spine-ink)" : "text-(--spine-ink-3)",
              )}
            >
              {letter}
            </i>
          ))}
        </p>
      </div>
      <ul className="mt-[9px] border-t border-(--spine-rule)">
        {shown.map((person) => (
          <PersonRow key={person.soul.id} person={person} weekStart={weekStart} markMap={markMap} />
        ))}
      </ul>
      {rest.length > 0 && (
        <details className="group">
          <summary className="flex min-h-11 cursor-pointer list-none items-center border-b border-(--spine-rule) pl-10 font-spine text-base font-[380] text-(--spine-ink-2) italic marker:hidden hover:text-(--spine-ink) spread:min-h-10 [&::-webkit-details-marker]:hidden">
            {t("personsMore", { words: cardinalWords(rest.length, locale, "feminine"), count: rest.length })}
          </summary>
          <ul>
            {rest.map((person) => (
              <PersonRow key={person.soul.id} person={person} weekStart={weekStart} markMap={markMap} />
            ))}
          </ul>
        </details>
      )}
    </section>
  )
}

function PersonRow({
  person,
  weekStart,
  markMap,
}: {
  person: PersonOfWeek
  weekStart: Date
  markMap: ReadonlyMap<string, Mark>
}) {
  const t = useTranslations("path.spine")
  const { locale } = useLocale()
  const { soul, days } = person
  return (
    <li className="grid min-h-11 grid-cols-[30px_1fr_auto] items-center gap-x-2.5 border-b border-(--spine-rule) spread:min-h-10">
      <SoulMark mark={markMap.get(soul.glyph_id)} weight={1.15} className="size-6 text-(--spine-ink)" />
      <span className="font-spine-sc text-[15px] font-semibold tracking-[.1em] lowercase">{soul.name}</span>
      <span role="img" aria-label={t("dayDotsAria", dotDays(days, weekStart, locale))} className="flex gap-[5px]">
        {days.map((on, i) => (
          <i
            key={i}
            className={cn(
              "size-[7px] flex-none rounded-full",
              on ? "bg-(--spine-ink)" : "opacity-70 shadow-[inset_0_0_0_1px_var(--spine-ink-3)]",
            )}
          />
        ))}
      </span>
    </li>
  )
}
