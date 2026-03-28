"use client"

import type { RecordStepProps } from "@/components/record/types"
import { IntensityPicker } from "@/components/record/IntensityPicker"
import { PositivenessPicker } from "@/components/record/PositivenessPicker"

export function StepIntensity({ data, onUpdate }: RecordStepProps) {
  return (
    <fieldset className="space-y-6">
      <legend className="text-lg font-semibold">Intensity & Positiveness</legend>
      <p className="text-sm text-muted-foreground">
        How intense was it? Was it positive or negative?
      </p>

      <div className="flex flex-wrap gap-6">
        <IntensityPicker
          value={data.intensity}
          onChange={(intensity) => onUpdate({ intensity })}
        />
        <PositivenessPicker
          value={data.positiveness}
          onChange={(positiveness) => onUpdate({ positiveness })}
        />
      </div>
    </fieldset>
  )
}
