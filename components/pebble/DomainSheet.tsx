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
import { DomainPicker } from "@/components/record/DomainPicker"

type DomainSheetProps = {
  value: string[]
  onSave: (ids: string[]) => void
}

export function DomainSheet({ value, onSave }: DomainSheetProps) {
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
            aria-label="Add domains"
          >
            <Plus />
          </Button>
        }
      />
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Domains</DialogTitle>
        </DialogHeader>
        <DomainPicker value={localIds} onChange={setLocalIds} />
        <DialogFooter>
          <DialogClose>Cancel</DialogClose>
          <Button onClick={handleSave}>Save</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
