import { POSITIVENESS_SIGNS, POSITIVENESS_LABELS } from "@/lib/config"

type IntensityDotsProps = {
  intensity: 1 | 2 | 3
  size?: "xs" | "sm"
}

export function IntensityDots({ intensity, size = "sm" }: IntensityDotsProps) {
  const filled = "●".repeat(intensity)
  const empty = "○".repeat(3 - intensity)
  return (
    <abbr
      title={`Intensity: ${intensity} of 3`}
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
  const sign = POSITIVENESS_SIGNS[value] ?? "~"
  const label = POSITIVENESS_LABELS[value] ?? "Neutral"
  return (
    <abbr
      title={`Positiveness: ${label}`}
      className={`font-medium no-underline ${size === "xs" ? "text-xs" : "text-sm"}`}
    >
      {sign}
    </abbr>
  )
}
