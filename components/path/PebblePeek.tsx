"use client"

import { X } from "lucide-react"
import { usePebble } from "@/lib/data/usePebble"
import { useSouls } from "@/lib/data/useSouls"
import { useCollections } from "@/lib/data/useCollections"
import { useMarks } from "@/lib/data/useMarks"
import { PebbleDetail } from "@/components/pebble/PebbleDetail"
import {
  Dialog,
  DialogContent,
  DialogClose,
} from "@/components/ui/dialog"

type PebblePeekProps = {
  pebbleId: string | null
  onClose: () => void
}

export function PebblePeek({ pebbleId, onClose }: PebblePeekProps) {
  const open = pebbleId !== null
  const id = pebbleId ?? ""

  const { pebble, loading: pebbleLoading, updatePebble } = usePebble(id)
  const { souls, loading: soulsLoading } = useSouls()
  const { collections, loading: collectionsLoading, updateCollection } = useCollections()
  const { marks, loading: marksLoading } = useMarks()

  const loading = pebbleLoading || soulsLoading || collectionsLoading || marksLoading

  const matchedCollections = collections.filter((c) =>
    c.pebble_ids.includes(id),
  )
  const mark = pebble ? marks.find((m) => m.id === pebble.mark_id) : undefined

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        if (!nextOpen) onClose()
      }}
    >
      <DialogContent className="max-w-lg max-h-[85dvh] overflow-y-auto">
        <div className="flex justify-end">
          <DialogClose variant="ghost" size="icon-sm" aria-label="Close">
            <X className="size-4" />
          </DialogClose>
        </div>

        {loading ? (
          <p className="text-sm text-muted-foreground">Loading…</p>
        ) : pebble ? (
          <PebbleDetail
            pebble={pebble}
            souls={souls}
            collections={matchedCollections}
            allCollections={collections}
            marks={marks}
            mark={mark}
            onUpdatePebble={updatePebble}
            onUpdateCollection={updateCollection}
          />
        ) : (
          <p className="text-sm text-muted-foreground">Pebble not found.</p>
        )}
      </DialogContent>
    </Dialog>
  )
}
