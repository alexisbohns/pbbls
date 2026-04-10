import { useCallback, useState } from "react"
import type { Soul } from "@/lib/types"

type UseComboboxSelectionOptions = {
  value: string[]
  onChange: (ids: string[]) => void
  addSoul: (input: { name: string }) => Promise<Soul>
  filteredSouls: Soul[]
  showAddOption: boolean
  addOptionIndex: number
  trimmed: string
  onQueryClear: () => void
  onActiveReset: () => void
}

export function useComboboxSelection({
  value,
  onChange,
  addSoul,
  filteredSouls,
  showAddOption,
  addOptionIndex,
  trimmed,
  onQueryClear,
  onActiveReset,
}: UseComboboxSelectionOptions) {
  const [isAdding, setIsAdding] = useState(false)

  const toggle = useCallback(
    (id: string) => {
      onChange(
        value.includes(id) ? value.filter((v) => v !== id) : [...value, id],
      )
    },
    [value, onChange],
  )

  const handleAdd = useCallback(async () => {
    if (isAdding || !trimmed) return
    setIsAdding(true)
    try {
      const soul = await addSoul({ name: trimmed })
      onChange([...value, soul.id])
      onQueryClear()
      onActiveReset()
    } finally {
      setIsAdding(false)
    }
  }, [addSoul, isAdding, onChange, onActiveReset, onQueryClear, trimmed, value])

  const activateOption = useCallback(
    (index: number) => {
      if (index === addOptionIndex && showAddOption) {
        void handleAdd()
      } else if (index >= 0 && index < filteredSouls.length) {
        toggle(filteredSouls[index].id)
      }
    },
    [addOptionIndex, filteredSouls, handleAdd, showAddOption, toggle],
  )

  return { toggle, handleAdd, activateOption, isAdding }
}
