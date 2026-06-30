"use client"

import { useDataProvider } from "@/lib/data/provider-context"
import type { Mark } from "@/lib/types"

/** Glyphs the user may attach to pebbles/souls: own ∪ entitled (bought). */
export function useUsableGlyphs(): { glyphs: Mark[]; loading: boolean } {
  const { store, loading } = useDataProvider()
  const seen = new Set(store.marks.map((m) => m.id))
  const glyphs = [...store.marks, ...store.entitledMarks.filter((m) => !seen.has(m.id))]
  return { glyphs, loading }
}
