"use client"

import { useTranslations } from "next-intl"
import { useGlyphMarket } from "@/lib/data/useGlyphMarket"
import { MarketGlyphCard } from "@/components/glyphs/MarketGlyphCard"
import { EmptyState } from "@/components/layout/EmptyState"

export function MarketGlyphs() {
  const t = useTranslations("glyphs")
  const tEmpty = useTranslations("glyphs.empty")
  const { glyphs, loading, buy, favourite } = useGlyphMarket()

  if (loading) return <p className="text-sm text-muted-foreground">{t("loading")}</p>
  if (glyphs.length === 0)
    return <EmptyState title={tEmpty("marketTitle")} description={tEmpty("marketDescription")} />

  return (
    <ul className="flex flex-col gap-2">
      {glyphs.map((glyph) => (
        <li key={glyph.id}>
          <MarketGlyphCard glyph={glyph} onBuy={buy} onFavourite={favourite} />
        </li>
      ))}
    </ul>
  )
}
