"use client"

import { useTranslations } from "next-intl"
import { useGlyphFavourites } from "@/lib/data/useGlyphFavourites"
import { MarketGlyphCard } from "@/components/glyphs/MarketGlyphCard"
import { EmptyState } from "@/components/layout/EmptyState"

export function FavouriteGlyphs() {
  const t = useTranslations("glyphs")
  const tEmpty = useTranslations("glyphs.empty")
  const { glyphs, loading, favourite, buy } = useGlyphFavourites()

  if (loading) return <p className="text-sm text-muted-foreground">{t("loading")}</p>
  if (glyphs.length === 0)
    return (
      <EmptyState title={tEmpty("favouritesTitle")} description={tEmpty("favouritesDescription")} />
    )

  return (
    <ul className="flex flex-col gap-2">
      {glyphs.map((glyph) => (
        <li key={glyph.id}>
          {/* Owned glyphs show an "Owned" badge; favourited-not-owned ones stay
              buyable, so wire the real buy path (MarketGlyphCard gates on `owned`). */}
          <MarketGlyphCard glyph={glyph} onBuy={buy} onFavourite={favourite} />
        </li>
      ))}
    </ul>
  )
}
