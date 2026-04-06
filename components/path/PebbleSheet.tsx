"use client"

import { X } from "lucide-react"
import { usePebble } from "@/lib/data/usePebble"
import { useSouls } from "@/lib/data/useSouls"
import { useCollections } from "@/lib/data/useCollections"
import { useMarks } from "@/lib/data/useMarks"
import { PebbleDetail } from "@/components/pebble/PebbleDetail"
import {
  Sheet,
  SheetContent,
  SheetClose,
} from "@/components/ui/sheet"

type PebbleSheetProps = {
  pebbleId: string | null
  onClose: () => void
}

export function PebbleSheet({ pebbleId, onClose }: PebbleSheetProps) {
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
    <Sheet
      open={open}
      onOpenChange={(nextOpen) => {
        if (!nextOpen) onClose()
      }}
    >
      <SheetContent>
        <div className="flex justify-end mb-2">
          <SheetClose variant="ghost" size="icon-sm" aria-label="Close">
            <X className="size-4" />
          </SheetClose>
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
      </SheetContent>
    </Sheet>
  )
}
