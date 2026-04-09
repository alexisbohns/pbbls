"use client"

import { useState, useCallback } from "react"
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
          <DialogTitle>Glyph</DialogTitle>
        </DialogHeader>

        <GlyphPickerGrid
          marks={marks}
          selectedMarkId={localMarkId}
          onSelect={setLocalMarkId}
        />

        <DialogFooter>
          <DialogClose>Cancel</DialogClose>
          <Button
            onClick={() => {
              onSave(localMarkId)
              onOpenChange(false)
            }}
          >
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
