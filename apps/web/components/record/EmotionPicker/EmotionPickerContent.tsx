"use client"

import { useMemo } from "react"
import { useLocale, useTranslations } from "next-intl"
import { useEmotionsWithPalette, type EmotionWithPalette } from "@/lib/data/useEmotionsWithPalette"
import {
  emotionCategoryOrder,
  type Intensity,
  type Valence,
} from "@/lib/config/emotion-category-ordering"
import { EmotionPickerSection } from "./EmotionPickerSection"
import { EmotionPickerEmpty } from "./EmotionPickerEmpty"

type EmotionPickerContentProps = {
  /** Current emotion_id, or undefined when nothing is chosen yet. */
  selected: string | undefined
  /** Drives section order (mirrors iOS valence-based ordering). */
  intensity?: Intensity
  valence?: Valence
  /** Fired with the tapped chip's id. Commit semantics belong to the caller. */
  onSelect: (id: string) => void
}

/**
 * The grouped, ordered body of the emotion picker — categories deduped from the
 * cached `v_emotions_with_palette` rows, ordered by the composer's current
 * (intensity, valence) cell, emotions sorted by their localized name.
 *
 * Presentation only: no staging, no dismissal, no commit policy. That is what
 * lets the two callers differ where they should — `EmotionPickerSheet` treats a
 * tap on the selected chip as a deselect and dismisses, while the flow's step
 * commits on tap and advances, and a step that advances cannot be cancelled.
 * Same grouping and ordering logic, one implementation.
 */
export function EmotionPickerContent({
  selected,
  intensity,
  valence,
  onSelect,
}: EmotionPickerContentProps) {
  // Untyped accessor for the runtime `emotion.<slug>.name` catalog — slugs
  // are DB values, not part of the typed message tree.
  const tAll = useTranslations() as unknown as {
    (key: string): string
    has(key: string): boolean
  }
  const locale = useLocale()
  const { rows, loading } = useEmotionsWithPalette()

  const groups = useMemo<CategoryGroup[]>(() => {
    if (rows.length === 0) return []
    const byCategorySlug = new Map<string, CategoryGroup>()
    for (const row of rows) {
      let group = byCategorySlug.get(row.category_slug)
      if (!group) {
        group = {
          slug: row.category_slug,
          name: row.category_name,
          primaryColor: row.primary_color,
          rows: [],
        }
        byCategorySlug.set(row.category_slug, group)
      }
      group.rows.push(row)
    }

    // Sort emotions within each category by their localized name so the order
    // matches the chip text the user actually sees (and follows fr-locale
    // collation when active).
    const collator = new Intl.Collator(locale, { sensitivity: "base" })
    const localizedName = (row: EmotionWithPalette): string => {
      const key = `emotion.${row.slug}.name`
      return tAll.has(key) ? tAll(key) : row.name
    }
    for (const group of byCategorySlug.values()) {
      group.rows.sort((a, b) => collator.compare(localizedName(a), localizedName(b)))
    }

    const order = emotionCategoryOrder(intensity, valence)
    const ordered: CategoryGroup[] = []
    for (const slug of order) {
      const group = byCategorySlug.get(slug)
      if (group && group.rows.length > 0) ordered.push(group)
    }
    return ordered
  }, [rows, intensity, valence, locale, tAll])

  if (loading || groups.length === 0) return <EmotionPickerEmpty />

  return (
    <div className="flex flex-col gap-6 pb-4">
      {groups.map((group) => (
        <EmotionPickerSection
          key={group.slug}
          categorySlug={group.slug}
          categoryName={group.name}
          primaryColor={group.primaryColor}
          rows={group.rows}
          selectedId={selected}
          onSelect={onSelect}
        />
      ))}
    </div>
  )
}

type CategoryGroup = {
  slug: string
  name: string
  primaryColor: string
  rows: EmotionWithPalette[]
}
