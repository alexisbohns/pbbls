"use client"

import { useDataProvider } from "@/lib/data/provider-context"
import type { UpdatePebbleInput } from "@/lib/data/data-provider"
import type { Pebble } from "@/lib/types"

export function usePebble(id: string) {
  const { provider, store, setStore, loading } = useDataProvider()

  // Derive the single pebble from the shared store snapshot — no extra
  // provider call needed; re-renders whenever any sibling mutation fires.
  const pebble = store.pebbles.find((p) => p.id === id)

  const updatePebble = async (
    input: UpdatePebbleInput,
  ): Promise<Pebble> => {
    if (!provider) throw new Error("Not authenticated")
    const updated = await provider.updatePebble(id, input)
    setStore(provider.getStore())
    return updated
  }

  return {
    pebble,
    loading,
    updatePebble,
  }
}
