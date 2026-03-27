"use client"

import type { RecordStepProps } from "@/components/record/RecordStepper"
import { TimePicker } from "@/components/record/TimePicker"
import { IntensityPicker } from "@/components/record/IntensityPicker"
import { PositivenessPicker } from "@/components/record/PositivenessPicker"
import { EmotionPicker } from "@/components/record/EmotionPicker"

export function RecordStep1({ data, onUpdate }: RecordStepProps) {
  return (
    <fieldset className="space-y-6">
      <legend className="text-lg font-semibold">Time, Intensity & Emotion</legend>
      <p className="text-sm text-muted-foreground">
        When did this happen? How intense was it? What emotion best describes it?
      </p>

      <TimePicker
        value={data.happened_at}
        onChange={(happened_at) => onUpdate({ happened_at })}
      />

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

      <EmotionPicker
        value={data.emotion_id}
        onChange={(emotion_id) => onUpdate({ emotion_id })}
      />
    </fieldset>
  )
}
