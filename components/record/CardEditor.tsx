"use client"

import { Check, ChevronDown } from "lucide-react"
import { CARD_TYPES } from "@/lib/config"
import type { PebbleCard } from "@/lib/types"

type CardEditorProps = {
  value: PebbleCard[]
  onChange: (cards: PebbleCard[]) => void
}

export function CardEditor({ value, onChange }: CardEditorProps) {
  const cardMap = new Map(value.map((c) => [c.species_id, c.value]))

  function handleChange(speciesId: string, text: string) {
    const next = new Map(value.map((c) => [c.species_id, c.value]))
    if (text.trim()) {
      next.set(speciesId, text)
    } else {
      next.delete(speciesId)
    }
    onChange(
      Array.from(next, ([species_id, v]) => ({ species_id, value: v })),
    )
  }

  return (
    <fieldset className="space-y-3">
      <legend className="text-sm font-medium">Reflection cards</legend>
      <p className="text-xs text-muted-foreground">
        Optional — add thoughts to any card.
      </p>

      <div className="space-y-2">
        {CARD_TYPES.map((type) => {
          const filled = cardMap.has(type.id) && cardMap.get(type.id)!.trim() !== ""
          return (
            <details
              key={type.id}
              open={filled || undefined}
              className="group rounded-lg border border-border"
            >
              <summary className="flex cursor-pointer list-none items-center gap-2 px-3 py-2 text-sm font-medium [&::-webkit-details-marker]:hidden">
                <ChevronDown
                  className="size-4 shrink-0 text-muted-foreground transition-transform group-open:rotate-180"
                  aria-hidden="true"
                />
                <span className="flex-1">{type.name}</span>
                {filled && (
                  <Check className="size-4 shrink-0 text-primary" aria-label={`${type.name} card filled`} />
                )}
              </summary>
              <div className="px-3 pb-3">
                <label htmlFor={`card-${type.id}`} className="sr-only">
                  {type.prompt}
                </label>
                <textarea
                  id={`card-${type.id}`}
                  placeholder={type.prompt}
                  value={cardMap.get(type.id) ?? ""}
                  onChange={(e) => handleChange(type.id, e.target.value)}
                  className="w-full resize-none rounded-lg border border-input bg-background px-3 py-2 text-sm outline-none field-sizing-content min-h-20 focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
                />
              </div>
            </details>
          )
        })}
      </div>
    </fieldset>
  )
}
