"use client"

import { useTranslations } from "next-intl"
import type { Mark } from "@/lib/types"
import { PickerSheet } from "@/components/ui/PickerSheet"
import { GlyphPickerGrid } from "@/components/glyphs/GlyphPickerGrid"

type GlyphPickerDialogProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  marks: Mark[]
  selectedMarkId: string | undefined
  onSave: (markId: string | undefined) => void
}

export function GlyphPickerDialog({
  open,
  onOpenChange,
  marks,
  selectedMarkId,
  onSave,
}: GlyphPickerDialogProps) {
  const t = useTranslations("record.glyph")

  const handleSelect = (id: string | undefined) => {
    onSave(id)
    onOpenChange(false)
  }

  return (
    <PickerSheet
      open={open}
      onOpenChange={onOpenChange}
      title={t("title")}
      closeLabel={t("close")}
    >
      <GlyphPickerGrid
        marks={marks}
        selectedMarkId={selectedMarkId}
        onSelect={handleSelect}
      />
    </PickerSheet>
  )
}
