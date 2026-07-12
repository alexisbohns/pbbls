"use client"

import { useTranslations } from "next-intl"
import { PickerSheet } from "@/components/ui/PickerSheet"
import { GlyphPickerTabs } from "@/components/record/GlyphPickerTabs"

type GlyphPickerDialogProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  selectedMarkId: string | undefined
  onSave: (markId: string | undefined) => void
}

export function GlyphPickerDialog({
  open,
  onOpenChange,
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
      <GlyphPickerTabs selectedMarkId={selectedMarkId} onSelect={handleSelect} />
    </PickerSheet>
  )
}
