"use client"

import { useId } from "react"
import { useTranslations } from "next-intl"
import { useEmotionPalettes } from "@/lib/data/useEmotionPalettes"
import { useLocale } from "@/lib/i18n"
import type { Mark, Soul } from "@/lib/types"
import { dayRange } from "@/lib/utils/spine/spine-dates"
import type { SpineDay, SpineWeek } from "@/lib/utils/spine/spine-week"
import { cn } from "@/lib/utils"
import { DaySeam } from "./DaySeam"
import { SpinePebble } from "./SpinePebble"

type SpineFeedProps = {
  week: SpineWeek
  /** Soul ids in the persons-of-the-week order (`personsOfWeek`). */
  order: readonly string[]
  soulMap: ReadonlyMap<string, Soul>
  markMap: ReadonlyMap<string, Mark>
  onOpen: (id: string) => void
  className?: string
}

/**
 * The recto of a week (#1052): raked paper behind one line down the margin,
 * each day opened by its seam, oldest first. Needs `StoneSprite` once on the
 * page (the Path renders it, not each week, so the pager's neighbours share it).
 */
export function SpineFeed({ week, order, soulMap, markMap, onOpen, className }: SpineFeedProps) {
  const t = useTranslations("path.spine")
  const { locale } = useLocale()
  const first = week.days[0]?.date ?? week.weekStart
  const last = week.days.at(-1)?.date ?? week.weekStart

  return (
    <section aria-label={t("feedAria", { range: dayRange(first, last, locale) })} className={cn("relative mt-10 pb-24 spread:mt-0 spread:pt-[34px]", className)}>
      <div aria-hidden className="spine-ground absolute inset-0 z-0" />
      {week.days.map((day) => (
        <SpineDaySection
          key={day.index}
          day={day}
          order={order}
          soulMap={soulMap}
          markMap={markMap}
          onOpen={onOpen}
        />
      ))}
    </section>
  )
}

function SpineDaySection({
  day,
  order,
  soulMap,
  markMap,
  onOpen,
}: {
  day: SpineDay
  order: readonly string[]
  soulMap: ReadonlyMap<string, Soul>
  markMap: ReadonlyMap<string, Mark>
  onOpen: (id: string) => void
}) {
  const headingId = useId()
  const { paletteByEmotionId } = useEmotionPalettes()
  return (
    <section
      aria-labelledby={headingId}
      data-day={day.index}
      className={cn(
        "relative scroll-mt-4",
        // The line: one hairline the length of the week, at --spine-x.
        "before:absolute before:inset-y-0 before:left-[calc(var(--spine-x)-.5px)] before:z-1 before:w-px before:bg-(--spine-line) before:content-['']",
        day.still ? "min-h-[48px] pt-3.5" : "pt-[58px] pb-[34px] spread:pt-[70px] spread:pb-[44px]",
      )}
    >
      <DaySeam date={day.date} headingId={headingId} still={day.still} />
      {!day.still && (
        <div className="flex flex-col gap-[38px] spread:gap-12">
          {day.pebbles.map((pebble) => (
            <SpinePebble
              key={pebble.id}
              pebble={pebble}
              palette={paletteByEmotionId.get(pebble.emotion_id) ?? null}
              order={order}
              soulMap={soulMap}
              markMap={markMap}
              onOpen={onOpen}
            />
          ))}
        </div>
      )}
    </section>
  )
}
