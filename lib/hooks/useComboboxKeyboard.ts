import { useCallback } from "react"

type UseComboboxKeyboardOptions = {
  optionCount: number
  activeIndex: number
  setActiveIndex: (index: number | ((prev: number) => number)) => void
  showAddOption: boolean
  onActivate: (index: number) => void
  onAdd: () => void
  onClear: () => void
}

export function useComboboxKeyboard({
  optionCount,
  activeIndex,
  setActiveIndex,
  showAddOption,
  onActivate,
  onAdd,
  onClear,
}: UseComboboxKeyboardOptions) {
  return useCallback(
    (e: React.KeyboardEvent) => {
      if (optionCount === 0) return

      switch (e.key) {
        case "ArrowDown": {
          e.preventDefault()
          setActiveIndex((prev) =>
            prev < optionCount - 1 ? prev + 1 : 0,
          )
          break
        }
        case "ArrowUp": {
          e.preventDefault()
          setActiveIndex((prev) =>
            prev > 0 ? prev - 1 : optionCount - 1,
          )
          break
        }
        case "Enter": {
          e.preventDefault()
          if (activeIndex >= 0) {
            onActivate(activeIndex)
          } else if (showAddOption) {
            onAdd()
          }
          break
        }
        case "Escape": {
          e.preventDefault()
          onClear()
          break
        }
        case "Home": {
          e.preventDefault()
          setActiveIndex(0)
          break
        }
        case "End": {
          e.preventDefault()
          setActiveIndex(optionCount - 1)
          break
        }
      }
    },
    [activeIndex, onActivate, onAdd, onClear, optionCount, setActiveIndex, showAddOption],
  )
}
