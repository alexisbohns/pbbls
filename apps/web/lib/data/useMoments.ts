"use client"

import { notifyKarma } from "@/lib/activity/karma-activity"
import { fireAchievementCheck } from "@/lib/activity/fire-achievement-check"
import { useDataProvider } from "@/lib/data/provider-context"
import type { CompletePebblePayload, SaveMomentPayload } from "@/lib/data/moment-payloads"
import type { PebbleSnap } from "@/lib/types"
import { karmaDelta } from "@/lib/data/moment-karma"

type CompleteOptions = {
  /** `pebble_created` from the Complete flow, `pebble_enriched` from Edit pebble. */
  reason: "pebble_created" | "pebble_enriched"
  /**
   * Takes over from the karma pill for a caller that shows the amount itself
   * (the Complete success screen). Omit it and the pill fires as usual.
   */
  onKarmaEarned?: (delta: number) => void
}

/**
 * The caller's moments (newest first) and the four moment writes (#1050).
 * Writers throw on a refused RPC (`MomentWriteError` for save and complete,
 * map it with `momentErrorKey`); a stale store after a failed reload is not an
 * error.
 */
export function useMoments() {
  const { provider, store, setStore, loading } = useDataProvider()

  const saveMoment = async (payload: SaveMomentPayload): Promise<string> => {
    if (!provider) throw new Error("Not authenticated")
    const id = await provider.saveMoment(payload)
    setStore(provider.getStore())
    return id
  }

  /**
   * Karma is this pebble's own events after the write minus before (R13, the
   * iOS method): `complete_pebble` writes its karma with `ref_id` = the pebble
   * id, so achievement grants and other writes never leak into the amount.
   * Before is 0 for a bare moment. Either read failing shows nothing.
   * Achievements are evaluated after every completion, never after a save.
   */
  const completePebble = async (payload: CompletePebblePayload, options: CompleteOptions): Promise<string> => {
    if (!provider) throw new Error("Not authenticated")
    const existingPebbleId = provider.getStore().moments.find((m) => m.id === payload.moment_id)?.pebble?.id
    const before = existingPebbleId ? await provider.pebbleKarma(existingPebbleId) : 0
    const pebbleId = await provider.completePebble(payload)
    setStore(provider.getStore())
    const after = await provider.pebbleKarma(pebbleId)
    const delta = karmaDelta(before, after)
    if (delta > 0) {
      if (options.onKarmaEarned) options.onKarmaEarned(delta)
      else notifyKarma(delta, options.reason)
    }
    fireAchievementCheck(provider)
    return pebbleId
  }

  const deleteMoment = async (momentId: string): Promise<void> => {
    if (!provider) throw new Error("Not authenticated")
    await provider.deleteMoment(momentId)
    setStore(provider.getStore())
  }

  /** Keyed by the PEBBLE id; the moment stays, bare (R8). */
  const deletePebble = async (pebbleId: string): Promise<void> => {
    if (!provider) throw new Error("Not authenticated")
    await provider.deletePebble(pebbleId)
    setStore(provider.getStore())
  }

  const uploadSnap = async (file: File): Promise<PebbleSnap> => {
    if (!provider) throw new Error("Not authenticated")
    return provider.uploadSnap(file)
  }

  return {
    moments: store.moments,
    loading,
    saveMoment,
    completePebble,
    deleteMoment,
    deletePebble,
    uploadSnap,
  }
}
