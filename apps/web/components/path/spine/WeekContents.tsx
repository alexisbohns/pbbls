"use client"

import { useId, type MouseEvent } from "react"
import { useTranslations } from "next-intl"
import { StoneSeal } from "@/components/pebble/stone/StoneSeal"
import { POLARITY_BY_VALENCE, SIZE_BY_INTENSITY, type Size } from "@/lib/config/pebble-geometry"
import type { StonePalette } from "@/lib/config/stone-tones"
import { momentDisplayName } from "@/lib/data/moment-row"
import { useEmotionPalettes } from "@/lib/data/useEmotionPalettes"
import { useFormatTime, useLocale } from "@/lib/i18n"
import type { Moment } from "@/lib/types"
import { contentsNameType } from "@/lib/utils/spine/entry-type"
import { shortWeekday } from "@/lib/utils/spine/spine-dates"
import type { SpineWeek } from "@/lib/utils/spine/spine-week"
import { cn } from "@/lib/utils"

type WeekContentsProps = {
  week: SpineWeek
  /** The day being read, 0 = Monday: its rows get the reading dot. */
  readingDay: number | undefined
  /** Go to the moment's stone and press it, without opening it. Called with the MOMENT id. */
  onGo: (momentId: string) => void
}

const SEAL_WIDTH: Record<Size, string> = { small: "w-5", medium: "w-6", large: "w-[27px]" }

/**
 * The week's contents (#1052, spec §Contents): one row per moment, oldest
 * first, each a real link to its stone. The stone is printed here as a flat
 * seal, never lit (spec D7); a moment that is not a pebble yet prints as a
 * blank medium seal (#1050, R4). The weekday heads the first row of each day.
 */
export function WeekContents({ week, readingDay, onGo }: WeekContentsProps) {
  const t = useTranslations("path.spine")
  const headingId = useId()
  const { paletteByEmotionId } = useEmotionPalettes()
  if (week.days.every((day) => day.moments.length === 0)) return null

  return (
    <section aria-labelledby={headingId} className="mt-10 spread:mt-9">
      <h2 id={headingId} className="font-spine-sc text-[11.5px] font-semibold tracking-[.2em] text-(--spine-ink-2)">
        {t("contents")}
      </h2>
      <ol className="mt-2.5">
        {week.days.flatMap((day) =>
          day.moments.map((moment, i) => (
            <ContentsRow
              key={moment.id}
              moment={moment}
              palette={moment.pebble ? (paletteByEmotionId.get(moment.pebble.emotion_id) ?? null) : null}
              date={day.date}
              first={i === 0}
              here={day.index === readingDay}
              onGo={onGo}
            />
          )),
        )}
      </ol>
    </section>
  )
}

function ContentsRow({
  moment,
  palette,
  date,
  first,
  here,
  onGo,
}: {
  moment: Moment
  palette: StonePalette | null
  date: Date
  first: boolean
  here: boolean
  onGo: (id: string) => void
}) {
  const t = useTranslations("path.spine")
  const { locale } = useLocale()
  const formatTime = useFormatTime()
  const pebble = moment.pebble
  const size: Size = pebble ? SIZE_BY_INTENSITY[pebble.intensity] : "medium"
  const phone = contentsNameType(pebble?.intensity ?? 2, pebble?.positiveness ?? 0, false)
  const name = momentDisplayName(moment.name)

  const go = (e: MouseEvent<HTMLAnchorElement>) => {
    // A modified click keeps the link's own behaviour (a new tab, a copy).
    if (e.metaKey || e.ctrlKey || e.shiftKey || e.altKey || e.button !== 0) return
    e.preventDefault()
    onGo(moment.id)
  }

  return (
    <li className={cn(first && "not-first:border-t not-first:border-(--spine-rule)")}>
      <a
        href={`#pebble-${moment.id}`}
        onClick={go}
        className="group grid min-h-[42px] grid-cols-[40px_36px_1fr_auto] items-center py-0.5 spread:min-h-9"
      >
        <span
          className={cn(
            "relative font-spine-sc text-[10.5px] font-semibold tracking-[.18em] transition-colors duration-300 motion-reduce:transition-none",
            here ? "text-(--spine-ink)" : "text-(--spine-ink-2)",
            // The reading dot, on the day's first row.
            here &&
              first &&
              "before:absolute before:top-1/2 before:-left-3 before:-mt-[3px] before:size-[5px] before:rounded-full before:bg-(--spine-ink) before:content-['']",
          )}
        >
          {first ? shortWeekday(date, locale) : null}
        </span>
        <span className="flex items-center">
          <StoneSeal
            size={size}
            polarity={pebble ? POLARITY_BY_VALENCE[pebble.positiveness] : "neutral"}
            palette={palette}
            glyph={pebble?.glyph ?? null}
            className={SEAL_WIDTH[size]}
          />
        </span>
        <span
          className={cn(
            "py-1.5 pr-2.5 font-spine text-[16.5px] leading-[1.2] text-pretty decoration-(--spine-rule) decoration-1 underline-offset-4 group-hover:underline spread:py-[5px] spread:text-[15.5px]",
            !name && "text-(--spine-ink-3)",
          )}
          style={{ fontWeight: phone.fontWeight, fontStyle: phone.italic || !name ? "italic" : undefined }}
        >
          {name ?? t("untitled")}
        </span>
        <time
          dateTime={moment.happened_at}
          className="font-spine-office text-[12.5px] tracking-[.04em] text-(--spine-ink-3) [font-variant-numeric:lining-nums_tabular-nums]"
        >
          {formatTime(new Date(moment.happened_at))}
        </time>
      </a>
    </li>
  )
}
