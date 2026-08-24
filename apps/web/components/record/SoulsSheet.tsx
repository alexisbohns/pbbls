"use client"

import { Check } from "lucide-react"
import { useTranslations } from "next-intl"
import type { Mark, Soul } from "@/lib/types"
import { PickerSheet } from "@/components/ui/PickerSheet"
import { SheetClose } from "@/components/ui/sheet"
import { SoulPickerContent } from "@/components/record/SoulPickerContent"

type SoulsSheetProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  selectedIds: string[]
  onToggle: (id: string) => void
  souls: Soul[]
  marks: Mark[]
  onAddSoul: (name: string) => Promise<void>
}

/**
 * Multi-select soul picker presented in the shared drawer. The grid is
 * `SoulPickerContent`, shared with the flow's souls step; what stays here is
 * the sheet's own Done affordance, which is how a sheet commits.
 */
export function SoulsSheet({
  open,
  onOpenChange,
  selectedIds,
  onToggle,
  souls,
  marks,
  onAddSoul,
}: SoulsSheetProps) {
  const t = useTranslations("record.souls")

  return (
    <PickerSheet
      open={open}
      onOpenChange={onOpenChange}
      title={t("title")}
      closeLabel={t("close")}
      overlay={
        selectedIds.length > 0 && (
          <SheetClose
            aria-label={t("done")}
            variant="default"
            size="icon-lg"
            className="rounded-full shadow-lg"
          >
            <Check className="size-5" aria-hidden />
          </SheetClose>
        )
      }
    >
      {/* Remounted per opening so the search query never carries over from the
          last time the sheet was used. */}
      {open && (
        <SoulPickerContent
          souls={souls}
          marks={marks}
          selectedIds={selectedIds}
          onToggle={onToggle}
          onAddSoul={onAddSoul}
        />
      )}
    </PickerSheet>
  )
}
