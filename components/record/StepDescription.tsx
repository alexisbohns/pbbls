"use client"

import type { RecordStepProps } from "@/components/record/types"

export function StepDescription({ data, onUpdate }: RecordStepProps) {
  return (
    <fieldset className="space-y-6">
      <legend className="text-lg font-semibold">Description</legend>
      <p className="text-sm text-muted-foreground">
        Add some details if you want.
      </p>

      <div>
        <label htmlFor="pebble-description" className="sr-only">
          Pebble description
        </label>
        <textarea
          id="pebble-description"
          placeholder="A bit more context…"
          value={data.description}
          onChange={(e) => onUpdate({ description: e.target.value })}
          className="w-full resize-none rounded-lg border border-input bg-transparent px-3 py-2 text-sm outline-none field-sizing-content min-h-20 focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
          autoFocus
        />
      </div>
    </fieldset>
  )
}
