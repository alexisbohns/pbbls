"use client"

import { useMemo, useState } from "react"
import { EMOTIONS } from "@/lib/config/emotions"
import { SelectableItem } from "@/components/ui/SelectableItem"
import { SearchableList } from "@/components/ui/SearchableList"
import {
  Popover,
  PopoverTrigger,
  PopoverContent,
} from "@/components/ui/popover"
import { cn } from "@/lib/utils"

type EmotionPopoverProps = {
  value: string
  onChange: (id: string) => void
}

export function EmotionPopover({ value, onChange }: EmotionPopoverProps) {
  const [query, setQuery] = useState("")
  const selectedEmotion = EMOTIONS.find((e) => e.id === value)

  const filtered = useMemo(() => {
    if (!query.trim()) return EMOTIONS
    const q = query.toLowerCase()
    return EMOTIONS.filter((e) => e.name.toLowerCase().includes(q))
  }, [query])

  return (
    <Popover onOpenChange={(open) => { if (!open) setQuery("") }}>
      <PopoverTrigger
        className={cn(
          "inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-medium transition-colors",
          value
            ? "border border-border bg-background text-foreground"
            : "border border-dashed border-muted-foreground/30 text-muted-foreground hover:border-muted-foreground/50",
        )}
        aria-label={selectedEmotion ? `Emotion: ${selectedEmotion.name}` : "Pick emotion"}
      >
        {selectedEmotion ? (
          <>
            <span
              className="size-2.5 rounded-full shrink-0"
              style={{ backgroundColor: selectedEmotion.color }}
              aria-hidden
            />
            {selectedEmotion.name}
          </>
        ) : (
          <>
            <span className="size-2.5 rounded-full shrink-0 border border-dashed border-muted-foreground/30" aria-hidden />
            Emotion
          </>
        )}
      </PopoverTrigger>
      <PopoverContent align="start" className="min-w-[200px] p-2">
        <SearchableList
          query={query}
          onQueryChange={setQuery}
          placeholder="Search emotions\u2026"
          isEmpty={filtered.length === 0}
          emptyMessage="No emotions found"
        >
          {filtered.map((emotion) => (
            <SelectableItem
              key={emotion.id}
              selected={value === emotion.id}
              onSelect={() => onChange(emotion.id)}
            >
              <span
                className="size-3 rounded-full shrink-0"
                style={{ backgroundColor: emotion.color }}
                aria-hidden
              />
              {emotion.name}
            </SelectableItem>
          ))}
        </SearchableList>
      </PopoverContent>
    </Popover>
  )
}
