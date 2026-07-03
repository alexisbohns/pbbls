"use client"

import { useEffect, useState } from "react"
import { createClient } from "@/lib/supabase/client"
import { withTimeout } from "@/lib/utils/with-timeout"
import type { MarkStroke } from "@/lib/types"

/** A domain's glyph: strokes + its square viewBox. Keyed by domain slug. */
export type DomainGlyph = { strokes: MarkStroke[]; viewBox: string }

type Row = {
  slug: string | null
  strokes: MarkStroke[] | null
  view_box: string | null
}

// Module-level cache — reference data is read-only and user-agnostic. Mirrors
// useEmotionsWithPalette.
let cached: Map<string, DomainGlyph> | null = null
let inflight: Promise<Map<string, DomainGlyph>> | null = null

async function fetchGlyphs(): Promise<Map<string, DomainGlyph>> {
  const supabase = createClient()
  const { data, error } = await withTimeout(
    supabase.from("v_domains_with_glyph").select("slug, strokes, view_box"),
    8000,
    "fetch v_domains_with_glyph",
  )
  if (error) throw new Error(error.message)

  const map = new Map<string, DomainGlyph>()
  for (const r of (data ?? []) as Row[]) {
    // Domains without a glyph (LEFT JOIN → null) are skipped; the surfaces fall
    // back to text-only, matching today's behavior.
    if (r.slug && r.strokes && r.strokes.length > 0 && r.view_box) {
      map.set(r.slug, { strokes: r.strokes, viewBox: r.view_box })
    }
  }
  return map
}

/**
 * Returns a `slug → DomainGlyph` map, or null until loaded. Failures resolve to
 * an empty map (text-only fallback) — a missing glyph is never fatal.
 */
export function useDomainGlyphs(): Map<string, DomainGlyph> | null {
  const [glyphs, setGlyphs] = useState<Map<string, DomainGlyph> | null>(cached)

  useEffect(() => {
    if (glyphs) return
    if (!inflight) {
      inflight = fetchGlyphs()
        .then((m) => {
          cached = m
          return m
        })
        .catch((e) => {
          console.warn("[useDomainGlyphs] fetch failed:", e)
          cached = new Map()
          return cached
        })
        .finally(() => {
          inflight = null
        })
    }
    let active = true
    inflight.then((m) => {
      if (active) setGlyphs(m)
    })
    return () => {
      active = false
    }
  }, [glyphs])

  return glyphs
}
