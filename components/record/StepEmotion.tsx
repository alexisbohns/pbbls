"use client"

import type { RecordStepProps } from "@/components/record/types"
import { EmotionPicker } from "@/components/record/EmotionPicker"

export function StepEmotion({ data, onUpdate }: RecordStepProps) {
  return (
    <fieldset className="space-y-6">
      <legend className="text-lg font-semibold">Emotion</legend>
      <p className="text-sm text-muted-foreground">
        What emotion best describes this moment?
      </p>

      <EmotionPicker
        value={data.emotion_id}
        onChange={(emotion_id) => onUpdate({ emotion_id })}
      />
    </fieldset>
  )
}
