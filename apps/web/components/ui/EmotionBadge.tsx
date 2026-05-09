"use client"

import type { Emotion } from "@/lib/config"
import { useEmotionLocalized } from "@/lib/i18n"

type EmotionBadgeProps = {
  emotion: Emotion
  size?: "sm" | "md"
}

export function EmotionBadge({ emotion, size = "sm" }: EmotionBadgeProps) {
  const { name } = useEmotionLocalized(emotion)
  return (
    <span
      className={`rounded-full font-medium ${size === "sm" ? "px-2 py-0.5 text-xs" : "px-2.5 py-0.5 text-sm"}`}
      style={{
        backgroundColor: `${emotion.color}20`,
        color: emotion.color,
      }}
    >
      {name}
    </span>
  )
}
