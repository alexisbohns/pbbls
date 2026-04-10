"use client"

import { useDataProvider } from "@/lib/data/provider-context"

export function useCollection(id: string) {
  const { store, loading } = useDataProvider()

  const collection = store.collections.find((c) => c.id === id)

  return {
    collection,
    loading,
  }
}
