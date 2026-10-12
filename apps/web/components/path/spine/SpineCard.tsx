"use client"

import { useState, type CSSProperties, type ReactNode } from "react"
import type { Polarity } from "@/lib/config/pebble-geometry"
import type { PebbleCover } from "@/lib/types"
import { useSnapAspect } from "@/lib/data/useSnapAspect"
import { cardTitleCut, type Valence } from "@/lib/utils/spine/entry-type"
import { cardCap, cardEdge, type Intensity } from "@/lib/utils/spine/card-size"
import { cn } from "@/lib/utils"

type SpineCardProps = {
  titleId: string
  name: string
  untitled: string
  intensity: Intensity
  valence: Valence
  polarity: Polarity
  cover: PebbleCover
  /** The photo's description for screen readers; the pebble's name will do. */
  alt: string
  day: number
  weekday: string
  time: string
  dateTime: string
  emotion: string
  photoUnavailable: string
  people: ReactNode
  openLabel: string
  onOpen: () => void
}

// The painted edge under the card, by valence: Paper, then Night.
const EDGE: Record<Polarity, string> = {
  highlight: "[--edge:var(--p)] dark:[--edge:var(--s)]",
  neutral: "[--edge:var(--s)] dark:[--edge:var(--p)]",
  lowlight: "[--edge:var(--d)] dark:[--edge:var(--sh)]",
}

const FIELD: Record<Polarity, string> = {
  highlight: "spine-field-high",
  neutral: "spine-field-neutral",
  lowlight: "spine-field-low",
}

/**
 * A pebble with a photo: the Deck card (#1052). Its width follows the photo's
 * ratio under a cap set by intensity, `min(column, (cap − 123) × ratio + 95)`,
 * floored at 180 px; the photo is never cropped. Until the ratio is known the
 * field is square, then it eases to the photo's shape (spec D14) through the
 * registered `--spine-ar`.
 *
 * The chrome is in card units, `u = min(card width / 70, 4.4px)`.
 */
export function SpineCard({
  titleId,
  name,
  untitled,
  intensity,
  valence,
  polarity,
  cover,
  alt,
  day,
  weekday,
  time,
  dateTime,
  emotion,
  photoUnavailable,
  people,
  openLabel,
  onOpen,
}: SpineCardProps) {
  const { ratio, learn } = useSnapAspect(cover)
  const [failed, setFailed] = useState(cover.url === null)
  const cut = cardTitleCut(valence)

  const style = {
    "--spine-ar": ratio ?? 1,
    "--cap-phone": `${cardCap(intensity, false)}px`,
    "--cap-wide": `${cardCap(intensity, true)}px`,
    "--t": `${cardEdge(intensity)}px`,
  } as CSSProperties

  return (
    <div
      className={cn(
        "@container relative z-2 rounded-[11px] bg-(--spine-stock)",
        "[--cap:var(--cap-phone)] spread:[--cap:var(--cap-wide)]",
        "[width:max(min(100%,calc((var(--cap)-123px)*var(--spine-ar)+95px)),min(180px,100%))]",
        "shadow-[0_var(--t)_0_0_var(--edge),var(--spine-card-shadow)]",
        "transition-[--spine-ar] duration-240 ease-out motion-reduce:transition-none",
        EDGE[polarity],
      )}
      style={style}
    >
      <div
        className={cn(
          "relative [--u:min(calc(100cqw/70),4.4px)]",
          "px-[calc(var(--u)*9)] pt-[calc(var(--u)*4.6)] pb-[calc(var(--u)*6.2)]",
          // The printed border.
          "before:pointer-events-none before:absolute before:inset-[calc(var(--u)*1.6)] before:rounded-[calc(var(--u)*1.3)] before:border before:border-(--spine-card-rule) before:content-['']",
        )}
      >
        <div
          aria-hidden
          className="absolute top-[calc(var(--u)*4.3)] left-[calc(var(--u)*2.1)] flex w-[calc(var(--u)*6.6)] flex-col items-center text-(--spine-ink)"
        >
          <span className="font-spine-office text-[length:calc(var(--u)*5.3)] leading-[.86] font-semibold tracking-[-.045em]">
            {day}
          </span>
          <span className="mt-[calc(var(--u)*1.1)] pl-[.14em] font-spine-sc text-[length:calc(var(--u)*1.8)] leading-none font-[650] tracking-[.14em]">
            {weekday}
          </span>
        </div>
        <p
          aria-hidden
          className="absolute top-[calc(var(--u)*4.7)] right-[calc(var(--u)*3.7)] flex items-center font-spine-office text-[length:calc(var(--u)*1.95)] leading-none font-[560] tracking-[.07em] whitespace-nowrap text-(--spine-ink-2) tabular-nums [writing-mode:vertical-rl]"
        >
          {time}
          <span className="my-[calc(var(--u)*1.2)] block h-[calc(var(--u)*2.4)] w-px bg-current opacity-50" />
          <span className="font-spine-sc lowercase font-[650] tracking-[.15em] text-(--spine-spot)">{emotion}</span>
        </p>

        <figure
          className={cn(
            "relative rounded-[calc(var(--u)*.5)] p-[calc(var(--u)*1.8)]",
            "after:pointer-events-none after:absolute after:inset-0 after:rounded-[inherit] after:shadow-[inset_0_0_0_1px_color-mix(in_oklab,var(--spine-spot)_30%,transparent)] after:content-['']",
            FIELD[polarity],
          )}
        >
          <div className="relative w-full [aspect-ratio:var(--spine-ar)]">
            {failed ? (
              <p className="absolute inset-0 grid place-items-center px-2 text-center font-spine-sc text-[length:calc(var(--u)*2.4)] tracking-[.12em] text-destructive lowercase">
                {photoUnavailable}
              </p>
            ) : (
              /* eslint-disable-next-line @next/next/no-img-element -- signed Storage URL, next/image not applicable */
              <img
                src={cover.url ?? undefined}
                alt={alt}
                loading="lazy"
                decoding="async"
                onLoad={(e) => learn(e.currentTarget.naturalWidth, e.currentTarget.naturalHeight)}
                onError={() => setFailed(true)}
                className="size-full rounded-[1px] bg-[color-mix(in_oklab,var(--spine-spot)_18%,var(--spine-stock))] object-contain [filter:var(--spine-photo-filter)]"
              />
            )}
          </div>
        </figure>

        <div className="pt-[calc(var(--u)*3.3)] text-center">
          <h3
            id={titleId}
            className={cn(
              "font-spine text-[length:calc(var(--u)*4.4)] leading-[1.1] tracking-[-.012em] text-balance text-(--spine-ink)",
              !name && "sr-only",
            )}
            style={{ fontWeight: cut.fontWeight, fontStyle: cut.italic ? "italic" : undefined }}
          >
            {name || untitled}
          </h3>
          <p className="sr-only">
            <time dateTime={dateTime}>{time}</time>, {emotion}
          </p>
          {people}
        </div>
      </div>
      <button
        type="button"
        onClick={onOpen}
        aria-label={openLabel}
        className="absolute inset-0 cursor-pointer rounded-[11px] focus-visible:outline-[1.5px] focus-visible:outline-offset-4 focus-visible:outline-(--spine-ink)"
      />
    </div>
  )
}
