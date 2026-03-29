"use client"

import type { RecordStepProps } from "@/components/record/types"
import { EmotionPearl } from "@/components/record/EmotionPearl"
import { EmotionPicker } from "@/components/record/EmotionPicker"
import { EMOTIONS } from "@/lib/config"

export function StepEmotion({ data, onUpdate }: RecordStepProps) {
  const selectedColor = EMOTIONS.find((e) => e.id === data.emotion_id)?.color

  return (
    <fieldset className="flex flex-col items-center space-y-6">
      <legend className="text-lg font-semibold">Emotion</legend>

      <EmotionPearl color={selectedColor} />

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
