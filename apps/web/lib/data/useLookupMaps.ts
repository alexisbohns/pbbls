import { useMemo } from "react"
import type { Soul, Emotion, Mark } from "@/lib/types"
import { EMOTIONS } from "@/lib/config"
import { useDataProvider } from "@/lib/data/provider-context"

export function useLookupMaps(souls: Soul[], marks: Mark[] = []) {
  const { store } = useDataProvider()

  const emotionMap = useMemo(
    () => new Map<string, Emotion>(EMOTIONS.map((e) => [e.id, e])),
    [],
  )

  const soulMap = useMemo(
    () => new Map<string, Soul>(souls.map((s) => [s.id, s])),
    [souls],
  )

  // Include entitled (bought) glyphs so a bought glyph attached to a
  // pebble/soul renders. Keyed by id, so own ∪ entitled dedupes naturally.
  const markMap = useMemo(
    () =>
      new Map<string, Mark>(
        [...marks, ...store.entitledMarks].map((m) => [m.id, m]),
      ),
    [marks, store.entitledMarks],
  )

  return { emotionMap, soulMap, markMap }
}
