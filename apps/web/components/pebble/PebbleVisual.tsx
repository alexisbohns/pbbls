"use client"

import type { CSSProperties } from "react"
import { useTranslations } from "next-intl"

import type { Mark, Pebble } from "@/lib/types"
import type { RenderTier } from "@/lib/engine"
import { EMOTIONS } from "@/lib/config/emotions"
import { useEmotionLocalized } from "@/lib/i18n"
import { useEmotionPalettes } from "@/lib/data/useEmotionPalettes"
import { usePebbleVisual } from "@/lib/hooks/usePebbleVisual"
import { cn } from "@/lib/utils"

type PebbleVisualProps = {
  pebble: Pebble
  mark?: Mark | null
  tier?: RenderTier
  className?: string
}

export function PebbleVisual({
  pebble,
  mark = null,
  tier = "thumbnail",
  className,
}: PebbleVisualProps) {
  // Prefer the server-composed render written by the compose-pebble edge
  // function. Fall back to the client engine for legacy rows that pre-date
  // the remote engine and for unauthenticated previews (e.g. landing page
  // seed pebbles) where no render exists yet.
  const fallback = usePebbleVisual(pebble, mark, tier)
  const isServerRender = pebble.render_svg !== null
  const svg = pebble.render_svg ?? fallback.svg

  const t = useTranslations("pebble")
  const tCommon = useTranslations("common")
  const matched = EMOTIONS.find((e) => e.id === pebble.emotion_id)
  const fallbackEmotion = matched ?? { slug: "", name: tCommon("unknown"), label: "" }
  const { name: emotionName } = useEmotionLocalized(fallbackEmotion)

  // Server-composed SVGs use stroke="currentColor" and carry their own
  // width/height attributes. The wrapper sets two CSS custom properties from
  // the category palette; globals.css picks --pebble-stroke-light in light
  // mode and --pebble-stroke-dark in dark mode, so theme switches are
  // CSS-only (no JS subscription, no hydration mismatch).
  // The client-engine fallback already inlines the emotion color via
  // `recolor()`, so leaving wrapperStyle undefined for it is harmless.
  const { paletteByEmotionId } = useEmotionPalettes()
  const palette = paletteByEmotionId.get(pebble.emotion_id)
  const wrapperStyle: CSSProperties | undefined =
    isServerRender && palette
      ? ({
          ["--pebble-stroke-light"]: palette.primary_color,
          ["--pebble-stroke-dark"]: palette.secondary_color,
        } as CSSProperties)
      : undefined

  return (
    <div
      data-slot="pebble-visual"
      role="img"
      aria-label={t("visualAria", {
        name: pebble.name,
        emotion: emotionName,
        intensity: pebble.intensity,
      })}
      className={cn("pbbls-visual", className)}
      style={wrapperStyle}
      dangerouslySetInnerHTML={{ __html: svg }}
    />
  )
}
