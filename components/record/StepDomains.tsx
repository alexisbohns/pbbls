"use client"

import type { RecordStepProps } from "@/components/record/types"
import { DomainPicker } from "@/components/record/DomainPicker"

export function StepDomains({ data, onUpdate }: RecordStepProps) {
  return (
    <fieldset className="space-y-6">
      <legend className="sr-only">Domains</legend>
      <p className="text-sm text-muted-foreground">
        Which life domains does this touch?
      </p>

      <DomainPicker
        value={data.domain_ids}
        onChange={(domain_ids) => onUpdate({ domain_ids })}
      />
    </fieldset>
  )
}
