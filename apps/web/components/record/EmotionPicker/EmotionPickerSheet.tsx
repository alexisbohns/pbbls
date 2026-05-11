"use client"

import { useMemo } from "react"
import { useLocale, useTranslations } from "next-intl"
import { X } from "lucide-react"
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetClose,
} from "@/components/ui/sheet"
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
  /** Current emotion_id from the form. */
  value: string | undefined
  /** Drives section order (mirrors iOS valence-based ordering). */
  intensity?: Intensity
  valence?: Valence
  /**
   * Called when the user picks (or clears) an emotion. Receives `undefined`
   * when the user taps the currently-selected chip to deselect.
   */
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
 * driven by the form's current cell. Tapping a chip commits the selection and
 * dismisses the sheet — tapping the currently-selected chip clears the value
 * (preserves iOS deselect behavior). The close (X) button in the header
 * dismisses without committing any change.
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

    // Sort emotions within each category by DB name (locale-aware). The
    // catalog mirrors DB names so this stays close to the localized order;
    // we re-run on locale change for proper collation.
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

  const handleChipSelect = (id: string) => {
    onChange(id === value ? undefined : id)
    onOpenChange(false)
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent>
        <SheetHeader className="relative">
          <SheetTitle>{t("title")}</SheetTitle>
          <SheetClose
            aria-label={t("close")}
            variant="ghost"
            size="icon-sm"
            className="absolute right-0 top-0"
          >
            <X aria-hidden />
          </SheetClose>
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
                selectedId={value}
                onSelect={handleChipSelect}
              />
            ))}
          </div>
        )}
      </SheetContent>
    </Sheet>
  )
}
