"use client"

import { useDataProvider } from "@/lib/data/provider-context"
import type { CreatePebbleInput, UpdatePebbleInput } from "@/lib/data/data-provider"
import type { Pebble } from "@/lib/types"

export function usePebbles() {
  const { provider, store, setStore, loading } = useDataProvider()

  const addPebble = async (input: CreatePebbleInput): Promise<Pebble> => {
    const pebble = await provider.createPebble(input)
    setStore(provider.getStore())
    return pebble
  }

  const updatePebble = async (
    id: string,
    input: UpdatePebbleInput,
  ): Promise<Pebble> => {
    const pebble = await provider.updatePebble(id, input)
    setStore(provider.getStore())
    return pebble
  }

  const removePebble = async (id: string): Promise<void> => {
    await provider.deletePebble(id)
    setStore(provider.getStore())
  }

  return {
    pebbles: store.pebbles,
    loading,
    addPebble,
    updatePebble,
    removePebble,
  }
}
