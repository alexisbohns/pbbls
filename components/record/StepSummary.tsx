"use client"

import type { RecordStepProps } from "@/components/record/types"
import { ReviewSummary } from "@/components/record/ReviewSummary"

export function StepSummary({ data }: RecordStepProps) {
  return (
    <fieldset className="space-y-6">
      <legend className="text-lg font-semibold">Summary</legend>
      <p className="text-sm text-muted-foreground">
        Review your pebble before saving.
      </p>

      <ReviewSummary data={data} />
    </fieldset>
  )
}
