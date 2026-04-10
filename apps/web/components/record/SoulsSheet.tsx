"use client"

import { useMemo, useState } from "react"
import { Plus, X } from "lucide-react"
import type { Soul } from "@/lib/types"
import { SelectableItem } from "@/components/ui/SelectableItem"
import { SearchableList } from "@/components/ui/SearchableList"
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetClose,
} from "@/components/ui/sheet"

type SoulsSheetProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  selectedIds: string[]
  onToggle: (id: string) => void
  souls: Soul[]
  onAddSoul: (name: string) => Promise<void>
}

export function SoulsSheet({
  open,
  onOpenChange,
  selectedIds,
  onToggle,
  souls,
  onAddSoul,
}: SoulsSheetProps) {
  const [query, setQuery] = useState("")

  const filtered = useMemo(() => {
    if (!query.trim()) return souls
    const q = query.toLowerCase()
    return souls.filter((s) => s.name.toLowerCase().includes(q))
  }, [souls, query])

  const canAddNew = useMemo(() => {
    if (!query.trim()) return false
    return !souls.some((s) => s.name.toLowerCase() === query.trim().toLowerCase())
  }, [souls, query])

  const handleAdd = async () => {
    const trimmed = query.trim()
    if (!trimmed) return
    await onAddSoul(trimmed)
    setQuery("")
  }

  const handleOpenChange = (nextOpen: boolean) => {
    onOpenChange(nextOpen)
    if (nextOpen) setQuery("")
  }

  return (
    <Sheet open={open} onOpenChange={handleOpenChange}>
      <SheetContent>
        <SheetHeader>
          <SheetTitle>Souls</SheetTitle>
        </SheetHeader>

        <SearchableList
          query={query}
          onQueryChange={setQuery}
          placeholder="Search souls\u2026"
          isEmpty={filtered.length === 0 && !canAddNew}
          emptyMessage="No souls found"
          onKeyDown={(e) => {
            if (e.key === "Enter" && canAddNew) {
              e.preventDefault()
              void handleAdd()
            }
          }}
        >
          {/* Selected chips */}
          {selectedIds.length > 0 && (
            <ul className="mb-3 flex flex-wrap gap-1.5" role="list" aria-label="Selected souls">
              {selectedIds.map((id) => {
                const soul = souls.find((s) => s.id === id)
                if (!soul) return null
                return (
                  <li key={id}>
                    <button
                      type="button"
                      onClick={() => onToggle(id)}
                      className="inline-flex items-center gap-1 rounded-md bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary"
                      aria-label={`Remove ${soul.name}`}
                    >
                      {soul.name}
                      <X className="size-3" aria-hidden />
                    </button>
                  </li>
                )
              })}
            </ul>
          )}

          {filtered.map((soul) => (
            <SelectableItem
              key={soul.id}
              selected={selectedIds.includes(soul.id)}
              onSelect={() => onToggle(soul.id)}
              className="py-2"
            >
              {soul.name}
            </SelectableItem>
          ))}

          {canAddNew && (
            <button
              type="button"
              onClick={() => void handleAdd()}
              className="flex w-full items-center gap-2 rounded-lg px-2 py-2 text-sm text-muted-foreground outline-none transition-colors hover:bg-muted hover:text-foreground"
            >
              <Plus className="size-4 shrink-0" />
              Add &quot;{query.trim()}&quot;
            </button>
          )}
        </SearchableList>

        <div className="mt-4 flex justify-end">
          <SheetClose aria-label="Done">
            Done
          </SheetClose>
        </div>
      </SheetContent>
    </Sheet>
  )
}
