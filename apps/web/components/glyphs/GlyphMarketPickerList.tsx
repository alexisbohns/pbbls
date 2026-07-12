"use client"

import { useTranslations } from "next-intl"
import { useGlyphMarket } from "@/lib/data/useGlyphMarket"
import { GlyphPreview } from "@/components/glyphs/GlyphPreview"
import { BuyGlyphDialog } from "@/components/glyphs/BuyGlyphDialog"
import { Button } from "@/components/ui/button"

type GlyphMarketPickerListProps = {
  /** Called with the glyph id right after a successful purchase. */
  onBought: (glyphId: string) => void
}

/**
 * Community tab of the glyph picker: buyable market glyphs (owned ones live
 * under the Owned tab; the caller's own creations are already excluded by
 * listMarketGlyphs). Buying reuses the store's BuyGlyphDialog + karma spend;
 * on success the glyph is handed back so the picker can select it and close.
 */
export function GlyphMarketPickerList({ onBought }: GlyphMarketPickerListProps) {
  const t = useTranslations("glyphs")
  const tMarket = useTranslations("market")
  const tGlyph = useTranslations("record.glyph")
  const { glyphs, loading, buy } = useGlyphMarket()

  const buyable = glyphs.filter((g) => !g.owned)

  if (loading) return <p className="text-sm text-muted-foreground">{t("loading")}</p>
  if (buyable.length === 0) {
    return <p className="text-sm text-muted-foreground">{tGlyph("emptyCommunity")}</p>
  }

  return (
    <ul className="flex flex-col gap-2">
      {buyable.map((glyph) => (
        <li key={glyph.id}>
          <article className="flex items-center gap-4 rounded-lg border border-border px-4 py-3">
            <GlyphPreview mark={glyph} className="w-14 shrink-0 aspect-square" />
            <div className="min-w-0 flex-1">
              <h3 className="truncate text-sm font-medium">{glyph.name || t("untitled")}</h3>
              <p className="mt-1 text-xs text-muted-foreground">
                {tMarket("price", { amount: glyph.price })}
              </p>
            </div>
            <BuyGlyphDialog
              amount={glyph.price}
              onBuy={async () => {
                await buy(glyph)
                onBought(glyph.id)
              }}
              trigger={<Button size="sm">{tMarket("buy")}</Button>}
            />
          </article>
        </li>
      ))}
    </ul>
  )
}
