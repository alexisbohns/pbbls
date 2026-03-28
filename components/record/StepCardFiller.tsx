"use client"

import { CARD_TYPES } from "@/lib/config"
import type { RecordStepProps } from "@/components/record/types"

type StepCardFillerProps = RecordStepProps & {
  cardTypeId: string
}

export function StepCardFiller({ data, onUpdate, cardTypeId }: StepCardFillerProps) {
  const cardType = CARD_TYPES.find((ct) => ct.id === cardTypeId)
  const currentValue = data.cards.find((c) => c.species_id === cardTypeId)?.value ?? ""

  function handleChange(text: string) {
    const updatedCards = data.cards.map((c) =>
      c.species_id === cardTypeId ? { ...c, value: text } : c,
    )
    onUpdate({ cards: updatedCards })
  }

  return (
    <fieldset className="space-y-6">
      <legend className="text-lg font-semibold">{cardType?.name ?? "Card"}</legend>
      <p className="text-sm text-muted-foreground">
        {cardType?.prompt}
      </p>

      <div>
        <label htmlFor={`card-${cardTypeId}`} className="sr-only">
          {cardType?.prompt}
        </label>
        <textarea
          id={`card-${cardTypeId}`}
          placeholder={cardType?.prompt}
          value={currentValue}
          onChange={(e) => handleChange(e.target.value)}
          className="w-full resize-none rounded-lg border border-input bg-transparent px-3 py-2 text-sm outline-none field-sizing-content min-h-20 focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
          autoFocus
        />
      </div>
    </fieldset>
  )
}
