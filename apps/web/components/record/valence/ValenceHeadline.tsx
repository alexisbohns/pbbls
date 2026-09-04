"use client"

import { useTranslations } from "next-intl"
import type { Polarity, Size } from "@/lib/config/pebble-geometry"
import { headlineInk } from "@/lib/valence/stone-style"
import { cn } from "@/lib/utils"

/** Caveat Bold at the three size steps, in px. Copied from the iOS tokens. */
const WORD_SIZE: Record<Size, number> = { small: 34, medium: 44, large: 56 }

/**
 * Horizontal breathing room the hand font needs so its glyphs are not cut off.
 *
 * Caveat Bold overhangs its advance width: the terminal `t` flicks up and to
 * the right past where the glyph officially ends. iOS measured 3–8pt of it
 * across the three sizes and had to pad the *string* to fix it, because a
 * SwiftUI frame is not what glyphs are clipped to. On web the frame *is* the
 * lever — `background-clip: text` paints the gradient within the padding box,
 * so ink outside it simply gets no paint — which is why this is padding rather
 * than a space on each side. Symmetric, so the word stays centred.
 */
const INK_OVERHANG: Record<Size, number> = { small: 6, medium: 8, large: 10 }

/**
 * The polarity word: the hand font, sized by the size group, coloured by the
 * polarity. Highlight wears the same gradient its stone does, so the word and
 * the stone read as one thing; lowlight goes darker than its stone ink, because
 * at headline size a grey word looks disabled rather than quiet.
 *
 * Lowercased on small events — their size is carried by the word's own size,
 * which is also why there is no overtitle spelling it out.
 */
export function ValenceWord({
  polarity,
  size,
  faded = false,
  animated = true,
  className,
}: {
  polarity: Polarity
  size: Size
  /** A neighbour one step out, which never competes with the answer. */
  faded?: boolean
  animated?: boolean
  className?: string
}) {
  const t = useTranslations("record.valenceFan")
  const word = t(`word.${polarity}`)
  const ink = headlineInk(polarity)

  return (
    <span
      className={cn(
        "block whitespace-nowrap font-hand leading-none font-bold",
        animated && "transition-[font-size,opacity] duration-200 ease-out motion-reduce:transition-none",
        className,
      )}
      style={{
        fontSize: WORD_SIZE[size],
        paddingInline: INK_OVERHANG[size],
        opacity: faded ? NEIGHBOUR_OPACITY : 1,
        ...(ink.backgroundImage
          ? {
              backgroundImage: ink.backgroundImage,
              WebkitBackgroundClip: "text",
              backgroundClip: "text",
              color: "transparent",
            }
          : { color: ink.color }),
      }}
    >
      {size === "small" ? word.toLocaleLowerCase() : word}
    </span>
  )
}

/** Faded enough that a neighbour never reads as the answer. */
export const NEIGHBOUR_OPACITY = 0.22

/** `OF MY DAY` / `OF MY WEEK` / `OF MY MONTH`, under the word. */
export function ValenceSpan({ size }: { size: Size }) {
  const t = useTranslations("record.valenceFan")
  return (
    <span className="block text-[15px] leading-tight font-semibold tracking-[0.02em] text-muted-foreground">
      {t(`span.${size}`)}
    </span>
  )
}
