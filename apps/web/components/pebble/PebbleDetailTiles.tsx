"use client"

import { useMemo, useState, type ReactNode } from "react"
import { Heart, Tag, Layers } from "lucide-react"
import { useTranslations } from "next-intl"
import type { Collection } from "@/lib/types"
import { EMOTIONS } from "@/lib/config/emotions"
import { DOMAINS } from "@/lib/config/domains"
import { useEmotionLocalized, useDomainLocalized } from "@/lib/i18n"
import { SelectableItem } from "@/components/ui/SelectableItem"
import { SearchableList } from "@/components/ui/SearchableList"
import {
  Popover,
  PopoverTrigger,
  PopoverContent,
} from "@/components/ui/popover"
import { cn } from "@/lib/utils"

// Vertical tile shared by emotion / domain / collection triggers in the
// pebble detail view. Icon on top, label below — matches the design in
// issue #391.
function TileTrigger({
  icon,
  label,
  ariaLabel,
}: {
  icon: ReactNode
  label: string
  ariaLabel: string
}) {
  return (
    <PopoverTrigger
      className={cn(
        "flex flex-col items-center justify-center gap-2 rounded-2xl border border-border/60 bg-muted/40 px-3 py-4 transition-colors",
        "hover:bg-muted/70 active:scale-[0.98] outline-none focus-visible:ring-2 focus-visible:ring-ring",
      )}
      aria-label={ariaLabel}
    >
      <span className="text-muted-foreground" aria-hidden>
        {icon}
      </span>
      <span className="text-sm font-medium text-foreground line-clamp-1">
        {label}
      </span>
    </PopoverTrigger>
  )
}

// ---------------------------------------------------------------------------
// Emotion tile
// ---------------------------------------------------------------------------

type EmotionTileProps = {
  value: string
  onChange: (id: string) => void
}

function EmotionOption({
  emotion,
  selected,
  onSelect,
}: {
  emotion: (typeof EMOTIONS)[number]
  selected: boolean
  onSelect: () => void
}) {
  const { name } = useEmotionLocalized(emotion)
  return (
    <SelectableItem selected={selected} onSelect={onSelect}>
      <span
        className="size-3 shrink-0 rounded-full"
        style={{ backgroundColor: emotion.color }}
        aria-hidden
      />
      {name}
    </SelectableItem>
  )
}

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

export function EmotionTile({ value, onChange }: EmotionTileProps) {
  const [query, setQuery] = useState("")
  const t = useTranslations("record.emotion")
  const selected = EMOTIONS.find((e) => e.id === value)
  const fallback = { slug: "", name: "", label: "" }
  const localized = useEmotionLocalized(selected ?? fallback)
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
    <Popover
      onOpenChange={(open) => {
        if (!open) setQuery("")
      }}
    >
      <TileTrigger
        icon={<Heart className="size-5" />}
        label={selected ? localized.name : t("label")}
        ariaLabel={
          selected ? t("selectedAria", { name: localized.name }) : t("pickAria")
        }
      />
      <PopoverContent align="center" className="min-w-[220px] p-2">
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

// ---------------------------------------------------------------------------
// Domain tile
// ---------------------------------------------------------------------------

type DomainTileProps = {
  value: string[]
  onChange: (ids: string[]) => void
}

function DomainOption({
  domain,
  selected,
  onSelect,
}: {
  domain: (typeof DOMAINS)[number]
  selected: boolean
  onSelect: () => void
}) {
  const { name, label } = useDomainLocalized(domain)
  return (
    <SelectableItem selected={selected} onSelect={onSelect}>
      <span className="flex flex-col items-start">
        <span>{name}</span>
        <span className="text-xs text-muted-foreground">{label}</span>
      </span>
    </SelectableItem>
  )
}

function useLocalizedDomainMap(): Map<string, string> {
  const t = useTranslations() as unknown as {
    (key: string): string
    has(key: string): boolean
  }
  return useMemo(() => {
    const map = new Map<string, string>()
    for (const d of DOMAINS) {
      const key = `domain.${d.slug}.name`
      map.set(d.slug, t.has(key) ? t(key) : d.name)
    }
    return map
  }, [t])
}

export function DomainTile({ value, onChange }: DomainTileProps) {
  const t = useTranslations("record.domain")
  const localizedNames = useLocalizedDomainMap()
  const selectedDomains = DOMAINS.filter((d) => value.includes(d.id))
  const selectedNames = useMemo(
    () => selectedDomains.map((d) => localizedNames.get(d.slug) ?? d.name),
    [selectedDomains, localizedNames],
  )

  const tileLabel =
    selectedDomains.length === 0
      ? t("label")
      : selectedDomains.length === 1
        ? selectedNames[0]
        : selectedNames.join(", ")

  const toggle = (id: string) => {
    onChange(value.includes(id) ? value.filter((d) => d !== id) : [...value, id])
  }

  return (
    <Popover>
      <TileTrigger
        icon={<Tag className="size-5" />}
        label={tileLabel}
        ariaLabel={
          selectedDomains.length > 0
            ? t("selectedAria", { names: selectedNames.join(", ") })
            : t("pickAria")
        }
      />
      <PopoverContent align="center" className="min-w-[200px]">
        {DOMAINS.map((domain) => (
          <DomainOption
            key={domain.id}
            domain={domain}
            selected={value.includes(domain.id)}
            onSelect={() => toggle(domain.id)}
          />
        ))}
      </PopoverContent>
    </Popover>
  )
}

// ---------------------------------------------------------------------------
// Collection tile
// ---------------------------------------------------------------------------

type CollectionTileProps = {
  value: string[]
  onChange: (ids: string[]) => void
  collections: Collection[]
}

export function CollectionTile({
  value,
  onChange,
  collections,
}: CollectionTileProps) {
  const t = useTranslations("record.collection")
  const selected = collections.filter((c) => value.includes(c.id))
  const selectedNames = selected.map((c) => c.name)

  const tileLabel =
    selected.length === 0
      ? t("label")
      : selected.length === 1
        ? selectedNames[0]
        : selectedNames.join(", ")

  const toggle = (id: string) => {
    onChange(value.includes(id) ? value.filter((c) => c !== id) : [...value, id])
  }

  return (
    <Popover>
      <TileTrigger
        icon={<Layers className="size-5" />}
        label={tileLabel}
        ariaLabel={
          selected.length > 0
            ? t("selectedAria", { names: selectedNames.join(", ") })
            : t("pickAria")
        }
      />
      <PopoverContent align="center" className="min-w-[200px]">
        {collections.length === 0 ? (
          <p className="px-2 py-4 text-center text-sm text-muted-foreground">
            {t("empty")}
          </p>
        ) : (
          collections.map((coll) => (
            <SelectableItem
              key={coll.id}
              selected={value.includes(coll.id)}
              onSelect={() => toggle(coll.id)}
            >
              {coll.name}
            </SelectableItem>
          ))
        )}
      </PopoverContent>
    </Popover>
  )
}
