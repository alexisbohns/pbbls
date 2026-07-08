"use client"

import { useCallback } from "react"
import { useTranslations } from "next-intl"
import { useDomainLocalized } from "@/lib/i18n"
import { cn } from "@/lib/utils"
import { useDomains, type DomainRow } from "@/lib/data/useDomains"
import { DomainGlyph } from "@/components/record/DomainGlyph"

type DomainPickerProps = {
  value: string[]
  onChange: (ids: string[]) => void
}

function DomainTile({ domain, selected, onToggle }: {
  domain: DomainRow
  selected: boolean
  onToggle: () => void
}) {
  const { name, label } = useDomainLocalized(domain)
  return (
    <li>
      <button
        type="button"
        aria-pressed={selected}
        onClick={onToggle}
        className={cn(
          "flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm transition-all duration-100 active:scale-[0.97] outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
          selected
            ? "bg-primary/10 text-primary ring-2 ring-primary"
            : "bg-muted/50 hover:bg-muted",
        )}
      >
        {domain.glyph ? (
          <DomainGlyph
            strokes={domain.glyph.strokes}
            viewBox={domain.glyph.viewBox}
            className="size-8 shrink-0"
          />
        ) : null}
        <span className="flex min-w-0 flex-col items-start">
          <span className="font-medium">{name}</span>
          <span
            className={cn(
              "text-xs",
              selected ? "text-primary/70" : "text-muted-foreground",
            )}
          >
            {label}
          </span>
        </span>
      </button>
    </li>
  )
}

export function DomainPicker({ value, onChange }: DomainPickerProps) {
  const t = useTranslations("record.domain")
  const { rows } = useDomains()

  const toggle = useCallback(
    (id: string) => {
      onChange(
        value.includes(id) ? value.filter((v) => v !== id) : [...value, id],
      )
    },
    [value, onChange],
  )

  return (
    <fieldset>
      <legend className="text-sm font-medium">{t("pickerLabel")}</legend>
      <ul
        role="group"
        aria-label={t("pickerAria")}
        className="mt-2 grid grid-cols-1 gap-2 sm:grid-cols-2"
      >
        {rows.map((domain) => (
          <DomainTile
            key={domain.id}
            domain={domain}
            selected={value.includes(domain.id)}
            onToggle={() => toggle(domain.id)}
          />
        ))}
      </ul>
    </fieldset>
  )
}
