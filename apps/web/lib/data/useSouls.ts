"use client"

import { useDataProvider } from "@/lib/data/provider-context"
import type { CreateSoulInput, UpdateSoulInput } from "@/lib/data/data-provider"
import type { Soul } from "@/lib/types"

export function useSouls() {
  const { provider, store, setStore, loading } = useDataProvider()

  const addSoul = async (input: CreateSoulInput): Promise<Soul> => {
    const soul = await provider.createSoul(input)
    setStore(provider.getStore())
    return soul
  }

  const updateSoul = async (
    id: string,
    input: UpdateSoulInput,
  ): Promise<Soul> => {
    const soul = await provider.updateSoul(id, input)
    setStore(provider.getStore())
    return soul
  }

  const removeSoul = async (id: string): Promise<void> => {
    await provider.deleteSoul(id)
    setStore(provider.getStore())
  }

  return {
    souls: store.souls,
    loading,
    addSoul,
    updateSoul,
    removeSoul,
  }
}
