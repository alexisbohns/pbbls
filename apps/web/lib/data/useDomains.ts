"use client"

import { useEffect, useState } from "react"
import { createClient } from "@/lib/supabase/client"
import { withTimeout } from "@/lib/utils/with-timeout"
import type { MarkStroke } from "@/lib/types"

export type DomainGlyph = { strokes: MarkStroke[]; viewBox: string }

export type DomainRow = {
  id: string
  slug: string
  name: string
  label: string
  glyph: DomainGlyph | null
}

type Row = {
  id: string | null
  slug: string | null
  name: string | null
  label: string | null
  strokes: MarkStroke[] | null
  view_box: string | null
}

// Module-level cache — reference data is read-only and user-agnostic. Mirrors
// `useEmotionsWithPalette`.
let cached: DomainRow[] | null = null
let inflight: Promise<DomainRow[]> | null = null

async function fetchDomains(): Promise<DomainRow[]> {
  const supabase = createClient()
  const { data, error } = await withTimeout(
    supabase.from("v_domains_with_glyph").select("id, slug, name, label, strokes, view_box"),
    8000,
    "fetch v_domains_with_glyph",
  )
  if (error) throw new Error(error.message)

  const rows: DomainRow[] = []
  for (const r of (data ?? []) as Row[]) {
    // PostgREST types every view column as nullable; the underlying `domains`
    // columns are NOT NULL, so a null id/slug/name/label here is a real data
    // bug — drop the row at the boundary rather than render a broken tile.
    if (!r.id || !r.slug || !r.name || !r.label) continue
    // Domains without a glyph (LEFT JOIN → null) fall back to text-only,
    // matching today's behavior.
    const glyph =
      r.strokes && r.strokes.length > 0 && r.view_box
        ? { strokes: r.strokes, viewBox: r.view_box }
        : null
    rows.push({ id: r.id, slug: r.slug, name: r.name, label: r.label, glyph })
  }
  return rows
}

function loadOnce(): Promise<DomainRow[]> {
  if (cached) return Promise.resolve(cached)
  if (inflight) return inflight
  inflight = fetchDomains()
    .then((rows) => {
      cached = rows
      return rows
    })
    .finally(() => {
      inflight = null
    })
  return inflight
}

/**
 * Returns all domains (id, slug, name, label, glyph) fetched from
 * `v_domains_with_glyph`, or an empty array until loaded. This is the source
 * of truth for domain id/slug — do not join against a hand-maintained slug
 * list, since it can drift from the DB (see issue behind this hook).
 */
export function useDomains(): { rows: DomainRow[]; loading: boolean } {
  const [rows, setRows] = useState<DomainRow[]>(() => cached ?? [])
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
        console.error("[useDomains]", err)
        setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  return { rows, loading }
}
