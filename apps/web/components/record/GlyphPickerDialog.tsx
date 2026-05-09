"use client"

import { useState, useCallback } from "react"
import { useTranslations } from "next-intl"
import type { Mark } from "@/lib/types"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogClose,
} from "@/components/ui/dialog"
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
  const [localMarkId, setLocalMarkId] = useState<string | undefined>(selectedMarkId)
  const t = useTranslations("record.glyph")
  const tCommon = useTranslations("common")

  const handleOpenChange = useCallback(
    (nextOpen: boolean) => {
      onOpenChange(nextOpen)
      if (nextOpen) setLocalMarkId(selectedMarkId)
    },
    [selectedMarkId, onOpenChange],
  )

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
        </DialogHeader>

        <GlyphPickerGrid
          marks={marks}
          selectedMarkId={localMarkId}
          onSelect={setLocalMarkId}
        />

        <DialogFooter>
          <DialogClose>{tCommon("cancel")}</DialogClose>
          <Button
            onClick={() => {
              onSave(localMarkId)
              onOpenChange(false)
            }}
          >
            {tCommon("save")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
