"use client"

import type { RecordStepProps } from "@/components/record/types"
import { Input } from "@/components/ui/input"

export function StepName({ data, onUpdate }: RecordStepProps) {
  return (
    <fieldset className="space-y-6">
      <legend className="text-lg font-semibold">Name</legend>
      <p className="text-sm text-muted-foreground">
        Give this pebble a short name.
      </p>

      <div>
        <label htmlFor="pebble-name" className="sr-only">
          Pebble name
        </label>
        <Input
          id="pebble-name"
          type="text"
          placeholder="What happened?"
          value={data.name}
          onChange={(e) => onUpdate({ name: e.target.value })}
          autoFocus
        />
      </div>
    </fieldset>
  )
}
