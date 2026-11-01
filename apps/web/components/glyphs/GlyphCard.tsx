"use client"

import Link from "next/link"
import { useTranslations } from "next-intl"
import type { Mark } from "@/lib/types"
import { useFormatDate } from "@/lib/i18n"
import { GlyphPreview } from "@/components/glyphs/GlyphPreview"

type GlyphCardProps = {
  mark: Mark
}

export function GlyphCard({ mark }: GlyphCardProps) {
  const t = useTranslations("glyphs")
  const formatDate = useFormatDate()
  const created = formatDate(mark.created_at, { dateStyle: "medium" })

  return (
    <article>
      <Link
        href={`/glyphs/${mark.id}`}
        className="flex items-center gap-4 rounded-2xl bg-m3-surface-container px-4 py-3 outline-none transition-colors hover:bg-m3-surface-container-high focus-visible:ring-2 focus-visible:ring-m3-primary motion-reduce:transition-none"
      >
        <GlyphPreview
          mark={mark}
          className="w-14 shrink-0 aspect-square"
        />

        <div className="min-w-0">
          <h3 className="text-sm font-medium truncate">
            {mark.name || t("untitled")}
          </h3>
          <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            <span>{created}</span>
          </div>
        </div>
      </Link>
    </article>
  )
}
