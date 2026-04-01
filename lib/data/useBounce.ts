"use client"

import { useDataProvider } from "@/lib/data/provider-context"

export function useBounce() {
  const { store, loading } = useDataProvider()

  return {
    bounce: store.bounce,
    bounceWindow: store.bounce_window,
    loading,
  }
}
