"use client"

import type { CSSProperties } from "react"
import { useTranslations } from "next-intl"

import type { StoneLook } from "@/lib/types"
import { EMOTIONS } from "@/lib/config/emotions"
import { useEmotionLocalized } from "@/lib/i18n"
import { useEmotionPalettes } from "@/lib/data/useEmotionPalettes"
import { momentDisplayName } from "@/lib/data/moment-row"
import { usePebbleVisual } from "@/lib/hooks/usePebbleVisual"
import { PebbleArtwork } from "./PebbleArtwork"

/** Stroke colour when the emotion is unknown to the config. */
const NEUTRAL_GRAY = "#9CA3AF"

type PebbleVisualProps = {
  look: StoneLook
  className?: string
  // Force a single stroke color regardless of theme. Used by the snap+pebble
  // bundle on the read page where the palette-tinted box would otherwise blend
  // with the dark-mode stroke (`secondary_color`).
  strokeOverride?: string
}

/**
 * The owner's pebble: the client-drawn body and the glyph the server resolved
 * for it (`look.glyph`, its collection's else its domain's, #1046 D5).
 */
export function PebbleVisual({ look, className, strokeOverride }: PebbleVisualProps) {
  const body = usePebbleVisual(look)

  const t = useTranslations("pebble")
  const tCommon = useTranslations("common")
  const tMoment = useTranslations("moment")
  const matched = EMOTIONS.find((e) => e.id === look.emotion_id)
  const fallbackEmotion = matched ?? { slug: "", name: tCommon("unknown"), label: "" }
  const { name: emotionName } = useEmotionLocalized(fallbackEmotion)

  // The artwork strokes in currentColor. The wrapper sets two CSS custom
  // properties; globals.css picks --pebble-stroke-light in light mode and
  // --pebble-stroke-dark in dark mode, so theme switches are CSS-only (no JS
  // subscription, no hydration mismatch). Without a category palette both
  // take the flat emotion colour.
  const { paletteByEmotionId } = useEmotionPalettes()
  const palette = paletteByEmotionId.get(look.emotion_id)
  const flat = matched?.color ?? NEUTRAL_GRAY
  const [light, dark] = strokeOverride
    ? [strokeOverride, strokeOverride]
    : palette
      ? [palette.primary_color, palette.secondary_color]
      : [flat, flat]
  const wrapperStyle = {
    ["--pebble-stroke-light"]: light,
    ["--pebble-stroke-dark"]: dark,
  } as CSSProperties

  return (
    <PebbleArtwork
      body={body}
      glyph={look.glyph}
      role="img"
      aria-label={t("visualAria", {
        name: momentDisplayName(look.name) ?? tMoment("untitled"),
        emotion: emotionName,
        intensity: look.intensity,
      })}
      className={className}
      style={wrapperStyle}
    />
  )
}
