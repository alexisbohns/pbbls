"use client"

import type { RecordStepProps } from "@/components/record/types"

export function StepDescription({ data, onUpdate }: RecordStepProps) {
  return (
    <fieldset className="space-y-6">
      <legend className="text-lg font-semibold">What do you want to remember?</legend>

      <div>
        <label htmlFor="pebble-description" className="sr-only">
          Pebble description
        </label>
        <textarea
          id="pebble-description"
          placeholder="A bit more context…"
          value={data.description}
          onChange={(e) => onUpdate({ description: e.target.value })}
          className="w-full resize-none rounded-lg border border-input bg-transparent px-3 py-2 text-base outline-none field-sizing-content min-h-20 focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 md:text-sm"
          autoFocus
        />
      </div>
    </fieldset>
  )
}
