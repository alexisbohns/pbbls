"use client"

import { useDomainLocalized } from "@/lib/i18n"
import { SelectableItem } from "@/components/ui/SelectableItem"
import type { DomainRow } from "@/lib/data/useDomains"
import { DomainGlyph } from "@/components/record/DomainGlyph"

type DomainPickerContentProps = {
  domains: DomainRow[]
  /** The chosen domain id, or undefined. At most one is ever selected. */
  selected: string | undefined
  onSelect: (id: string) => void
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
            strokeClassName={selected ? "text-primary" : "text-foreground"}
          />
        ) : null}
        <span className="flex flex-col items-start">
          <span>{name}</span>
          {/* The description is a sentence, so it takes a plain small style —
              never one of the uppercasing label tokens. */}
          <span className="text-xs text-muted-foreground">{label}</span>
        </span>
      </span>
    </SelectableItem>
  )
}

/**
 * The domain list — glyph, name and description per row.
 *
 * Presentation only, shared by `DomainSheet` (which dismisses on pick and lets
 * a second tap clear the choice) and the flow's domain step (which commits and
 * advances). The caller owns what a tap means.
 */
export function DomainPickerContent({ domains, selected, onSelect }: DomainPickerContentProps) {
  return (
    <div className="flex flex-col gap-0.5">
      {domains.map((domain) => (
        <DomainOption
          key={domain.id}
          domain={domain}
          selected={domain.id === selected}
          muted={selected !== undefined && domain.id !== selected}
          onSelect={() => onSelect(domain.id)}
        />
      ))}
    </div>
  )
}
