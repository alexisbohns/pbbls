import { useMemo } from "react"
import type { Soul } from "@/lib/types"

type ComboboxFilterResult = {
  filteredSouls: Soul[]
  showAddOption: boolean
  optionCount: number
  addOptionIndex: number
  trimmed: string
}

export function useComboboxFilter(
  souls: Soul[],
  value: string[],
  query: string,
): ComboboxFilterResult {
  return useMemo(() => {
    const trimmed = query.trim()
    const lowerQuery = trimmed.toLowerCase()

    const filteredSouls = trimmed
      ? souls.filter((s) => s.name.toLowerCase().includes(lowerQuery))
      : souls

    const exactMatch = souls.some(
      (s) => s.name.toLowerCase() === lowerQuery,
    )
    const showAddOption = trimmed.length > 0 && !exactMatch
    const optionCount = filteredSouls.length + (showAddOption ? 1 : 0)
    const addOptionIndex = filteredSouls.length

    return { filteredSouls, showAddOption, optionCount, addOptionIndex, trimmed }
  }, [souls, query])
}
