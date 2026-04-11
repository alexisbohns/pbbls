"use client"

import { useDataProvider } from "@/lib/data/provider-context"
import type { UpdateSoulInput } from "@/lib/data/data-provider"
import type { Soul } from "@/lib/types"

export function useSoul(id: string) {
  const { provider, store, setStore, loading } = useDataProvider()

  const soul = store.souls.find((s) => s.id === id)

  const updateSoul = async (input: UpdateSoulInput): Promise<Soul> => {
    if (!provider) throw new Error("Not authenticated")
    const updated = await provider.updateSoul(id, input)
    setStore(provider.getStore())
    return updated
  }

  return {
    soul,
    loading,
    updateSoul,
  }
}
