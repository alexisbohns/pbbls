"use client"

import { useDataProvider } from "@/lib/data/provider-context"
import type { UpdateMarkInput } from "@/lib/data/data-provider"
import type { Mark } from "@/lib/types"

export function useMark(id: string) {
  const { provider, store, setStore, loading } = useDataProvider()

  const mark = store.marks.find((m) => m.id === id)

  const updateMark = async (
    input: UpdateMarkInput,
  ): Promise<Mark> => {
    if (!provider) throw new Error("Not authenticated")
    const updated = await provider.updateMark(id, input)
    setStore(provider.getStore())
    return updated
  }

  return {
    mark,
    loading,
    updateMark,
  }
}
