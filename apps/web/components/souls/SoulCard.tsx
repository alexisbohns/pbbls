"use client"

import Link from "next/link"
import { Gem } from "lucide-react"
import { useTranslations } from "next-intl"
import type { Mark, Soul } from "@/lib/types"
import { SoulGlyphThumbnail } from "@/components/souls/SoulGlyphThumbnail"

type SoulCardProps = {
  soul: Soul
  mark: Mark | undefined
  pebbleCount: number
}

export function SoulCard({ soul, mark, pebbleCount }: SoulCardProps) {
  const t = useTranslations("souls")
  return (
    <Link
      href={`/souls/${soul.id}`}
      className="group flex flex-col items-center gap-2 rounded-lg p-2 transition-all duration-100 hover:bg-muted/50 active:scale-[0.98] focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
    >
      <SoulGlyphThumbnail
        mark={mark}
        className="aspect-square w-full text-foreground"
      />
      <span className="text-sm font-medium text-foreground line-clamp-1">
        {soul.name}
      </span>
      <span className="flex items-center gap-1 text-xs text-foreground">
        <Gem
          className="size-3 text-primary"
          aria-hidden="true"
        />
        <span aria-label={t("pebbleCount", { count: pebbleCount })}>
          {pebbleCount}
        </span>
      </span>
    </Link>
  )
}
