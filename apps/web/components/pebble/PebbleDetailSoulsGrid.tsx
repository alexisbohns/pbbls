"use client"

import { useTranslations } from "next-intl"
import type { Mark, Soul } from "@/lib/types"
import { SoulGlyphThumbnail } from "@/components/souls/SoulGlyphThumbnail"

type PebbleDetailSoulsGridProps = {
  souls: Soul[]
  marks: Mark[]
  onOpenSoulsSheet: () => void
}

export function PebbleDetailSoulsGrid({
  souls,
  marks,
  onOpenSoulsSheet,
}: PebbleDetailSoulsGridProps) {
  const t = useTranslations("pebble.peek")
  if (souls.length === 0) return null

  return (
    <ul className="grid grid-cols-2 gap-3" role="list">
      {souls.map((soul) => {
        const mark = marks.find((m) => m.id === soul.glyph_id)
        return (
          <li key={soul.id}>
            <button
              type="button"
              onClick={onOpenSoulsSheet}
              aria-label={t("editSoulAria", { name: soul.name })}
              className="flex w-full items-center gap-3 rounded-2xl border border-border/60 px-3 py-2.5 text-left transition-colors hover:bg-muted/40 active:scale-[0.98] outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-muted/60">
                <SoulGlyphThumbnail
                  mark={mark}
                  className="size-7 text-foreground"
                />
              </span>
              <span className="line-clamp-1 text-sm font-medium text-foreground">
                {soul.name}
              </span>
            </button>
          </li>
        )
      })}
    </ul>
  )
}
