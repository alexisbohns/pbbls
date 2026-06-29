"use client"

import { useDataProvider } from "@/lib/data/provider-context"
import { notifyKarma } from "@/lib/activity/karma-activity"
import type { UpdatePebbleInput } from "@/lib/data/data-provider"
import type { Pebble, PebbleSnap } from "@/lib/types"

export function usePebble(id: string) {
  const { provider, store, setStore, loading } = useDataProvider()

  // Derive the single pebble from the shared store snapshot — no extra
  // provider call needed; re-renders whenever any sibling mutation fires.
  const pebble = store.pebbles.find((p) => p.id === id)

  const updatePebble = async (
    input: UpdatePebbleInput,
  ): Promise<Pebble> => {
    if (!provider) throw new Error("Not authenticated")
    const before = provider.getStore().karma
    const updated = await provider.updatePebble(id, input)
    const after = provider.getStore()
    setStore(after)
    const delta = after.karma - before
    if (delta > 0) notifyKarma(delta, "pebble_enriched")
    return updated
  }

  const uploadSnap = async (file: File): Promise<PebbleSnap> => {
    if (!provider) throw new Error("Not authenticated")
    return provider.uploadSnap(file)
  }

  const deletePebbleMedia = async (snapId: string): Promise<void> => {
    if (!provider) throw new Error("Not authenticated")
    await provider.deletePebbleMedia(snapId)
    setStore(provider.getStore())
  }

  return {
    pebble,
    loading,
    updatePebble,
    uploadSnap,
    deletePebbleMedia,
  }
}
