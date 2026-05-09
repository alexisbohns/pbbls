"use client"

import { useMemo } from "react"
import { Compass } from "lucide-react"
import { useTranslations } from "next-intl"
import { DOMAINS, type Domain } from "@/lib/config/domains"
import { useDomainLocalized } from "@/lib/i18n"
import { SelectableItem } from "@/components/ui/SelectableItem"
import {
  Popover,
  PopoverTrigger,
  PopoverContent,
} from "@/components/ui/popover"
import { cn } from "@/lib/utils"

type DomainPopoverProps = {
  value: string[]
  onChange: (ids: string[]) => void
}

function DomainOption({ domain, selected, onSelect }: {
  domain: Domain
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

export function DomainPopover({ value, onChange }: DomainPopoverProps) {
  const t = useTranslations("record.domain")
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
    <Popover>
      <PopoverTrigger
        className={cn(
          "inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-medium transition-colors",
          value.length > 0
            ? "border border-border bg-background text-foreground"
            : "border border-dashed border-muted-foreground/30 text-muted-foreground hover:border-muted-foreground/50",
        )}
        aria-label={value.length > 0 ? t("selectedAria", { names: selectedNames.join(", ") }) : t("pickAria")}
      >
        <Compass className="size-3.5" aria-hidden />
        {selectedDomains.length > 0
          ? selectedNames.join(", ")
          : t("label")}
      </PopoverTrigger>
      <PopoverContent align="start" className="min-w-[180px]">
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
