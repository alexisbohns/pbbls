"use client"

import { useMemo, useState } from "react"
import { Plus, X } from "lucide-react"
import { useTranslations } from "next-intl"
import type { Mark, Soul } from "@/lib/types"
import { SearchableList } from "@/components/ui/SearchableList"
import { SoulGlyphThumbnail } from "@/components/souls/SoulGlyphThumbnail"
import { cn } from "@/lib/utils"

type SoulPickerContentProps = {
  souls: Soul[]
  marks: Mark[]
  selectedIds: string[]
  onToggle: (id: string) => void
  /** Creates a soul from the typed name and selects it. */
  onAddSoul: (name: string) => Promise<void>
}

function SoulOption({ soul, mark, selected, muted, onSelect }: {
  soul: Soul
  mark: Mark | undefined
  selected: boolean
  muted: boolean
  onSelect: () => void
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={selected}
      aria-label={soul.name}
      className={cn(
        "flex w-full flex-col items-center gap-1.5 rounded-xl p-2 text-center outline-none transition-colors transition-opacity hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring",
        muted && "opacity-50 hover:opacity-100",
      )}
    >
      <span className="grid aspect-square w-full place-items-center rounded-lg bg-muted/60 p-2.5">
        <SoulGlyphThumbnail
          mark={mark}
          className="size-full"
          strokeClassName={selected ? "text-primary" : "text-foreground"}
        />
      </span>
      <span
        className={cn(
          "line-clamp-1 w-full text-xs text-foreground",
          selected && "font-medium text-primary",
        )}
      >
        {soul.name}
      </span>
    </button>
  )
}

/**
 * The searchable soul grid, with the selected chips above it and the
 * create-from-the-query row below.
 *
 * Multi-select throughout: a tap toggles and never commits the step, because
 * dropping multi-soul tagging to make every step of the flow uniform would be a
 * real capability loss against the composer. Presentation only — `SoulsSheet`
 * wraps it with a Done affordance, the flow step with its Skip / Done button.
 */
export function SoulPickerContent({
  souls,
  marks,
  selectedIds,
  onToggle,
  onAddSoul,
}: SoulPickerContentProps) {
  const [query, setQuery] = useState("")
  const t = useTranslations("record.souls")

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

  return (
    <SearchableList
      query={query}
      onQueryChange={setQuery}
      placeholder={t("searchPlaceholder")}
      isEmpty={filtered.length === 0 && !canAddNew}
      emptyMessage={t("empty")}
      onKeyDown={(e) => {
        if (e.key === "Enter" && canAddNew) {
          e.preventDefault()
          void handleAdd()
        }
      }}
    >
      {selectedIds.length > 0 && (
        <ul className="mb-3 flex flex-wrap gap-1.5" role="list" aria-label={t("selectedListAria")}>
          {selectedIds.map((id) => {
            const soul = souls.find((s) => s.id === id)
            if (!soul) return null
            return (
              <li key={id}>
                <button
                  type="button"
                  onClick={() => onToggle(id)}
                  className="inline-flex items-center gap-1 rounded-md bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary"
                  aria-label={t("removeAria", { name: soul.name })}
                >
                  {soul.name}
                  <X className="size-3" aria-hidden />
                </button>
              </li>
            )
          })}
        </ul>
      )}

      <ul role="list" className="grid grid-cols-3 gap-3">
        {filtered.map((soul) => (
          <li key={soul.id}>
            <SoulOption
              soul={soul}
              mark={marks.find((m) => m.id === soul.glyph_id)}
              selected={selectedIds.includes(soul.id)}
              muted={selectedIds.length > 0 && !selectedIds.includes(soul.id)}
              onSelect={() => onToggle(soul.id)}
            />
          </li>
        ))}
      </ul>

      {canAddNew && (
        <button
          type="button"
          onClick={() => void handleAdd()}
          className="flex w-full items-center gap-2 rounded-lg px-2 py-2 text-sm text-muted-foreground outline-none transition-colors hover:bg-muted hover:text-foreground"
        >
          <Plus className="size-4 shrink-0" />
          {t("addNew", { name: query.trim() })}
        </button>
      )}
    </SearchableList>
  )
}
