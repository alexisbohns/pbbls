"use client"

import type { RecordStepProps } from "@/components/record/types"

export function StepDescription({ data }: RecordStepProps) {
  return (
    <fieldset className="space-y-4">
      <legend className="text-lg font-semibold">What do you want to remember?</legend>

      {data.description && (
        <p className="text-muted-foreground">{data.description}</p>
      )}
    </fieldset>
  )
}
