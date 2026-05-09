"use client"

import { useCallback } from "react"
import { useTranslations } from "next-intl"
import { DOMAINS, type Domain } from "@/lib/config/domains"
import { useDomainLocalized } from "@/lib/i18n"
import { cn } from "@/lib/utils"

type DomainPickerProps = {
  value: string[]
  onChange: (ids: string[]) => void
}

function DomainTile({ domain, selected, onToggle }: {
  domain: Domain
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
          "flex w-full flex-col items-start rounded-lg px-3 py-2 text-sm transition-all duration-100 active:scale-[0.97] outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
          selected
            ? "bg-primary/10 text-primary ring-2 ring-primary"
            : "bg-muted/50 hover:bg-muted",
        )}
      >
        <span className="font-medium">{name}</span>
        <span
          className={cn(
            "text-xs",
            selected ? "text-primary/70" : "text-muted-foreground",
          )}
        >
          {label}
        </span>
      </button>
    </li>
  )
}

export function DomainPicker({ value, onChange }: DomainPickerProps) {
  const t = useTranslations("record.domain")

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
        {DOMAINS.map((domain) => (
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
