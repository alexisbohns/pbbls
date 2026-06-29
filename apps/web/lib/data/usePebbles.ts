"use client"

import { notifyKarma } from "@/lib/activity/karma-activity"
import { useDataProvider } from "@/lib/data/provider-context"
import type { CreatePebbleInput, UpdatePebbleInput } from "@/lib/data/data-provider"
import type { Pebble, PebbleSnap } from "@/lib/types"

export function usePebbles() {
  const { provider, store, setStore, loading } = useDataProvider()

  const addPebble = async (input: CreatePebbleInput): Promise<Pebble> => {
    if (!provider) throw new Error("Not authenticated")
    const before = provider.getStore().karma
    const pebble = await provider.createPebble(input)
    const after = provider.getStore()
    setStore(after)
    const delta = after.karma - before
    if (delta > 0) notifyKarma(delta, "pebble_created")
    return pebble
  }

  const updatePebble = async (id: string, input: UpdatePebbleInput): Promise<Pebble> => {
    if (!provider) throw new Error("Not authenticated")
    const before = provider.getStore().karma
    const pebble = await provider.updatePebble(id, input)
    const after = provider.getStore()
    setStore(after)
    const delta = after.karma - before
    if (delta > 0) notifyKarma(delta, "pebble_enriched")
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
    updatePebble,
    removePebble,
    uploadSnap,
  }
}
