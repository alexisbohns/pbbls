"use client"

import { useTranslations } from "next-intl"
import { useGlyphFavourites } from "@/lib/data/useGlyphFavourites"
import { MarketGlyphCard } from "@/components/glyphs/MarketGlyphCard"
import { EmptyState } from "@/components/layout/EmptyState"

export function FavouriteGlyphs() {
  const t = useTranslations("glyphs")
  const tEmpty = useTranslations("glyphs.empty")
  const { glyphs, loading, favourite } = useGlyphFavourites()

  if (loading) return <p className="text-sm text-muted-foreground">{t("loading")}</p>
  if (glyphs.length === 0)
    return (
      <EmptyState title={tEmpty("favouritesTitle")} description={tEmpty("favouritesDescription")} />
    )

  return (
    <ul className="flex flex-col gap-2">
      {glyphs.map((glyph) => (
        <li key={glyph.id}>
          {/* Favourites are already owned/favourited; Buy still guarded by `owned`. */}
          <MarketGlyphCard glyph={glyph} onBuy={async () => {}} onFavourite={favourite} />
        </li>
      ))}
    </ul>
  )
}
