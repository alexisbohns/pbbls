"use client"

import { useMemo, useRef, useState } from "react"
import { Plus, Search, X } from "lucide-react"
import { useSouls } from "@/lib/data/useSouls"
import { useComboboxFilter } from "@/lib/hooks/useComboboxFilter"
import { useComboboxKeyboard } from "@/lib/hooks/useComboboxKeyboard"
import { useComboboxSelection } from "@/lib/hooks/useComboboxSelection"
import { cn } from "@/lib/utils"

type SoulPickerProps = {
  value: string[]
  onChange: (ids: string[]) => void
}

export function SoulPicker({ value, onChange }: SoulPickerProps) {
  const { souls, addSoul } = useSouls()
  const [query, setQuery] = useState("")
  const [activeIndex, setActiveIndex] = useState(-1)
  const inputRef = useRef<HTMLInputElement>(null)
  const listboxId = "soul-listbox"

  const { filteredSouls, showAddOption, optionCount, addOptionIndex, trimmed } =
    useComboboxFilter(souls, value, query)

  const clearQuery = () => setQuery("")
  const resetActive = () => setActiveIndex(-1)

  const { toggle, handleAdd, activateOption, isAdding } = useComboboxSelection({
    value,
    onChange,
    addSoul,
    filteredSouls,
    showAddOption,
    addOptionIndex,
    trimmed,
    onQueryClear: clearQuery,
    onActiveReset: resetActive,
  })

  const handleKeyDown = useComboboxKeyboard({
    optionCount,
    activeIndex,
    setActiveIndex,
    showAddOption,
    onActivate: activateOption,
    onAdd: () => void handleAdd(),
    onClear: () => {
      setQuery("")
      setActiveIndex(-1)
    },
  })

  const activeDescendant = useMemo(() => {
    if (activeIndex >= 0 && activeIndex < filteredSouls.length) {
      return `soul-option-${filteredSouls[activeIndex].id}`
    }
    if (activeIndex === addOptionIndex && showAddOption) return "soul-option-add"
    return undefined
  }, [activeIndex, filteredSouls, addOptionIndex, showAddOption])

  const expanded = optionCount > 0
  const selectedSouls = souls.filter((s) => value.includes(s.id))

  return (
    <fieldset>
      <legend className="text-sm font-medium">Souls</legend>

      {/* Selected chips */}
      {selectedSouls.length > 0 && (
        <ul role="list" className="mt-2 flex flex-wrap gap-2" aria-label="Selected souls">
          {selectedSouls.map((soul) => (
            <li
              key={soul.id}
              className="flex items-center gap-1 rounded-full border border-border px-2.5 py-0.5 text-xs font-medium"
            >
              {soul.name}
              <button
                type="button"
                aria-label={`Remove ${soul.name}`}
                onClick={() => toggle(soul.id)}
                className="rounded-full p-0.5 text-muted-foreground hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:outline-none"
              >
                <X className="size-3" aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
      )}

      {/* Search input */}
      <div className="relative mt-2">
        <Search
          className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
          aria-hidden="true"
        />
        <label htmlFor="soul-search" className="sr-only">
          Search or add a soul
        </label>
        <input
          ref={inputRef}
          id="soul-search"
          type="search"
          value={query}
          onChange={(e) => {
            setQuery(e.target.value)
            setActiveIndex(-1)
          }}
          onKeyDown={handleKeyDown}
          placeholder="Search or add a soul\u2026"
          role="combobox"
          aria-autocomplete="list"
          aria-controls={listboxId}
          aria-expanded={expanded}
          aria-activedescendant={activeDescendant}
          className="h-8 w-full rounded-lg border border-input bg-background pl-8 pr-3 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
        />
      </div>

      {/* Listbox */}
      {expanded && (
        <ul
          id={listboxId}
          role="listbox"
          aria-multiselectable="true"
          aria-label="Souls"
          className="mt-1 max-h-48 overflow-y-auto rounded-lg border border-input bg-background"
        >
          {filteredSouls.map((soul, i) => {
            const selected = value.includes(soul.id)
            const active = i === activeIndex
            return (
              <li
                key={soul.id}
                id={`soul-option-${soul.id}`}
                role="option"
                aria-selected={selected}
                onClick={() => toggle(soul.id)}
                onPointerEnter={() => setActiveIndex(i)}
                className={cn(
                  "flex cursor-pointer items-center gap-2 px-3 py-1.5 text-sm",
                  active && "bg-muted",
                  selected && "font-medium",
                )}
              >
                <span
                  className={cn(
                    "flex size-4 shrink-0 items-center justify-center rounded border text-[10px]",
                    selected
                      ? "border-primary bg-primary text-primary-foreground"
                      : "border-input",
                  )}
                  aria-hidden="true"
                >
                  {selected && "✓"}
                </span>
                {soul.name}
              </li>
            )
          })}

          {showAddOption && (
            <li
              id="soul-option-add"
              role="option"
              aria-selected={false}
              onClick={() => void handleAdd()}
              onPointerEnter={() => setActiveIndex(addOptionIndex)}
              className={cn(
                "flex cursor-pointer items-center gap-2 border-t border-input px-3 py-1.5 text-sm text-primary",
                activeIndex === addOptionIndex && "bg-muted",
                isAdding && "pointer-events-none opacity-50",
              )}
            >
              <Plus className="size-4 shrink-0" aria-hidden="true" />
              {isAdding ? "Adding\u2026" : `Add \u201c${trimmed}\u201d`}
            </li>
          )}
        </ul>
      )}

      <p aria-live="polite" className="sr-only">
        {filteredSouls.length} soul{filteredSouls.length !== 1 && "s"} found
        {showAddOption && `, type Enter to add "${trimmed}"`}
      </p>
    </fieldset>
  )
}
