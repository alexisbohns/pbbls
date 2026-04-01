"use client"

import { useDataProvider } from "@/lib/data/provider-context"

export function useKarma() {
  const { store, loading } = useDataProvider()

  return {
    karma: store.karma,
    karmaLog: store.karma_log,
    loading,
  }
}
