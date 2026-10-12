"use client"

import { useCallback, useEffect, useId, useRef, type CSSProperties } from "react"
import { useTranslations } from "next-intl"
import { LitStone } from "@/components/pebble/stone/LitStone"
import { StoneRipple } from "@/components/pebble/stone/StoneRipple"
import { EMOTIONS } from "@/lib/config/emotions"
import { POLARITY_BY_VALENCE, SIZE_BY_INTENSITY } from "@/lib/config/pebble-geometry"
import { OPEN_DELAY_MS } from "@/lib/config/stone-motion"
import { BLANK_PALETTE, stonePaletteVars, type StonePalette } from "@/lib/config/stone-tones"
import { hashUUID } from "@/lib/engine/seed"
import { useEmotionLocalized, useFormatTime, useLocale } from "@/lib/i18n"
import { useRegisterSpinePress } from "@/lib/hooks/useSpinePresses"
import { useStonePress } from "@/lib/hooks/useStonePress"
import type { Mark, Pebble, Soul } from "@/lib/types"
import { entryType } from "@/lib/utils/spine/entry-type"
import { longDay, shortWeekday } from "@/lib/utils/spine/spine-dates"
import { STONE_WIDTH_CLASS, stoneHeight } from "@/lib/utils/spine/stone-box"
import { cn } from "@/lib/utils"
import { PeopleLine } from "./PeopleLine"
import { SpineCard } from "./SpineCard"
import { SpineEntry } from "./SpineEntry"

type SpinePebbleProps = {
  pebble: Pebble
  /** Null for a moment with no emotion yet: a blank stone. */
  palette: StonePalette | null
  order: readonly string[]
  soulMap: ReadonlyMap<string, Soul>
  markMap: ReadonlyMap<string, Mark>
  onOpen: (id: string) => void
}

const NO_EMOTION = { slug: "", name: "", label: "" }

function reducedMotion(): boolean {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches
}

/**
 * One pebble on the Spine (#1052): its lit stone on the margin line, and to
 * the right a Deck card if it has a photo, the entry set as type if not.
 * Pressing the stone dips it, ripples the ground and glints, then opens the
 * pebble once the dip lands (spec D8); pressing the card or entry opens it at
 * once and ripples its stone all the same.
 */
export function SpinePebble({ pebble, palette, order, soulMap, markMap, onOpen }: SpinePebbleProps) {
  const t = useTranslations("path.spine")
  const { locale } = useLocale()
  const formatTime = useFormatTime()
  const emotion = useEmotionLocalized(EMOTIONS.find((e) => e.id === pebble.emotion_id) ?? NO_EMOTION).name
  const titleId = useId()
  const { hostRef, press, ripple, endRipple, touched } = useStonePress()
  useRegisterSpinePress(pebble.id, press)
  const openTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  useEffect(
    () => () => {
      if (openTimer.current) clearTimeout(openTimer.current)
    },
    [],
  )

  const size = SIZE_BY_INTENSITY[pebble.intensity]
  const polarity = POLARITY_BY_VALENCE[pebble.positiveness]
  const at = new Date(pebble.happened_at)
  const time = formatTime(at)
  const hasCard = pebble.cover !== null
  const phone = entryType(pebble.intensity, pebble.positiveness, false)
  const wide = entryType(pebble.intensity, pebble.positiveness, true)
  const height = stoneHeight(size)

  const names = pebble.soul_ids.map((id) => soulMap.get(id)?.name).filter((n): n is string => !!n)
  const openLabel = t(hasCard ? "cardAria" : "stoneAria", {
    name: pebble.name || t("untitled"),
    date: longDay(at, locale),
    time,
    stone: t(`stone.${size}.${polarity}`),
    emotion,
    with: names.length > 0 ? t("withNames", { names: new Intl.ListFormat(locale).format(names) }) : "",
  })

  const pressStone = useCallback(() => {
    press()
    if (openTimer.current) clearTimeout(openTimer.current)
    openTimer.current = setTimeout(() => onOpen(pebble.id), reducedMotion() ? 0 : OPEN_DELAY_MS)
  }, [press, onOpen, pebble.id])

  const openNow = useCallback(() => {
    press()
    onOpen(pebble.id)
  }, [press, onOpen, pebble.id])

  const people = (
    <PeopleLine
      soulIds={pebble.soul_ids}
      order={order}
      soulMap={soulMap}
      markMap={markMap}
      variant={hasCard ? "card" : "entry"}
    />
  )

  const style = {
    ...stonePaletteVars(palette ?? BLANK_PALETTE),
    "--fs-phone": `${phone.fontSize}px`,
    "--fs-wide": `${wide.fontSize}px`,
    "--sth-phone": `${height.phone}px`,
    "--sth-wide": `${height.wide}px`,
    "--anchor": hasCard ? "31px" : "calc(var(--fs) * .6)",
  } as CSSProperties

  const stoneBox = cn(
    "absolute left-(--spine-x) top-(--anchor) -translate-x-1/2 -translate-y-1/2",
    STONE_WIDTH_CLASS[size],
  )

  return (
    <article
      ref={hostRef}
      id={`pebble-${pebble.id}`}
      aria-labelledby={titleId}
      className={cn(
        "spine-spot relative scroll-mt-[90px] pr-(--spine-pad-r) pl-(--spine-col-x)",
        "[--fs:var(--fs-phone)] [--sth:var(--sth-phone)] spread:[--fs:var(--fs-wide)] spread:[--sth:var(--sth-wide)]",
        "min-h-[calc(var(--anchor)+var(--sth)/2+14px)]",
        // A card hangs off its stone by a leader.
        hasCard &&
          "before:absolute before:top-(--anchor) before:left-(--spine-x) before:z-1 before:h-px before:w-[calc(var(--spine-col-x)-var(--spine-x))] before:bg-(--spine-line) before:content-['']",
      )}
      style={style}
    >
      {ripple !== null && (
        <StoneRipple
          key={ripple}
          size={size}
          polarity={palette ? polarity : "neutral"}
          onDone={endRipple}
          className={cn(stoneBox, "pointer-events-none z-1 overflow-visible")}
        />
      )}
      <button
        type="button"
        data-spine-stone
        onClick={pressStone}
        aria-label={openLabel}
        className={cn(
          stoneBox,
          "z-3 cursor-pointer rounded-[40%] [-webkit-tap-highlight-color:transparent]",
          "after:absolute after:-inset-2.5 after:content-['']",
          "focus-visible:rounded-full focus-visible:outline-[1.5px] focus-visible:outline-offset-[9px] focus-visible:outline-(--spine-ink)",
        )}
      >
        <LitStone
          size={size}
          polarity={polarity}
          palette={palette}
          glyph={pebble.glyph}
          seed={hashUUID(pebble.id)}
          touched={touched}
        />
      </button>

      {pebble.cover ? (
        <SpineCard
          titleId={titleId}
          name={pebble.name}
          untitled={t("untitled")}
          intensity={pebble.intensity}
          valence={pebble.positiveness}
          polarity={polarity}
          cover={pebble.cover}
          alt={pebble.name}
          day={at.getDate()}
          weekday={shortWeekday(at, locale)}
          time={time}
          dateTime={pebble.happened_at}
          emotion={emotion}
          photoUnavailable={t("photoUnavailable")}
          people={people}
          openLabel={openLabel}
          onOpen={openNow}
        />
      ) : (
        <SpineEntry
          titleId={titleId}
          name={pebble.name}
          untitled={t("untitled")}
          type={phone}
          time={time}
          dateTime={pebble.happened_at}
          emotion={emotion}
          people={people}
          openLabel={openLabel}
          onOpen={openNow}
        />
      )}
    </article>
  )
}
