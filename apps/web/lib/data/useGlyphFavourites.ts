"use client"

import { useCallback, useEffect, useState } from "react"
import { useDataProvider } from "@/lib/data/provider-context"
import type { MarketGlyph } from "@/lib/types"

export function useGlyphFavourites() {
  const { provider } = useDataProvider()
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

  return { glyphs, loading, favourite, refresh }
}
