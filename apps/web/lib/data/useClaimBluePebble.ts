"use client"

import { useCallback } from "react"
import { presentGrantedAchievements } from "@/lib/activity/fire-achievement-check"
import { useDataProvider } from "@/lib/data/provider-context"

/**
 * The first five's claim (#1384 D12): `claim_blue_pebble()`. A returned row is
 * presented as any granted badge is; an empty result is "not granted", never
 * success (log 2026-10-26). Resolves true only when the pebble was granted;
 * a failed call is logged and thrown.
 */
export function useClaimBluePebble() {
  const { provider } = useDataProvider()
  const claim = useCallback(async (): Promise<boolean> => {
    if (!provider) return false
    let granted
    try {
      granted = await provider.claimBluePebble()
    } catch (err) {
      console.error("[home] claim_blue_pebble failed", err)
      throw err
    }
    if (!granted) return false
    void presentGrantedAchievements(provider, [granted]).catch((err) => {
      console.warn("[home] presenting the blue pebble failed", err)
    })
    return true
  }, [provider])
  return { claim }
}
