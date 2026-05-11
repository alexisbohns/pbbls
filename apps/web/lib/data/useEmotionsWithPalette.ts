"use client"

import { useEffect, useState } from "react"
import { createClient } from "@/lib/supabase/client"
import { withTimeout } from "@/lib/utils/with-timeout"

export type EmotionWithPalette = {
  id: string
  slug: string
  name: string
  emoji: string
  category_id: string
  category_slug: string
  category_name: string
  primary_color: string
  secondary_color: string
  light_color: string
  surface_color: string
}

type Row = {
  id: string | null
  slug: string | null
  name: string | null
  emoji: string | null
  category_id: string | null
  category_slug: string | null
  category_name: string | null
  primary_color: string | null
  secondary_color: string | null
  light_color: string | null
  surface_color: string | null
}

// Module-level cache — reference data is read-only and user-agnostic. Mirrors
// `useEmotionPalettes`. A second hook (rather than extending the first) keeps
// the palette-only consumers' payload small.
let cached: EmotionWithPalette[] | null = null
let inflight: Promise<EmotionWithPalette[]> | null = null

async function fetchRows(): Promise<EmotionWithPalette[]> {
  const supabase = createClient()
  const { data, error } = await withTimeout(
    supabase
      .from("v_emotions_with_palette")
      .select(
        "id, slug, name, emoji, category_id, category_slug, category_name, primary_color, secondary_color, light_color, surface_color",
      ),
    8000,
    "fetch v_emotions_with_palette (full)",
  )
  if (error) throw new Error(error.message)

  const rows: EmotionWithPalette[] = []
  for (const r of (data ?? []) as Row[]) {
    // PostgREST types every view column as nullable. The underlying tables
    // enforce NOT NULL on all of these (see PR #382 migration), so a null
    // here is a real data bug — drop the row at the boundary.
    if (
      r.id &&
      r.slug &&
      r.name &&
      r.emoji &&
      r.category_id &&
      r.category_slug &&
      r.category_name &&
      r.primary_color &&
      r.secondary_color &&
      r.light_color &&
      r.surface_color
    ) {
      rows.push({
        id: r.id,
        slug: r.slug,
        name: r.name,
        emoji: r.emoji,
        category_id: r.category_id,
        category_slug: r.category_slug,
        category_name: r.category_name,
        primary_color: r.primary_color,
        secondary_color: r.secondary_color,
        light_color: r.light_color,
        surface_color: r.surface_color,
      })
    }
  }
  return rows
}

function loadOnce(): Promise<EmotionWithPalette[]> {
  if (cached) return Promise.resolve(cached)
  if (inflight) return inflight
  inflight = fetchRows()
    .then((rows) => {
      cached = rows
      return rows
    })
    .finally(() => {
      inflight = null
    })
  return inflight
}

export function useEmotionsWithPalette(): {
  rows: EmotionWithPalette[]
  loading: boolean
} {
  const [rows, setRows] = useState<EmotionWithPalette[]>(() => cached ?? [])
  const [loading, setLoading] = useState<boolean>(() => cached === null)

  useEffect(() => {
    let cancelled = false
    loadOnce()
      .then((next) => {
        if (cancelled) return
        setRows(next)
        setLoading(false)
      })
      .catch((err) => {
        if (cancelled) return
        console.error("[useEmotionsWithPalette]", err)
        setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  return { rows, loading }
}
