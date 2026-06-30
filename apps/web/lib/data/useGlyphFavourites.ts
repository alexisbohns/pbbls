"use client"

import { useCallback, useEffect, useState } from "react"
import { useTranslations } from "next-intl"
import { useDataProvider } from "@/lib/data/provider-context"
import { notifyGlyphPurchased } from "@/lib/activity/glyph-activity"
import type { MarketGlyph } from "@/lib/types"

export function useGlyphFavourites() {
  const { provider, setStore } = useDataProvider()
  const tGlyphs = useTranslations("glyphs")
  const [glyphs, setGlyphs] = useState<MarketGlyph[]>([])
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    if (!provider) return
    setGlyphs(await provider.listFavouriteGlyphs())
    setLoading(false)
  }, [provider])

  useEffect(() => {
    // Defer the setState past the synchronous render boundary (mirrors
    // useWallet.ts) to satisfy react-hooks/set-state-in-effect.
    void (async () => {
      await Promise.resolve()
      await refresh()
    })()
  }, [refresh])

  const favourite = useCallback(
    async (glyphId: string, value: boolean) => {
      if (!provider) return
      await provider.setFavourite(glyphId, value)
      await refresh()
    },
    [provider, refresh],
  )

  // A favourited-but-not-owned glyph is still buyable from the Favourites tab,
  // so it needs a real purchase path — not a no-op. Mirrors useGlyphMarket.buy.
  const buy = useCallback(
    async (glyph: MarketGlyph) => {
      if (!provider) throw new Error("Not authenticated")
      await provider.buyGlyph(glyph.id)
      setStore(provider.getStore())
      notifyGlyphPurchased(glyph.id, glyph.name || tGlyphs("untitled"), glyph.price)
      await refresh()
    },
    [provider, setStore, refresh, tGlyphs],
  )

  return { glyphs, loading, favourite, buy, refresh }
}
