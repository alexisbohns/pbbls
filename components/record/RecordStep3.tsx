"use client"

import type { RecordStepProps } from "@/components/record/RecordStepper"
import { CardEditor } from "@/components/record/CardEditor"
import { ReviewSummary } from "@/components/record/ReviewSummary"

export function RecordStep3({ data, onUpdate }: RecordStepProps) {
  return (
    <fieldset className="space-y-6">
      <legend className="text-lg font-semibold">Cards & Review</legend>
      <p className="text-sm text-muted-foreground">
        Add reflective cards and review your pebble before saving.
      </p>

      <CardEditor
        value={data.cards}
        onChange={(cards) => onUpdate({ cards })}
      />

      <ReviewSummary data={data} />
    </fieldset>
  )
}
