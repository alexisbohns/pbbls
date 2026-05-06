"use client"

import { useEffect, useState } from "react"
import { createClient } from "@/lib/supabase/client"
import { withTimeout } from "@/lib/utils/with-timeout"

export type EmotionPalette = {
  primary_color: string
  secondary_color: string
  light_color: string
  surface_color: string
}

type PaletteMap = Map<string, EmotionPalette>

type PaletteRow = {
  id: string | null
  primary_color: string | null
  secondary_color: string | null
  light_color: string | null
  surface_color: string | null
}

// Module-level cache. Reference data is read-only and user-agnostic, so a
// single fetch per page load (deduped via the in-flight promise) is enough —
// matches the spec's "fetch-on-mount, in-memory, no persistence" decision.
let cachedMap: PaletteMap | null = null
let inflight: Promise<PaletteMap> | null = null

async function fetchPalettes(): Promise<PaletteMap> {
  const supabase = createClient()
  const { data, error } = await withTimeout(
    supabase
      .from("v_emotions_with_palette")
      .select("id, primary_color, secondary_color, light_color, surface_color"),
    8000,
    "fetch v_emotions_with_palette",
  )
  if (error) throw new Error(error.message)

  const map: PaletteMap = new Map()
  for (const row of (data ?? []) as PaletteRow[]) {
    // Postgres view types every column as `string | null` (NOT NULL doesn't
    // propagate through views). Narrow at the boundary: drop incomplete rows
    // rather than `??`-fallback to legacy emotion.color, since Phase 2 ships
    // `emotions.category_id NOT NULL` so any null here is a real data bug.
    if (
      row.id &&
      row.primary_color &&
      row.secondary_color &&
      row.light_color &&
      row.surface_color
    ) {
      map.set(row.id, {
        primary_color: row.primary_color,
        secondary_color: row.secondary_color,
        light_color: row.light_color,
        surface_color: row.surface_color,
      })
    }
  }
  return map
}

function loadOnce(): Promise<PaletteMap> {
  if (cachedMap) return Promise.resolve(cachedMap)
  if (inflight) return inflight
  inflight = fetchPalettes()
    .then((map) => {
      cachedMap = map
      return map
    })
    .finally(() => {
      inflight = null
    })
  return inflight
}

export function useEmotionPalettes(): {
  paletteByEmotionId: PaletteMap
  loading: boolean
} {
  const [map, setMap] = useState<PaletteMap>(() => cachedMap ?? new Map())
  const [loading, setLoading] = useState<boolean>(() => cachedMap === null)

  useEffect(() => {
    // If the cache was populated by a sibling consumer between this
    // component's render and effect, useState's initializer already missed
    // it — loadOnce resolves immediately in that case, so the .then handler
    // syncs us up without a synchronous setState in the effect body.
    let cancelled = false
    loadOnce()
      .then((next) => {
        if (cancelled) return
        setMap(next)
        setLoading(false)
      })
      .catch((err) => {
        if (cancelled) return
        console.error("[useEmotionPalettes]", err)
        setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  return { paletteByEmotionId: map, loading }
}
