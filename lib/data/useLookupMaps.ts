import { useMemo } from "react"
import type { Soul, Emotion } from "@/lib/types"
import { EMOTIONS } from "@/lib/config"

export function useLookupMaps(souls: Soul[]) {
  const emotionMap = useMemo(
    () => new Map<string, Emotion>(EMOTIONS.map((e) => [e.id, e])),
    [],
  )

  const soulMap = useMemo(
    () => new Map<string, Soul>(souls.map((s) => [s.id, s])),
    [souls],
  )

  return { emotionMap, soulMap }
}
