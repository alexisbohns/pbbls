"use client"

import { CARD_TYPES } from "@/lib/config"
import type { RecordStepProps } from "@/components/record/types"

type StepCardFillerProps = RecordStepProps & {
  cardTypeId: string
}

export function StepCardFiller({ data, cardTypeId }: StepCardFillerProps) {
  const cardType = CARD_TYPES.find((ct) => ct.id === cardTypeId)
  const currentValue = data.cards.find((c) => c.species_id === cardTypeId)?.value ?? ""

  return (
    <fieldset className="space-y-4">
      <legend className="text-lg font-semibold">{cardType?.name ?? "Card"}</legend>
      <p className="text-sm text-muted-foreground">
        {cardType?.prompt}
      </p>

      {currentValue && (
        <p className="text-muted-foreground">{currentValue}</p>
      )}
    </fieldset>
  )
}
