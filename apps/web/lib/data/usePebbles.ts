"use client"

import { notifyKarma } from "@/lib/activity/karma-activity"
import { fireAchievementCheck } from "@/lib/activity/fire-achievement-check"
import { useDataProvider } from "@/lib/data/provider-context"
import type { CreatePebbleInput } from "@/lib/data/data-provider"
import type { Pebble, PebbleSnap } from "@/lib/types"

export function usePebbles() {
  const { provider, store, setStore, loading } = useDataProvider()

  /**
   * `onKarmaEarned` takes over from the karma pill for callers that show the
   * amount themselves — the record flow's success screen puts it on screen, so
   * a capsule over it would be redundant. Omit it and the pill fires as usual.
   */
  const addPebble = async (
    input: CreatePebbleInput,
    options?: { onKarmaEarned?: (delta: number) => void },
  ): Promise<Pebble> => {
    if (!provider) throw new Error("Not authenticated")
    const before = provider.getStore().karma
    const pebble = await provider.createPebble(input)
    const after = provider.getStore()
    setStore(after)
    const delta = after.karma - before
    if (delta > 0) {
      if (options?.onKarmaEarned) options.onKarmaEarned(delta)
      else notifyKarma(delta, "pebble_created")
    }
    fireAchievementCheck(provider)
    return pebble
  }

  const removePebble = async (id: string): Promise<void> => {
    if (!provider) throw new Error("Not authenticated")
    await provider.deletePebble(id)
    setStore(provider.getStore())
  }

  const uploadSnap = async (file: File): Promise<PebbleSnap> => {
    if (!provider) throw new Error("Not authenticated")
    return provider.uploadSnap(file)
  }

  return {
    pebbles: store.pebbles,
    loading,
    addPebble,
    removePebble,
    uploadSnap,
  }
}
