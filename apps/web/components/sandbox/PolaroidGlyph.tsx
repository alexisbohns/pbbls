"use client"

import type { Mark, Pebble } from "@/lib/types"
import { PebbleFramed } from "@/components/pebble/PebbleFramed"
import { cn } from "@/lib/utils"

/**
 * The three candidate placements for the pebble on a polaroid card. This is the
 * open question the sandbox exists to answer, so all three ship and the toolbar
 * switches between them live.
 */
export type GlyphVariant = "stamp" | "adaptive" | "margin"

export const GLYPH_VARIANTS: { key: GlyphVariant; label: string; note: string }[] = [
  {
    key: "stamp",
    label: "Stamp",
    note: "Bottom-right, over the picture, on a soft disc. Same spot with or without a picture — reads like a wax seal.",
  },
  {
    key: "adaptive",
    label: "Adaptive",
    note: "With a picture: a small mark straddling the bottom-left corner, half on the image. Without: the pebble becomes the hero, filling the empty picture well.",
  },
  {
    key: "margin",
    label: "Margin mark",
    note: "Never touches the picture. Inline, left of the title, at avatar scale. Without a picture the well collapses and the card is a title-only slip.",
  },
]

type Props = {
  pebble: Pebble
  mark?: Mark
  variant: GlyphVariant
  hasPicture: boolean
  /** `lg` is the full-width card, which can carry a bigger pebble. */
  size?: "md" | "lg"
  className?: string
}

/** Where each variant sits, given whether the card has a picture. */
export function PolaroidGlyph({ pebble, mark, variant, hasPicture, size = "md", className }: Props) {
  const framed = (extra: string) => (
    <PebbleFramed pebble={pebble} mark={mark} tier="thumbnail" className={cn(extra, className)} />
  )

  if (variant === "stamp") {
    return (
      <div
        className={cn(
          "pointer-events-none absolute right-1.5 bottom-1.5 grid place-items-center rounded-full",
          // The disc is what keeps the pebble legible over an arbitrary photo.
          "bg-card/70 backdrop-blur-[2px] ring-1 ring-black/5 dark:ring-white/10",
          size === "lg" ? "size-16 p-1.5" : "size-11 p-1",
        )}
      >
        {framed("size-full")}
      </div>
    )
  }

  if (variant === "adaptive") {
    // No picture → the pebble is the only thing to look at, so it takes the well.
    if (!hasPicture) {
      return (
        <div className={cn("grid place-items-center py-2", size === "lg" ? "h-40" : "h-28")}>
          {framed("size-full")}
        </div>
      )
    }
    // With a picture → half on the image, half on the paper margin.
    return (
      <div
        className={cn(
          "pointer-events-none absolute left-1.5 -bottom-4 grid place-items-center",
          size === "lg" ? "size-16" : "size-12",
        )}
      >
        {/* drop-shadow rather than a disc: the mark reads against both the photo
            and the white paper it half-covers, without a plate around it. */}
        {framed("size-full drop-shadow-[0_1px_3px_rgba(0,0,0,0.35)]")}
      </div>
    )
  }

  // margin — inline in the caption, never over the picture
  return framed(size === "lg" ? "size-10 shrink-0" : "size-8 shrink-0")
}
