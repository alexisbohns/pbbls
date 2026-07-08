"use client"

import { useMemo, useState } from "react"
import { Compass } from "lucide-react"
import { useTranslations } from "next-intl"
import { useDomainLocalized } from "@/lib/i18n"
import { SelectableItem } from "@/components/ui/SelectableItem"
import { PickerSheet } from "@/components/ui/PickerSheet"
import { SheetTrigger } from "@/components/ui/sheet"
import { cn } from "@/lib/utils"
import { useDomains, type DomainRow } from "@/lib/data/useDomains"
import { DomainGlyph } from "@/components/record/DomainGlyph"

type DomainSheetProps = {
  value: string[]
  onChange: (ids: string[]) => void
}

function DomainOption({ domain, selected, muted, onSelect }: {
  domain: DomainRow
  selected: boolean
  muted: boolean
  onSelect: () => void
}) {
  const { name, label } = useDomainLocalized(domain)
  return (
    <SelectableItem selected={selected} onSelect={onSelect} showCheck={false} muted={muted} className="py-2">
      <span className="flex items-center gap-3">
        {domain.glyph ? (
          <DomainGlyph
            strokes={domain.glyph.strokes}
            viewBox={domain.glyph.viewBox}
            className="size-7 shrink-0"
            strokeClassName={selected ? "text-accent" : "text-foreground"}
          />
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
 * Single-select domain picker presented in the shared drawer. Picking a row
 * commits it and dismisses the sheet (mirrors the emotion picker); re-tapping
 * the selected row clears it. The interface stays array-based (`value`/
 * `onChange` over `string[]`) so the create payload's `domain_ids` is unchanged,
 * but at most one id is ever held.
 */
export function DomainSheet({ value, onChange }: DomainSheetProps) {
  const t = useTranslations("record.domain")
  const { rows } = useDomains()
  const localizedNames = useLocalizedDomainMap(rows)
  const [open, setOpen] = useState(false)
  const selectedDomains = rows.filter((d) => value.includes(d.id))
  const selectedNames = useMemo(
    () => selectedDomains.map((d) => localizedNames.get(d.slug) ?? d.name),
    [selectedDomains, localizedNames],
  )

  const toggle = (id: string) => {
    onChange(value.includes(id) ? [] : [id])
    setOpen(false)
  }

  return (
    <PickerSheet
      open={open}
      onOpenChange={setOpen}
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
        {rows.map((domain) => (
          <DomainOption
            key={domain.id}
            domain={domain}
            selected={value.includes(domain.id)}
            muted={value.length > 0 && !value.includes(domain.id)}
            onSelect={() => toggle(domain.id)}
          />
        ))}
      </div>
    </PickerSheet>
  )
}

function useLocalizedDomainMap(rows: DomainRow[]): Map<string, string> {
  const t = useTranslations() as unknown as {
    (key: string): string
    has(key: string): boolean
  }
  return useMemo(() => {
    const map = new Map<string, string>()
    for (const d of rows) {
      const key = `domain.${d.slug}.name`
      map.set(d.slug, t.has(key) ? t(key) : d.name)
    }
    return map
  }, [rows, t])
}
