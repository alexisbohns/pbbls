"use client"

import { useMemo, useState, type ReactNode } from "react"
import { Heart, Tag, Layers } from "lucide-react"
import { useTranslations } from "next-intl"
import type { Collection } from "@/lib/types"
import { DOMAINS } from "@/lib/config/domains"
import { useDomainLocalized } from "@/lib/i18n"
import { SelectableItem } from "@/components/ui/SelectableItem"
import {
  Popover,
  PopoverTrigger,
  PopoverContent,
} from "@/components/ui/popover"
import {
  EmotionPickerSheet,
  useSelectedEmotionDisplay,
} from "@/components/record/EmotionPicker"
import { cn } from "@/lib/utils"
import { useDomainGlyphs, type DomainGlyph as DomainGlyphData } from "@/lib/data/useDomainGlyphs"
import { DomainGlyph } from "@/components/record/DomainGlyph"

// Vertical tile shared by emotion / domain / collection triggers in the
// pebble detail view. Icon on top, label below — matches the design in
// issue #391.
//
// `unset` switches to the Edit-mode empty state from issue #481:
// dashed border + faded, no surface fill. Used when `editing` is true and
// the underlying value is empty.
function TileTrigger({
  icon,
  label,
  ariaLabel,
  unset,
  className,
}: {
  icon: ReactNode
  label: string
  ariaLabel: string
  unset?: boolean
  className?: string
}) {
  return (
    <PopoverTrigger
      className={cn(
        "flex flex-col items-center justify-center gap-2 rounded-2xl px-3 py-4 transition-colors",
        "active:scale-[0.98] outline-none focus-visible:ring-2 focus-visible:ring-ring",
        unset
          ? "border-2 border-dashed border-muted-foreground/30 opacity-60 hover:opacity-100"
          : "bg-surface hover:bg-muted/70",
        className,
      )}
      aria-label={ariaLabel}
    >
      <span className="text-primary" aria-hidden>
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
  editing?: boolean
  className?: string
}

export function EmotionTile({ value, onChange, editing, className }: EmotionTileProps) {
  const t = useTranslations("record.emotion")
  const [pickerOpen, setPickerOpen] = useState(false)
  const selected = useSelectedEmotionDisplay(value || undefined)
  const unset = editing && !selected

  return (
    <>
      <button
        type="button"
        onClick={() => setPickerOpen(true)}
        aria-label={
          selected ? t("selectedAria", { name: selected.name }) : t("pickAria")
        }
        className={cn(
          "flex flex-col items-center justify-center gap-2 rounded-2xl px-3 py-4 transition-colors",
          "active:scale-[0.98] outline-none focus-visible:ring-2 focus-visible:ring-ring",
          unset
            ? "border-2 border-dashed border-muted-foreground/30 opacity-60 hover:opacity-100"
            : "bg-surface hover:bg-muted/70",
          className,
        )}
      >
        <span className="text-muted-foreground" aria-hidden>
          {selected ? (
            <span className="text-lg leading-none">{selected.emoji}</span>
          ) : (
            <Heart className="size-5" />
          )}
        </span>
        <span className="text-sm font-medium text-foreground line-clamp-1">
          {selected ? selected.name : t("label")}
        </span>
      </button>
      <EmotionPickerSheet
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        value={value || undefined}
        onChange={(id) => onChange(id ?? "")}
      />
    </>
  )
}

// ---------------------------------------------------------------------------
// Domain tile
// ---------------------------------------------------------------------------

type DomainTileProps = {
  value: string[]
  onChange: (ids: string[]) => void
  editing?: boolean
  className?: string
}

function DomainOption({
  domain,
  glyph,
  selected,
  onSelect,
}: {
  domain: (typeof DOMAINS)[number]
  glyph?: DomainGlyphData
  selected: boolean
  onSelect: () => void
}) {
  const { name, label } = useDomainLocalized(domain)
  return (
    <SelectableItem selected={selected} onSelect={onSelect}>
      <span className="flex items-center gap-2">
        {glyph ? (
          <DomainGlyph strokes={glyph.strokes} viewBox={glyph.viewBox} className="size-6 shrink-0" />
        ) : null}
        <span className="flex flex-col items-start">
          <span>{name}</span>
          <span className="text-xs text-muted-foreground">{label}</span>
        </span>
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

export function DomainTile({ value, onChange, editing, className }: DomainTileProps) {
  const t = useTranslations("record.domain")
  const domainGlyphs = useDomainGlyphs()
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
        unset={editing && selectedDomains.length === 0}
        className={className}
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
            glyph={domainGlyphs?.get(domain.slug)}
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
  editing?: boolean
  className?: string
}

export function CollectionTile({
  value,
  onChange,
  collections,
  editing,
  className,
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
        unset={editing && selected.length === 0}
        className={className}
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
