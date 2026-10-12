"use client"

import { useCallback, useEffect, useId, useMemo, useRef } from "react"
import { useTranslations } from "next-intl"
import { useLocale } from "@/lib/i18n"
import { useReadingDay } from "@/lib/hooks/useReadingDay"
import { SpinePressContext, useSpinePresses } from "@/lib/hooks/useSpinePresses"
import type { Mark, Soul } from "@/lib/types"
import { personsOfWeek } from "@/lib/utils/spine/persons-of-week"
import { spineWeek } from "@/lib/utils/spine/spine-week"
import { weekWords } from "@/lib/utils/spine/week-opening"
import { isoWeekKey, type WeekRollEntry } from "@/lib/utils/week-roll-entries"
import { cn } from "@/lib/utils"
import { PersonsOfWeek } from "./PersonsOfWeek"
import { SpineFeed } from "./SpineFeed"
import { WeekContents } from "./WeekContents"
import { WeekMouth } from "./WeekMouth"
import { WeekOpening } from "./WeekOpening"

type SpineWeekProps = {
  entry: WeekRollEntry
  today: Date
  soulMap: ReadonlyMap<string, Soul>
  markMap: ReadonlyMap<string, Mark>
  onOpen: (id: string) => void
  /** Absent on the first week there is. */
  onPreviousWeek?: () => void
  /** Absent on the last week there is. */
  onNextWeek?: () => void
  className?: string
}

/** The contents' press waits for the scroll to land (the mock's 620 ms). */
const GO_PRESS_DELAY_MS = 620
/** A target that moved within this window (a card settling) is re-scrolled once. */
const GO_SETTLE_MS = 800

function reducedMotion(): boolean {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches
}

/** An element's top in its document's layout, whatever any ancestor has scrolled. */
function layoutTop(el: HTMLElement): number {
  let top = 0
  for (let node: HTMLElement | null = el; node; node = node.offsetParent as HTMLElement | null) top += node.offsetTop
  return top
}

/**
 * One week of the Path as a book (#1052): the verso (the opening, the persons
 * of the week and the contents), then the recto (the line with every pebble
 * of the week, oldest first) and the mouth. Phone geometry; the spread at
 * ≥ 900 px lays the same two parts side by side.
 *
 * The persons and the contents follow the reading: the day crossing a line
 * 35% down the screen turns ink in both. A contents link scrolls to its stone,
 * focuses it and presses it without opening the pebble (spec D8).
 *
 * Needs `StoneSprite` once on the page.
 */
export function SpineWeek({
  entry,
  today,
  soulMap,
  markMap,
  onOpen,
  onPreviousWeek,
  onNextWeek,
  className,
}: SpineWeekProps) {
  const t = useTranslations("path.spine")
  const { locale } = useLocale()
  const titleId = useId()
  const hostRef = useRef<HTMLElement>(null)
  const presses = useSpinePresses()

  const week = useMemo(() => spineWeek(entry, today), [entry, today])
  const persons = useMemo(
    () => personsOfWeek(entry.pebbles, entry.weekStart, soulMap, locale),
    [entry, soulMap, locale],
  )
  const order = useMemo(() => persons.map((p) => p.soul.id), [persons])
  const words = useMemo(() => weekWords(entry.weekStart, locale), [entry.weekStart, locale])
  const current = isoWeekKey(today) === entry.weekStartIso
  const readingDay = useReadingDay(
    hostRef,
    useMemo(() => week.days.map((d) => d.index), [week]),
  )

  const goTimers = useRef<ReturnType<typeof setTimeout>[]>([])
  useEffect(() => () => goTimers.current.forEach(clearTimeout), [])

  const go = useCallback(
    (id: string) => {
      const target = document.getElementById(`pebble-${id}`)
      if (!target) return
      goTimers.current.forEach(clearTimeout)
      const behavior = reducedMotion() ? "auto" : "smooth"
      const top = layoutTop(target)
      target.scrollIntoView({ behavior, block: "center" })
      target.querySelector<HTMLElement>("[data-spine-stone]")?.focus({ preventScroll: true })
      goTimers.current = [
        setTimeout(() => presses.press(id), behavior === "auto" ? 0 : GO_PRESS_DELAY_MS),
        // A card above may settle to its photo's ratio while the page scrolls.
        setTimeout(() => {
          if (Math.abs(layoutTop(target) - top) > 2) target.scrollIntoView({ behavior, block: "center" })
        }, GO_SETTLE_MS),
      ]
    },
    [presses],
  )

  return (
    <SpinePressContext value={presses}>
      <article ref={hostRef} aria-labelledby={titleId} className={cn("font-spine text-base leading-[1.4] text-(--spine-ink)", className)}>
        <aside aria-label={t("glanceAria")} className="px-5 pt-[max(18px,env(safe-area-inset-top))] pb-2">
          <WeekOpening
            words={words}
            titleId={titleId}
            pebbleCount={entry.pebbles.length}
            peopleCount={persons.length}
            onPreviousWeek={onPreviousWeek}
          />
          <PersonsOfWeek persons={persons} weekStart={entry.weekStart} markMap={markMap} readingDay={readingDay} />
          <WeekContents week={week} readingDay={readingDay} onGo={go} />
        </aside>
        <SpineFeed
          week={week}
          order={order}
          soulMap={soulMap}
          markMap={markMap}
          onOpen={onOpen}
          footer={<WeekMouth words={words} current={current} onNextWeek={onNextWeek} />}
        />
      </article>
    </SpinePressContext>
  )
}
