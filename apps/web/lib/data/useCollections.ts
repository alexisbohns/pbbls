"use client"

import { useDataProvider } from "@/lib/data/provider-context"
import type { CreateCollectionInput, UpdateCollectionInput } from "@/lib/data/data-provider"
import type { Collection } from "@/lib/types"

export function useCollections() {
  const { provider, store, setStore, loading } = useDataProvider()

  const addCollection = async (
    input: CreateCollectionInput,
  ): Promise<Collection> => {
    if (!provider) throw new Error("Not authenticated")
    const collection = await provider.createCollection(input)
    setStore(provider.getStore())
    return collection
  }

  const updateCollection = async (
    id: string,
    input: UpdateCollectionInput,
  ): Promise<Collection> => {
    if (!provider) throw new Error("Not authenticated")
    const collection = await provider.updateCollection(id, input)
    setStore(provider.getStore())
    return collection
  }

  const removeCollection = async (id: string): Promise<void> => {
    if (!provider) throw new Error("Not authenticated")
    await provider.deleteCollection(id)
    setStore(provider.getStore())
  }

  return {
    collections: store.collections,
    loading,
    addCollection,
    updateCollection,
    removeCollection,
  }
}
