"use client"

import { useTranslations } from "next-intl"
import { POSITIVENESS_SIGNS } from "@/lib/config"

type IntensityDotsProps = {
  intensity: 1 | 2 | 3
  size?: "xs" | "sm"
}

const POSITIVENESS_KEY: Record<number, "highlight" | "neutral" | "lowlight"> = {
  [-1]: "lowlight",
  [0]: "neutral",
  [1]: "highlight",
}

export function IntensityDots({ intensity, size = "sm" }: IntensityDotsProps) {
  const t = useTranslations("pebble.intensity")
  const filled = "●".repeat(intensity)
  const empty = "○".repeat(3 - intensity)
  return (
    <abbr
      title={t("title", { value: intensity })}
      className={`tracking-wide no-underline ${size === "xs" ? "text-xs" : "text-sm"}`}
    >
      {filled}
      {empty}
    </abbr>
  )
}

type PositivenessIndicatorProps = {
  value: number
  size?: "xs" | "sm"
}

export function PositivenessIndicator({ value, size = "sm" }: PositivenessIndicatorProps) {
  const t = useTranslations("pebble.positiveness")
  const sign = POSITIVENESS_SIGNS[value] ?? "~"
  const labelKey = POSITIVENESS_KEY[value] ?? "neutral"
  const label = t(labelKey)
  return (
    <abbr
      title={t("title", { label })}
      className={`font-medium no-underline ${size === "xs" ? "text-xs" : "text-sm"}`}
    >
      {sign}
    </abbr>
  )
}
