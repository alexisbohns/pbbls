"use client"

import type { CSSProperties } from "react"
import { useTranslations } from "next-intl"
import type { Mark, Pebble } from "@/lib/types"
import { useEmotionPalettes } from "@/lib/data/useEmotionPalettes"
import { useFormatDate, useFormatTime } from "@/lib/i18n"
import { PebbleVisual } from "@/components/pebble/PebbleVisual"
import { cn } from "@/lib/utils"

type PathPebbleRowProps = {
  pebble: Pebble
  mark?: Mark
  onSelect?: (id: string) => void
}

/**
 * Path-specific pebble row. Renders the row tinted by its emotion's
 * palette: a 56×56 surface-coloured rounded thumbnail, the pebble name
 * in the palette's primary (light) / light (dark) colour, and an
 * uppercased "weekday day month · HH:MM" caption at 50% opacity.
 *
 * Mirrors the iOS `PathPebbleRow` shipped in PR #378. Kept separate
 * from the canonical `PebbleCard` so a Path-only tweak cannot regress
 * the Soul / Collection detail rows that still use the card layout.
 */
export function PathPebbleRow({ pebble, mark, onSelect }: PathPebbleRowProps) {
  const t = useTranslations("pebble")
  const formatDate = useFormatDate()
  const formatTime = useFormatTime()
  const { paletteByEmotionId } = useEmotionPalettes()
  const palette = paletteByEmotionId.get(pebble.emotion_id)

  const date = new Date(pebble.happened_at)
  const dateLabel = formatDate(date, {
    weekday: "long",
    day: "numeric",
    month: "long",
  })
  const time = formatTime(date)

  // Pipe palette into CSS custom properties on the row root, so the
  // dark-mode swap happens via `.dark` cascade in globals.css rather
  // than via JS-read `useTheme()` (which causes SSR/CSR mismatch).
  const rowStyle: CSSProperties | undefined = palette
    ? ({
        ["--path-row-name-light"]: palette.primary_color,
        ["--path-row-name-dark"]: palette.light_color,
      } as CSSProperties)
    : undefined

  const thumbnailStyle: CSSProperties | undefined = palette
    ? { backgroundColor: palette.surface_color }
    : undefined

  return (
    <button
      type="button"
      onClick={() => onSelect?.(pebble.id)}
      className={cn(
        "path-row flex w-full items-center gap-3 rounded-xl p-2 text-left transition-all duration-100",
        "hover:bg-background active:scale-[0.98] focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
      )}
      style={rowStyle}
      aria-label={t("cardAria", { name: pebble.name, time })}
    >
      <div
        className="flex size-14 shrink-0 items-center justify-center rounded-xl bg-muted"
        style={thumbnailStyle}
      >
        <PebbleVisual
          pebble={pebble}
          mark={mark}
          tier="thumbnail"
          className="size-10"
        />
      </div>

      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <h3
          className={cn(
            "path-row-name truncate font-heading text-base font-semibold",
            !palette && "text-foreground",
          )}
        >
          {pebble.name}
        </h3>
        <time
          dateTime={pebble.happened_at}
          className={cn(
            "path-row-name text-[10px] uppercase tracking-[0.12em] opacity-50",
            !palette && "text-muted-foreground opacity-100",
          )}
        >
          {dateLabel} · {time}
        </time>
      </div>
    </button>
  )
}
