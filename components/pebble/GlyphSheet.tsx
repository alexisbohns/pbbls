"use client"

import { useState, useCallback } from "react"
import { Plus } from "lucide-react"
import type { Mark } from "@/lib/types"
import { cn } from "@/lib/utils"
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
import { GlyphPreview } from "@/components/glyphs/GlyphPreview"

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

  const handleSelect = useCallback((id: string) => {
    setLocalMarkId((prev) => (prev === id ? undefined : id))
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

        {marks.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            No glyphs yet. Carve one from the Carve page.
          </p>
        ) : (
          <ul
            role="radiogroup"
            aria-label="Glyphs"
            className="grid grid-cols-4 gap-2"
          >
            {marks.map((mark) => {
              const selected = localMarkId === mark.id
              return (
                <li key={mark.id}>
                  <button
                    type="button"
                    role="radio"
                    aria-checked={selected}
                    aria-label={mark.name ?? `Glyph ${mark.id.slice(0, 4)}`}
                    onClick={() => handleSelect(mark.id)}
                    className={cn(
                      "flex size-16 items-center justify-center rounded-lg border p-1 transition-all duration-100 outline-none focus-visible:ring-3 focus-visible:ring-ring/50 active:scale-95",
                      selected
                        ? "border-primary bg-primary/10 ring-2 ring-primary"
                        : "border-input hover:bg-muted",
                    )}
                  >
                    <GlyphPreview mark={mark} className="size-full" />
                  </button>
                </li>
              )
            })}
          </ul>
        )}

        <DialogFooter>
          <DialogClose>Cancel</DialogClose>
          <Button onClick={handleSave}>Save</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
