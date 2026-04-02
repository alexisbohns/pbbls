"use client"

import { useState, useCallback } from "react"
import { Plus } from "lucide-react"
import type { Collection } from "@/lib/types"
import type { UpdateCollectionInput } from "@/lib/data/data-provider"
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

type CollectionSheetProps = {
  pebbleId: string
  allCollections: Collection[]
  linkedIds: string[]
  onUpdateCollection: (id: string, input: UpdateCollectionInput) => Promise<Collection>
}

export function CollectionSheet({
  pebbleId,
  allCollections,
  linkedIds,
  onUpdateCollection,
}: CollectionSheetProps) {
  const [open, setOpen] = useState(false)
  const [selectedIds, setSelectedIds] = useState<string[]>(linkedIds)

  const handleOpenChange = useCallback(
    (nextOpen: boolean) => {
      setOpen(nextOpen)
      if (nextOpen) setSelectedIds(linkedIds)
    },
    [linkedIds],
  )

  const toggle = useCallback((id: string) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((v) => v !== id) : [...prev, id],
    )
  }, [])

  const handleSave = useCallback(async () => {
    const added = selectedIds.filter((id) => !linkedIds.includes(id))
    const removed = linkedIds.filter((id) => !selectedIds.includes(id))

    const updates = [
      ...added.map((collectionId) => {
        const collection = allCollections.find((c) => c.id === collectionId)
        if (!collection) return null
        return onUpdateCollection(collectionId, {
          pebble_ids: [...collection.pebble_ids, pebbleId],
        })
      }),
      ...removed.map((collectionId) => {
        const collection = allCollections.find((c) => c.id === collectionId)
        if (!collection) return null
        return onUpdateCollection(collectionId, {
          pebble_ids: collection.pebble_ids.filter((pid) => pid !== pebbleId),
        })
      }),
    ].filter(Boolean)

    await Promise.all(updates)
    setOpen(false)
  }, [selectedIds, linkedIds, allCollections, pebbleId, onUpdateCollection])

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger
        render={
          <Button
            variant="ghost"
            size="icon-xs"
            aria-label="Add collections"
          >
            <Plus />
          </Button>
        }
      />
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Collections</DialogTitle>
        </DialogHeader>

        {allCollections.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            No collections yet. Create one from the Collections page.
          </p>
        ) : (
          <ul role="listbox" aria-multiselectable="true" aria-label="Collections" className="max-h-64 overflow-y-auto">
            {allCollections.map((collection) => {
              const selected = selectedIds.includes(collection.id)
              return (
                <li
                  key={collection.id}
                  role="option"
                  aria-selected={selected}
                  onClick={() => toggle(collection.id)}
                  className={cn(
                    "flex cursor-pointer items-center gap-2 rounded-md px-3 py-2 text-sm transition-colors duration-75",
                    selected && "font-medium",
                  )}
                >
                  <span
                    className={cn(
                      "flex size-4 shrink-0 items-center justify-center rounded border text-[10px]",
                      selected
                        ? "border-primary bg-primary text-primary-foreground"
                        : "border-input",
                    )}
                    aria-hidden="true"
                  >
                    {selected && "✓"}
                  </span>
                  {collection.name}
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
