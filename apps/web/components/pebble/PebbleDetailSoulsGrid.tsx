"use client"

import { useTranslations } from "next-intl"
import type { Mark, Soul } from "@/lib/types"
import { SoulGlyphThumbnail } from "@/components/souls/SoulGlyphThumbnail"
import { cn } from "@/lib/utils"

type PebbleDetailSoulsGridProps = {
  souls: Soul[]
  marks: Mark[]
  /** Makes each soul a button that opens the souls sheet; read-only without it. */
  onOpenSoulsSheet?: () => void
}

export function PebbleDetailSoulsGrid({
  souls,
  marks,
  onOpenSoulsSheet,
}: PebbleDetailSoulsGridProps) {
  const t = useTranslations("pebble.peek")
  if (souls.length === 0) return null

  const cell = "flex w-full items-center gap-3 rounded-2xl border border-border/60 px-3 py-2.5 text-left"

  return (
    <ul className="grid grid-cols-2 gap-3" role="list">
      {souls.map((soul) => {
        const mark = marks.find((m) => m.id === soul.glyph_id)
        const content = (
          <>
            <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-muted/60">
              <SoulGlyphThumbnail
                mark={mark}
                className="size-7 text-foreground"
              />
            </span>
            <span className="line-clamp-1 text-sm font-medium text-foreground">
              {soul.name}
            </span>
          </>
        )
        return (
          <li key={soul.id}>
            {onOpenSoulsSheet ? (
              <button
                type="button"
                onClick={onOpenSoulsSheet}
                aria-label={t("editSoulAria", { name: soul.name })}
                className={cn(cell, "transition-colors hover:bg-surface active:scale-[0.98] outline-none focus-visible:ring-2 focus-visible:ring-ring")}
              >
                {content}
              </button>
            ) : (
              <div className={cell}>{content}</div>
            )}
          </li>
        )
      })}
    </ul>
  )
}
