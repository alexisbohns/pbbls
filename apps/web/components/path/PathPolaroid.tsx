"use client"

import { useTranslations } from "next-intl"
import type { Mark, Pebble, Soul } from "@/lib/types"
import { useFormatDate, useFormatTime } from "@/lib/i18n"
import { SoulGlyphThumbnail } from "@/components/souls/SoulGlyphThumbnail"
import { PathStone, type StoneSize } from "./PathStone"
import { cn } from "@/lib/utils"

/** The stock itself — paper, corners, and what lifts it off the page. In dark mode
 *  a drop shadow cannot read against a near-black background, so the card swaps to
 *  a faint inset top highlight instead. */
const STOCK =
  "bg-white dark:bg-card rounded-sm " +
  "shadow-[0_1px_0_rgba(0,0,0,0.02),0_4px_8px_-4px_rgba(0,0,0,0.06),0_14px_28px_-16px_rgba(0,0,0,0.09)] " +
  "dark:shadow-[inset_0_1px_0_rgba(255,255,255,0.05),0_4px_8px_-4px_rgba(0,0,0,0.45),0_14px_28px_-16px_rgba(0,0,0,0.6)]"

/** How a card answers the pointer. Written as `group-*` so it fires from the
 *  wrapper: the overlay button covers the figure, and a pointer over that button is
 *  not a pointer over the figure. The deck lies flat at rest — the tilt is
 *  interaction only. */
const HOVER =
  "transition-[transform,box-shadow] duration-300 ease-out " +
  "group-hover:-rotate-3 group-hover:scale-105 " +
  "group-has-[:focus-visible]:-rotate-3 group-has-[:focus-visible]:scale-105 " +
  "group-hover:shadow-[0_2px_0_rgba(0,0,0,0.03),0_8px_16px_-6px_rgba(0,0,0,0.10),0_24px_40px_-16px_rgba(0,0,0,0.16)] " +
  "dark:group-hover:shadow-[inset_0_1px_0_rgba(255,255,255,0.06),0_8px_16px_-6px_rgba(0,0,0,0.55),0_24px_40px_-16px_rgba(0,0,0,0.75)] " +
  "group-active:rotate-2 group-active:scale-95 " +
  "motion-reduce:transition-none motion-reduce:group-hover:rotate-0 motion-reduce:group-hover:scale-100"

/** Top padding on a card with no picture, and how far the stone is pulled above the
 *  edge. Paired: the stone hangs by roughly half its height, and the paper opens up
 *  enough to hold it. A card *with* a picture keeps a square margin instead — the
 *  stone lands on the picture, which needs no extra room. */
const HEAD: Record<StoneSize, { pad: string; lift: string }> = {
  sm: { pad: "pt-7", lift: "-top-5" },
  md: { pad: "pt-9", lift: "-top-7" },
  lg: { pad: "pt-12", lift: "-top-9" },
}

const PAD: Record<"sm" | "md" | "lg", { box: string; square: string }> = {
  sm: { box: "px-2.5 pb-2.5", square: "pt-2.5" },
  md: { box: "px-3 pb-3", square: "pt-3" },
  lg: { box: "px-4 pb-4", square: "pt-4" },
}

function SoulAvatars({
  soulIds,
  soulMap,
  markMap,
  max = 3,
}: {
  soulIds: string[]
  soulMap: Map<string, Soul>
  markMap: Map<string, Mark>
  max?: number
}) {
  if (soulIds.length === 0) return null
  const shown = soulIds.slice(0, max)
  const overflow = soulIds.length - shown.length

  return (
    <div className="flex items-center -space-x-1.5">
      {shown.map((id) => {
        const soul = soulMap.get(id)
        const mark = soul ? markMap.get(soul.glyph_id) : undefined
        return (
          <span
            key={id}
            title={soul?.name}
            className="grid size-5 place-items-center rounded-full bg-background p-0.5 ring-2 ring-white dark:ring-card"
          >
            <SoulGlyphThumbnail mark={mark} className="size-full" strokeClassName="text-foreground/70" />
          </span>
        )
      })}
      {overflow > 0 && (
        <span className="grid size-5 place-items-center rounded-full bg-background text-[9px] font-semibold text-muted-foreground ring-2 ring-white dark:ring-card">
          +{overflow}
        </span>
      )}
    </div>
  )
}

/**
 * One pebble as a polaroid print: the stone laid over the top edge, then the
 * picture, the name in a hand, and who and when underneath.
 */
export function PathPolaroid({
  pebble,
  mark,
  soulMap,
  markMap,
  stoneSize,
  size = "md",
  onSelect,
}: {
  pebble: Pebble
  mark?: Mark
  soulMap: Map<string, Soul>
  markMap: Map<string, Mark>
  stoneSize: StoneSize
  size?: "sm" | "md" | "lg"
  onSelect?: (id: string) => void
}) {
  const t = useTranslations("pebble")
  const formatDate = useFormatDate()
  const formatTime = useFormatTime()
  const picture = pebble.instants[0]
  const head = HEAD[stoneSize]
  const pad = PAD[size]
  const hasSouls = pebble.soul_ids.length > 0
  const happenedAt = new Date(pebble.happened_at)

  // "Tuesday, 20". Composed from two single-field formats rather than one
  // `{ weekday, day }` call: Intl joins those two without a separator in most
  // locales ("Tuesday 20"), and the field order it picks is not ours to set.
  // Each part still goes through the locale formatter, so the weekday is
  // translated and the day numeral is locale-correct.
  const dayLabel = `${formatDate(happenedAt, { weekday: "long" })}, ${formatDate(happenedAt, { day: "numeric" })}`

  // The pebble's accessible name keeps the time — it is the only thing that
  // separates two pebbles recorded on the same day.
  const time = formatTime(happenedAt)

  return (
    // The stone breaks the top edge, so a hovered card has to rise above its
    // neighbours or the one above clips it.
    <div className="group relative hover:z-30">
      <figure className={cn(STOCK, HOVER, "relative", pad.box, picture ? pad.square : head.pad)}>
        <PathStone pebble={pebble} mark={mark} size={stoneSize} className={head.lift} />

        {picture && (
          /* eslint-disable-next-line @next/next/no-img-element -- signed Storage URL, next/image not applicable */
          <img
            src={picture}
            alt=""
            loading="lazy"
            // h-auto is load-bearing: the picture has to take its height from its
            // own width, so portrait and landscape snaps both sit edge to edge.
            className="h-auto w-full rounded-xs"
          />
        )}

        <figcaption className="flex select-none flex-col">
          <h3
            className={cn(
              "text-center font-hand font-bold text-balance text-foreground",
              picture ? "pt-2.5" : "pt-0",
              // Leading after the size — Tailwind's text-* utilities also set
              // line-height, so tailwind-merge drops an earlier `leading-*`.
              size === "sm" ? "text-[1rem] leading-[1.05]" : "text-[1.125rem] leading-[1.05]",
            )}
          >
            {pebble.name}
          </h3>

          {/* Keeps its height with no souls on the card, so every print's caption
              block is the same depth. With souls the date sits opposite them; with
              none it centres under the name, where a lone right-aligned date just
              looked stranded. */}
          <div
            className={cn(
              "flex min-h-5 items-center gap-2 pt-1.5",
              hasSouls ? "justify-between" : "justify-center",
            )}
          >
            {hasSouls && (
              <SoulAvatars soulIds={pebble.soul_ids} soulMap={soulMap} markMap={markMap} />
            )}
            <time
              dateTime={pebble.happened_at}
              className="text-[10px] text-muted-foreground"
            >
              {dayLabel}
            </time>
          </div>
        </figcaption>
      </figure>

      {/* An invisible button over the card rather than a clickable wrapper:
          <button> takes phrasing content, so it may not contain the <figure>. */}
      {onSelect && (
        <button
          type="button"
          onClick={() => onSelect(pebble.id)}
          aria-label={t("cardAria", { name: pebble.name, time })}
          className="absolute inset-0 cursor-pointer rounded-sm outline-offset-4"
        />
      )}
    </div>
  )
}
