"use client"

import type { RecordStepProps } from "@/components/record/types"

export function StepName({ data }: RecordStepProps) {
  return (
    <fieldset className="space-y-4">
      <legend className="text-lg font-semibold">What happened?</legend>

      {data.name && (
        <p className="text-muted-foreground">{data.name}</p>
      )}
    </fieldset>
  )
}
