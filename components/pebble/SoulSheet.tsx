"use client"

import { useState, useCallback } from "react"
import { Plus } from "lucide-react"
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
import { SoulPicker } from "@/components/record/SoulPicker"

type SoulSheetProps = {
  value: string[]
  onSave: (ids: string[]) => void
}

export function SoulSheet({ value, onSave }: SoulSheetProps) {
  const [open, setOpen] = useState(false)
  const [localIds, setLocalIds] = useState<string[]>(value)

  const handleOpenChange = useCallback(
    (nextOpen: boolean) => {
      setOpen(nextOpen)
      if (nextOpen) setLocalIds(value)
    },
    [value],
  )

  const handleSave = useCallback(() => {
    onSave(localIds)
    setOpen(false)
  }, [localIds, onSave])

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger
        render={
          <Button
            variant="ghost"
            size="icon-xs"
            aria-label="Add souls"
          >
            <Plus />
          </Button>
        }
      />
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Souls</DialogTitle>
        </DialogHeader>
        <SoulPicker value={localIds} onChange={setLocalIds} />
        <DialogFooter>
          <DialogClose>Cancel</DialogClose>
          <Button onClick={handleSave}>Save</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
