"use client"

import { CARD_TYPES } from "@/lib/config"
import type { RecordStepProps } from "@/components/record/types"

export function StepCardPicker({ data, onUpdate }: RecordStepProps) {
  const selectedIds = new Set(data.cards.map((c) => c.species_id))

  function toggle(speciesId: string) {
    if (selectedIds.has(speciesId)) {
      onUpdate({ cards: data.cards.filter((c) => c.species_id !== speciesId) })
    } else {
      onUpdate({ cards: [...data.cards, { species_id: speciesId, value: "" }] })
    }
  }

  return (
    <fieldset className="space-y-6">
      <legend className="text-lg font-semibold">Cards</legend>
      <p className="text-sm text-muted-foreground">
        Pick the reflection cards you want to fill.
      </p>

      <ul className="space-y-2" role="group" aria-label="Card types">
        {CARD_TYPES.map((type) => {
          const checked = selectedIds.has(type.id)
          return (
            <li key={type.id}>
              <label
                className={`flex cursor-pointer items-center gap-3 rounded-lg border px-3 py-2 text-sm transition-all duration-100 active:scale-[0.98] ${
                  checked
                    ? "border-primary bg-primary/5"
                    : "border-border"
                }`}
              >
                <input
                  type="checkbox"
                  checked={checked}
                  onChange={() => toggle(type.id)}
                  className="size-4 accent-primary"
                />
                <span className="flex-1">
                  <span className="font-medium">{type.name}</span>
                  <span className="ml-2 text-muted-foreground">{type.prompt}</span>
                </span>
              </label>
            </li>
          )
        })}
      </ul>
    </fieldset>
  )
}
