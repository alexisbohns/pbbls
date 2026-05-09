"use client"

import { useMemo, useState } from "react"
import { useTranslations } from "next-intl"
import { EMOTIONS } from "@/lib/config/emotions"
import { useEmotionLocalized } from "@/lib/i18n"
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

function EmotionOption({ emotion, selected, onSelect }: {
  emotion: typeof EMOTIONS[number]
  selected: boolean
  onSelect: () => void
}) {
  const { name } = useEmotionLocalized(emotion)
  return (
    <SelectableItem selected={selected} onSelect={onSelect}>
      <span
        className="size-3 rounded-full shrink-0"
        style={{ backgroundColor: emotion.color }}
        aria-hidden
      />
      {name}
    </SelectableItem>
  )
}

export function EmotionPopover({ value, onChange }: EmotionPopoverProps) {
  const [query, setQuery] = useState("")
  const t = useTranslations("record.emotion")
  const selectedEmotion = EMOTIONS.find((e) => e.id === value)
  const selectedLocalized = useEmotionLocalized(
    selectedEmotion ?? { slug: "", name: "", label: "" },
  )

  // Match against the localized name so search works in either language.
  const localizedNames = useLocalizedEmotionMap()
  const filtered = useMemo(() => {
    const trimmed = query.trim().toLowerCase()
    if (!trimmed) return EMOTIONS
    return EMOTIONS.filter((e) => {
      const name = localizedNames.get(e.slug) ?? e.name
      return name.toLowerCase().includes(trimmed)
    })
  }, [query, localizedNames])

  return (
    <Popover onOpenChange={(open) => { if (!open) setQuery("") }}>
      <PopoverTrigger
        className={cn(
          "inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-medium transition-colors",
          value
            ? "border border-border bg-background text-foreground"
            : "border border-dashed border-muted-foreground/30 text-muted-foreground hover:border-muted-foreground/50",
        )}
        aria-label={selectedEmotion ? t("selectedAria", { name: selectedLocalized.name }) : t("pickAria")}
      >
        {selectedEmotion ? (
          <>
            <span
              className="size-2.5 rounded-full shrink-0"
              style={{ backgroundColor: selectedEmotion.color }}
              aria-hidden
            />
            {selectedLocalized.name}
          </>
        ) : (
          <>
            <span className="size-2.5 rounded-full shrink-0 border border-dashed border-muted-foreground/30" aria-hidden />
            {t("label")}
          </>
        )}
      </PopoverTrigger>
      <PopoverContent align="start" className="min-w-[200px] p-2">
        <SearchableList
          query={query}
          onQueryChange={setQuery}
          placeholder={t("searchPlaceholder")}
          isEmpty={filtered.length === 0}
          emptyMessage={t("empty")}
        >
          {filtered.map((emotion) => (
            <EmotionOption
              key={emotion.id}
              emotion={emotion}
              selected={value === emotion.id}
              onSelect={() => onChange(emotion.id)}
            />
          ))}
        </SearchableList>
      </PopoverContent>
    </Popover>
  )
}

/**
 * Build a slug → localized name map once per render so search filtering
 * doesn't need to call the catalog hook per option.
 */
function useLocalizedEmotionMap(): Map<string, string> {
  const t = useTranslations() as unknown as {
    (key: string): string
    has(key: string): boolean
  }
  return useMemo(() => {
    const map = new Map<string, string>()
    for (const e of EMOTIONS) {
      const key = `emotion.${e.slug}.name`
      map.set(e.slug, t.has(key) ? t(key) : e.name)
    }
    return map
  }, [t])
}
