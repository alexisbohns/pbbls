"use client"

import { useDataProvider } from "@/lib/data/provider-context"

export function usePebblesCount() {
  const { store, loading } = useDataProvider()

  return {
    pebblesCount: store.pebbles_count,
    loading,
  }
}
