"use client"

import type { RecordStepProps } from "@/components/record/RecordStepper"
import { SoulPicker } from "@/components/record/SoulPicker"
import { DomainPicker } from "@/components/record/DomainPicker"

export function RecordStep2({ data, onUpdate }: RecordStepProps) {
  return (
    <fieldset className="space-y-6">
      <legend className="text-lg font-semibold">Souls & Domains</legend>
      <p className="text-sm text-muted-foreground">
        Who was involved? Which life domains does this touch?
      </p>

      <SoulPicker
        value={data.soul_ids}
        onChange={(soul_ids) => onUpdate({ soul_ids })}
      />

      <DomainPicker
        value={data.domain_ids}
        onChange={(domain_ids) => onUpdate({ domain_ids })}
      />
    </fieldset>
  )
}
