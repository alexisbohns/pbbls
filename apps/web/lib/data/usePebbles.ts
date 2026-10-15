"use client"

import { useDataProvider } from "@/lib/data/provider-context"
import type { PebbleSnap } from "@/lib/types"

export function usePebbles() {
  const { provider, store, setStore, loading } = useDataProvider()

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
    removePebble,
    uploadSnap,
  }
}
