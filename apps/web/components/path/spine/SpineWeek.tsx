"use client"

import { useCallback, useContext, useEffect, useId, useMemo, useRef } from "react"
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
import { PathEmptyState } from "@/components/path/PathEmptyState"
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
  /** Opens the read page, with the MOMENT id. */
  onOpen: (momentId: string) => void
  /** Absent on the first week there is. */
  onPreviousWeek?: () => void
  /** Absent on the last week there is. */
  onNextWeek?: () => void
  /**
   * One half only (#1384, two-area spec D4): the verso (opening, persons,
   * contents) or the recto (the line and the mouth), each a plain column, for a
   * Path whose halves sit in different areas. Absent: the whole book.
   */
  only?: "verso" | "recto"
  /** The verso drawn alone follows the day its recto is reading. */
  readingDay?: number
  /** The recto drawn alone publishes the day it is reading. */
  onReadingDay?: (day: number | undefined) => void
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
 * of the week, oldest first) and the mouth. From 900 px the two sit side by
 * side as a spread (`spine-spread`): the verso sticks while the recto scrolls.
 * Whoever lays chrome over the verso (the Path's roll and dock) reserves its
 * room with `--spine-verso-top` and `--spine-verso-foot`.
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
  only,
  readingDay: readingDayFromRecto,
  onReadingDay,
  className,
}: SpineWeekProps) {
  const t = useTranslations("path.spine")
  const { locale } = useLocale()
  const titleId = useId()
  const hostRef = useRef<HTMLElement>(null)
  // A Path that draws its halves apart shares one set of presses above them,
  // so a contents row on the verso presses its stone on the recto.
  const sharedPresses = useContext(SpinePressContext)
  const ownPresses = useSpinePresses()
  const presses = sharedPresses ?? ownPresses

  const week = useMemo(() => spineWeek(entry, today), [entry, today])
  const persons = useMemo(
    () => personsOfWeek(entry.moments, entry.weekStart, soulMap, locale),
    [entry, soulMap, locale],
  )
  const order = useMemo(() => persons.map((p) => p.soul.id), [persons])
  const words = useMemo(() => weekWords(entry.weekStart, locale), [entry.weekStart, locale])
  const current = isoWeekKey(today) === entry.weekStartIso
  const lineReadingDay = useReadingDay(
    hostRef,
    useMemo(() => week.days.map((d) => d.index), [week]),
  )
  const readingDay = only === "verso" ? readingDayFromRecto : lineReadingDay
  useEffect(() => {
    onReadingDay?.(lineReadingDay)
  }, [onReadingDay, lineReadingDay])

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
      <article
        ref={hostRef}
        aria-labelledby={only === "recto" ? undefined : titleId}
        className={cn(only === undefined && "spine-spread", "font-spine text-base leading-[1.4] text-(--spine-ink)", className)}
      >
        {only !== "recto" && (
          <aside
            aria-label={t("glanceAria")}
            className={cn(
              "px-5 pt-[max(18px,env(safe-area-inset-top))] pb-2",
              // The verso stays put while the recto scrolls, and scrolls itself
              // without a bar when the contents outgrow it. A Path that lays its
              // roll and dock over this column says how much room they take.
              only === undefined &&
                "spread:sticky spread:top-0 spread:max-h-(--spine-page-h,100dvh) spread:overflow-y-auto spread:[scrollbar-width:none] spread:[&::-webkit-scrollbar]:hidden",
              only === undefined &&
                "spread:pt-[calc(34px+var(--spine-verso-top,0px))] spread:pr-12 spread:pb-[calc(40px+var(--spine-verso-foot,0px))] spread:pl-9",
            )}
          >
            <WeekOpening
              words={words}
              titleId={titleId}
              pebbleCount={entry.pebbleCount}
              peopleCount={persons.length}
              onPreviousWeek={onPreviousWeek}
            />
            <PersonsOfWeek persons={persons} weekStart={entry.weekStart} markMap={markMap} readingDay={readingDay} />
            <WeekContents week={week} readingDay={readingDay} onGo={go} />
          </aside>
        )}
        {only === "verso" ? null : entry.moments.length === 0 ? (
          // Only the current week can be empty (the roll lists weeks with
          // moments, plus this one): the recto is the fresh-week invitation.
          <section className="spread:pt-[34px]">
            <PathEmptyState />
          </section>
        ) : (
          <SpineFeed
            week={week}
            order={order}
            soulMap={soulMap}
            markMap={markMap}
            onOpen={onOpen}
            footer={<WeekMouth words={words} current={current} onNextWeek={onNextWeek} />}
          />
        )}
      </article>
    </SpinePressContext>
  )
}
