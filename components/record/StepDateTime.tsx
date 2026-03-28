"use client"

import type { RecordStepProps } from "@/components/record/types"
import { TimePicker } from "@/components/record/TimePicker"

export function StepDateTime({ data, onUpdate }: RecordStepProps) {
  return (
    <fieldset className="space-y-6">
      <legend className="text-lg font-semibold">Date and time</legend>
      <p className="text-sm text-muted-foreground">
        When did this happen?
      </p>

      <TimePicker
        value={data.happened_at}
        onChange={(happened_at) => onUpdate({ happened_at })}
      />
    </fieldset>
  )
}
