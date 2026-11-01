"use client"

import { useCallback } from "react"
import { useAuth } from "@/lib/data/auth-context"
import { useDataProvider } from "@/lib/data/provider-context"
import { useKeptRead } from "@/lib/data/useKeptRead"
import { readerTimeZone } from "@/lib/home/timezone"
import type { HomeWrap } from "@/lib/types"

const kept = new Map<string, HomeWrap[]>()

/**
 * The finished months' wraps (#1384 D13), and `reveal(month)`, which unfolds
 * one (`reveal_wrap`) then reads the wraps again. A failed unfold throws.
 */
export function useHomeWraps() {
  const { user } = useAuth()
  const { provider } = useDataProvider()
  const tz = readerTimeZone()
  const read = useKeptRead(kept, user ? `${user.id}|${tz}` : null, (p) => p.getHomeWraps(tz), "home_wraps")
  const { retry } = read

  const reveal = useCallback(
    async (month: string) => {
      if (!provider) return
      try {
        await provider.revealWrap(month, tz)
      } catch (err) {
        console.error("[home] reveal_wrap failed", err)
        throw err
      }
      retry()
    },
    [provider, tz, retry],
  )

  return { ...read, reveal }
}
