"use client"

import type { RecordStepProps } from "@/components/record/types"
import { SoulPicker } from "@/components/record/SoulPicker"

export function StepSouls({ data, onUpdate }: RecordStepProps) {
  return (
    <fieldset className="space-y-6">
      <legend className="sr-only">Souls</legend>
      <p className="text-sm text-muted-foreground">
        Who was involved?
      </p>

      <SoulPicker
        value={data.soul_ids}
        onChange={(soul_ids) => onUpdate({ soul_ids })}
      />
    </fieldset>
  )
}
