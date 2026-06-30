"use client"

import { useCallback, useEffect, useState } from "react"
import { useDataProvider } from "@/lib/data/provider-context"
import type { GlyphSubmission } from "@/lib/types"

export function useGlyphSubmissions() {
  const { provider } = useDataProvider()
  const [submissions, setSubmissions] = useState<GlyphSubmission[]>([])
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    if (!provider) return
    setSubmissions(await provider.getMySubmissions())
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

  const submit = useCallback(
    async (glyphId: string) => {
      if (!provider) throw new Error("Not authenticated")
      const created = await provider.submitGlyph(glyphId)
      await refresh()
      return created
    },
    [provider, refresh],
  )

  return { submissions, loading, submit, refresh }
}
