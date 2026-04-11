"use client"

import { useDataProvider } from "@/lib/data/provider-context"
import type { CreateMarkInput, UpdateMarkInput } from "@/lib/data/data-provider"
import type { Mark } from "@/lib/types"

export function useMarks() {
  const { provider, store, setStore, loading } = useDataProvider()

  const addMark = async (input: CreateMarkInput): Promise<Mark> => {
    if (!provider) throw new Error("Not authenticated")
    const mark = await provider.createMark(input)
    setStore(provider.getStore())
    return mark
  }

  const updateMark = async (
    id: string,
    input: UpdateMarkInput,
  ): Promise<Mark> => {
    if (!provider) throw new Error("Not authenticated")
    const mark = await provider.updateMark(id, input)
    setStore(provider.getStore())
    return mark
  }

  const removeMark = async (id: string): Promise<void> => {
    if (!provider) throw new Error("Not authenticated")
    await provider.deleteMark(id)
    setStore(provider.getStore())
  }

  return {
    marks: store.marks,
    loading,
    addMark,
    updateMark,
    removeMark,
  }
}
