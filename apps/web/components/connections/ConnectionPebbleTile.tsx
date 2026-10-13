"use client"

import type { CSSProperties } from "react"
import type { SharedConnectionPebble } from "@/lib/types"
import { useFormatDate, useEmotionLocalized } from "@/lib/i18n"
import { useEmotionPalettes } from "@/lib/data/useEmotionPalettes"
import { usePebbleVisual } from "@/lib/hooks/usePebbleVisual"
import { PebbleArtwork } from "@/components/pebble/PebbleArtwork"

/**
 * One shared pebble on the connection detail page (M51): the pebble drawn from
 * its valence and resolved glyph (palette-tinted via the .pbbls-visual
 * CSS-variable contract), name,
 * emotion dot and date. Deliberately not PebbleVisual (owner-oriented, wants a
 * full Pebble) and deliberately not a link — there is no cross-user pebble
 * read view in v1.
 */
export function ConnectionPebbleTile({ pebble }: { pebble: SharedConnectionPebble }) {
  const formatDate = useFormatDate()
  const { name: emotionName } = useEmotionLocalized({
    slug: pebble.emotion.slug,
    name: pebble.emotion.name,
    label: "",
  })
  const { paletteByEmotionId } = useEmotionPalettes()
  const palette = paletteByEmotionId.get(pebble.emotion.id)
  const wrapperStyle = palette
    ? ({
        ["--pebble-stroke-light"]: palette.primary_color,
        ["--pebble-stroke-dark"]: palette.secondary_color,
      } as CSSProperties)
    : ({
        ["--pebble-stroke-light"]: pebble.emotion.color,
        ["--pebble-stroke-dark"]: pebble.emotion.color,
      } as CSSProperties)

  // Another person's glyph strokes, drawn inside the viewer's own
  // authenticated session: PebbleArtwork hands them to React as attributes
  // only, never as markup (#829).
  const body = usePebbleVisual(pebble)

  return (
    <li className="flex flex-col items-center gap-2 rounded-xl border border-muted p-3 text-center dark:border-accent">
      <PebbleArtwork body={body} glyph={pebble.glyph} aria-hidden className="size-20" style={wrapperStyle} />
      <span className="w-full truncate text-sm font-medium text-foreground">{pebble.name}</span>
      <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
        <span
          className="size-2 rounded-full"
          style={{ backgroundColor: pebble.emotion.color }}
          aria-hidden
        />
        {emotionName}
        <span aria-hidden>·</span>
        {formatDate(pebble.happened_at, { dateStyle: "medium" })}
      </span>
    </li>
  )
}
