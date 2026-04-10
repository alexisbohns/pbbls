import { useMemo } from "react"
import type { Soul, Emotion, Mark } from "@/lib/types"
import { EMOTIONS } from "@/lib/config"

export function useLookupMaps(souls: Soul[], marks: Mark[] = []) {
  const emotionMap = useMemo(
    () => new Map<string, Emotion>(EMOTIONS.map((e) => [e.id, e])),
    [],
  )

  const soulMap = useMemo(
    () => new Map<string, Soul>(souls.map((s) => [s.id, s])),
    [souls],
  )

  const markMap = useMemo(
    () => new Map<string, Mark>(marks.map((m) => [m.id, m])),
    [marks],
  )

  return { emotionMap, soulMap, markMap }
}
