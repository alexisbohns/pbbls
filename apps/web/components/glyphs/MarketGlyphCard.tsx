"use client"

import { useTranslations } from "next-intl"
import { Heart } from "lucide-react"
import type { MarketGlyph } from "@/lib/types"
import { GlyphPreview } from "@/components/glyphs/GlyphPreview"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { BuyGlyphDialog } from "@/components/glyphs/BuyGlyphDialog"

type MarketGlyphCardProps = {
  glyph: MarketGlyph
  onBuy: (glyph: MarketGlyph) => Promise<void>
  onFavourite: (glyphId: string, value: boolean) => void
}

export function MarketGlyphCard({ glyph, onBuy, onFavourite }: MarketGlyphCardProps) {
  const t = useTranslations("glyphs")
  const tMarket = useTranslations("market")

  return (
    <article className="flex items-center gap-4 rounded-lg border border-border px-4 py-3">
      <GlyphPreview mark={glyph} className="w-14 shrink-0 aspect-square" />
      <div className="min-w-0 flex-1">
        <h3 className="truncate text-sm font-medium">{glyph.name || t("untitled")}</h3>
        <p className="mt-1 text-xs text-muted-foreground">
          {tMarket("price", { amount: glyph.price })}
        </p>
      </div>

      <button
        type="button"
        onClick={() => onFavourite(glyph.id, !glyph.favourited)}
        aria-label={glyph.favourited ? tMarket("unfavourite") : tMarket("favourite")}
        aria-pressed={glyph.favourited}
        className="rounded-full p-2 text-muted-foreground transition hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <Heart className={`size-4 ${glyph.favourited ? "fill-current text-rose-500" : ""}`} />
      </button>

      {glyph.owned ? (
        <Badge variant="secondary">{tMarket("owned")}</Badge>
      ) : (
        <BuyGlyphDialog
          amount={glyph.price}
          onBuy={() => onBuy(glyph)}
          trigger={<Button size="sm">{tMarket("buy")}</Button>}
        />
      )}
    </article>
  )
}
