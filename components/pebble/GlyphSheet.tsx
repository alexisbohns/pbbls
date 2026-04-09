"use client"

import { useState, useCallback } from "react"
import { Plus } from "lucide-react"
import type { Mark } from "@/lib/types"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { GlyphPickerGrid } from "@/components/glyphs/GlyphPickerGrid"

type GlyphSheetProps = {
  marks: Mark[]
  selectedMarkId: string | undefined
  onSave: (markId: string | undefined) => void
}

export function GlyphSheet({ marks, selectedMarkId, onSave }: GlyphSheetProps) {
  const [open, setOpen] = useState(false)
  const [localMarkId, setLocalMarkId] = useState<string | undefined>(selectedMarkId)

  const handleOpenChange = useCallback(
    (nextOpen: boolean) => {
      setOpen(nextOpen)
      if (nextOpen) setLocalMarkId(selectedMarkId)
    },
    [selectedMarkId],
  )

  const handleSelect = useCallback((id: string | undefined) => {
    setLocalMarkId(id)
  }, [])

  const handleSave = useCallback(() => {
    onSave(localMarkId)
    setOpen(false)
  }, [localMarkId, onSave])

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger
        render={
          <Button
            variant="ghost"
            size="icon-xs"
            aria-label="Add glyph"
          >
            <Plus />
          </Button>
        }
      />
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Glyph</DialogTitle>
        </DialogHeader>

        <GlyphPickerGrid
          marks={marks}
          selectedMarkId={localMarkId}
          onSelect={handleSelect}
        />

        <DialogFooter>
          <DialogClose>Cancel</DialogClose>
          <Button onClick={handleSave}>Save</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
