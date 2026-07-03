"use client"

import { useMemo } from "react"
import { Compass } from "lucide-react"
import { useTranslations } from "next-intl"
import { DOMAINS, type Domain } from "@/lib/config/domains"
import { useDomainLocalized } from "@/lib/i18n"
import { SelectableItem } from "@/components/ui/SelectableItem"
import { PickerSheet } from "@/components/ui/PickerSheet"
import { SheetTrigger } from "@/components/ui/sheet"
import { cn } from "@/lib/utils"
import { useDomainGlyphs, type DomainGlyph as DomainGlyphData } from "@/lib/data/useDomainGlyphs"
import { DomainGlyph } from "@/components/record/DomainGlyph"

type DomainSheetProps = {
  value: string[]
  onChange: (ids: string[]) => void
}

function DomainOption({ domain, glyph, selected, onSelect }: {
  domain: Domain
  glyph?: DomainGlyphData
  selected: boolean
  onSelect: () => void
}) {
  const { name, label } = useDomainLocalized(domain)
  return (
    <SelectableItem selected={selected} onSelect={onSelect} className="py-2">
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

/**
 * Multi-select domain picker presented in the shared drawer. Toggling a row
 * keeps the sheet open; the header `X` (or a backdrop tap) dismisses it.
 */
export function DomainSheet({ value, onChange }: DomainSheetProps) {
  const t = useTranslations("record.domain")
  const glyphs = useDomainGlyphs()
  const localizedNames = useLocalizedDomainMap()
  const selectedDomains = DOMAINS.filter((d) => value.includes(d.id))
  const selectedNames = useMemo(
    () => selectedDomains.map((d) => localizedNames.get(d.slug) ?? d.name),
    [selectedDomains, localizedNames],
  )

  const toggle = (id: string) => {
    onChange(
      value.includes(id) ? value.filter((d) => d !== id) : [...value, id],
    )
  }

  return (
    <PickerSheet
      title={t("title")}
      closeLabel={t("close")}
      trigger={
        <SheetTrigger
          className={cn(
            "inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-medium transition-colors",
            value.length > 0
              ? "border border-border bg-background text-foreground"
              : "border border-dashed border-muted-foreground/30 text-muted-foreground hover:border-muted-foreground/50",
          )}
          aria-label={value.length > 0 ? t("selectedAria", { names: selectedNames.join(", ") }) : t("pickAria")}
        >
          <Compass className="size-3.5" aria-hidden />
          {selectedDomains.length > 0 ? selectedNames.join(", ") : t("label")}
        </SheetTrigger>
      }
    >
      <div className="flex flex-col gap-0.5">
        {DOMAINS.map((domain) => (
          <DomainOption
            key={domain.id}
            domain={domain}
            glyph={glyphs?.get(domain.slug)}
            selected={value.includes(domain.id)}
            onSelect={() => toggle(domain.id)}
          />
        ))}
      </div>
    </PickerSheet>
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
