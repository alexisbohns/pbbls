"use client"

import { useEmotionsWithPalette } from "@/lib/data/useEmotionsWithPalette"
import { useEmotionLocalized } from "@/lib/i18n"

/**
 * Resolve the current `emotion_id` form value into a display payload
 * (emoji + locale-resolved name) for trigger buttons next to the picker
 * sheet. Returns `undefined` when nothing is selected or while the palette
 * cache is still hydrating.
 */
export function useSelectedEmotionDisplay(
  value: string | undefined,
): { emoji: string; name: string } | undefined {
  const { rows } = useEmotionsWithPalette()
  const row = value ? rows.find((r) => r.id === value) : undefined
  // `useEmotionLocalized` is a hook — call it unconditionally with a benign
  // fallback so the hook order stays stable across renders.
  const localized = useEmotionLocalized(
    row ? { slug: row.slug, name: row.name, label: "" } : { slug: "", name: "", label: "" },
  )
  if (!row) return undefined
  return { emoji: row.emoji, name: localized.name }
}
