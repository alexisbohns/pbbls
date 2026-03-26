"use client"

import { createContext, useContext, type Dispatch, type SetStateAction } from "react"
import type { DataProvider, Store } from "@/lib/data/data-provider"

// ---------------------------------------------------------------------------
// Context value — typed against the DataProvider interface so a future
// Supabase implementation can be swapped in without touching hooks or UI.
// ---------------------------------------------------------------------------

export type DataContextValue = {
  /**
   * Always a concrete DataProvider — never null.
   * Typed against the interface so a future Supabase implementation can be
   * swapped in without touching hooks.
   */
  provider: DataProvider
  store: Store
  setStore: Dispatch<SetStateAction<Store>>
  /** Kept in the API for spec compliance; currently always false. */
  loading: boolean
}

export const DataContext = createContext<DataContextValue | null>(null)

/**
 * Consume the data context.
 * Must be called inside a component tree wrapped by <DataProvider>.
 */
export function useDataProvider(): DataContextValue {
  const ctx = useContext(DataContext)
  if (!ctx) throw new Error("useDataProvider must be used within <DataProvider>")
  return ctx
}
