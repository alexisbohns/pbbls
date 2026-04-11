"use client"

import { createContext, useContext } from "react"
import type { DataProvider, Store } from "@/lib/data/data-provider"

export type DataContextValue = {
  provider: DataProvider | null
  store: Store
  setStore: (store: Store) => void
  loading: boolean
  error: Error | null
  refreshStore: () => void
}

export const DataContext = createContext<DataContextValue | null>(null)

export function useDataProvider(): DataContextValue {
  const ctx = useContext(DataContext)
  if (!ctx) throw new Error("useDataProvider must be used within <DataProvider>")
  return ctx
}
