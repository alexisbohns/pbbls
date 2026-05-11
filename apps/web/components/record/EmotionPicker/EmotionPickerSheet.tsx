"use client"

import { useMemo, useState } from "react"
import { useLocale, useTranslations } from "next-intl"
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import { Button } from "@/components/ui/button"
import { useEmotionsWithPalette, type EmotionWithPalette } from "@/lib/data/useEmotionsWithPalette"
import {
  emotionCategoryOrder,
  type Intensity,
  type Valence,
} from "@/lib/config/emotion-category-ordering"
import { EmotionPickerSection } from "./EmotionPickerSection"
import { EmotionPickerEmpty } from "./EmotionPickerEmpty"

type EmotionPickerSheetProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Current emotion_id from the form; selection is staged locally until Done. */
  value: string | undefined
  /** Drives section order (mirrors iOS valence-based ordering). */
  intensity?: Intensity
  valence?: Valence
  /** Commit handler. Receives the staged emotion id, or undefined to clear. */
  onChange: (id: string | undefined) => void
}

type CategoryGroup = {
  slug: string
  name: string
  primaryColor: string
  rows: EmotionWithPalette[]
}

/**
 * Two-level emotion picker presented as a Sheet.
 *
 * Categories derive from the cached `v_emotions_with_palette` rows by deduping
 * on `category_slug`; section order is `emotionCategoryOrder(intensity, valence)`
 * driven by the form's current cell. Selection is staged locally — Done commits
 * via `onChange`; Cancel discards. Tapping the staged chip clears it so the
 * user can deselect inside the sheet without backing out (mirrors iOS).
 */
export function EmotionPickerSheet({
  open,
  onOpenChange,
  value,
  intensity,
  valence,
  onChange,
}: EmotionPickerSheetProps) {
  const t = useTranslations("record.emotionPicker")
  const locale = useLocale()
  const { rows, loading } = useEmotionsWithPalette()
  const [staged, setStaged] = useState<string | undefined>(value)

  // Reset staged selection on every open transition so unsaved local edits
  // from a Cancel'd session don't leak into the next opening. Handled here
  // (instead of in an effect) to avoid cascading renders.
  const handleOpenChange = (nextOpen: boolean) => {
    if (nextOpen) setStaged(value)
    onOpenChange(nextOpen)
  }

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

    // Sort emotions within each category by localized name (locale-aware), to
    // match iOS `EmotionPickerSheet.groups`. We don't have the localized name
    // resolved here (no hook in this scope), so sort on the DB `name` column
    // — close enough since the catalog tracks DB names. Re-run on locale change
    // via the dependency so `localeCompare` picks up the right locale rules.
    const collator = new Intl.Collator(locale, { sensitivity: "base" })
    for (const group of byCategorySlug.values()) {
      group.rows.sort((a, b) => collator.compare(a.name, b.name))
    }

    const order = emotionCategoryOrder(intensity, valence)
    const ordered: CategoryGroup[] = []
    for (const slug of order) {
      const group = byCategorySlug.get(slug)
      if (group && group.rows.length > 0) ordered.push(group)
    }
    return ordered
  }, [rows, intensity, valence, locale])

  const handleChipToggle = (id: string) => {
    setStaged((current) => (current === id ? undefined : id))
  }

  const handleCancel = () => {
    onOpenChange(false)
  }

  const handleDone = () => {
    onChange(staged)
    onOpenChange(false)
  }

  return (
    <Sheet open={open} onOpenChange={handleOpenChange}>
      <SheetContent>
        <SheetHeader>
          <SheetTitle>{t("title")}</SheetTitle>
        </SheetHeader>

        {loading || groups.length === 0 ? (
          <EmotionPickerEmpty />
        ) : (
          <div className="flex flex-col gap-6 pb-4">
            {groups.map((group) => (
              <EmotionPickerSection
                key={group.slug}
                categorySlug={group.slug}
                categoryName={group.name}
                primaryColor={group.primaryColor}
                rows={group.rows}
                selectedId={staged}
                onToggle={handleChipToggle}
              />
            ))}
          </div>
        )}

        <div className="sticky bottom-0 -mx-4 mt-2 flex justify-end gap-2 border-t border-border/40 bg-popover px-4 py-3 md:-mx-6 md:px-6">
          <Button variant="ghost" size="sm" onClick={handleCancel}>
            {t("cancel")}
          </Button>
          <Button size="sm" onClick={handleDone}>
            {t("done")}
          </Button>
        </div>
      </SheetContent>
    </Sheet>
  )
}

