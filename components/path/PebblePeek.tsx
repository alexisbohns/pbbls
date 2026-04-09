"use client"

import { X } from "lucide-react"
import { Dialog as DialogPrimitive } from "@base-ui/react/dialog"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import { usePebble } from "@/lib/data/usePebble"
import { useSouls } from "@/lib/data/useSouls"
import { useCollections } from "@/lib/data/useCollections"
import { useMarks } from "@/lib/data/useMarks"
import { PebbleDetail } from "@/components/pebble/PebbleDetail"

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
    <DialogPrimitive.Root
      open={open}
      onOpenChange={(nextOpen) => {
        if (!nextOpen) onClose()
      }}
    >
      <DialogPrimitive.Portal>
        <DialogPrimitive.Backdrop
          className="fixed inset-0 z-50 bg-black/10 supports-backdrop-filter:backdrop-blur-xs transition-opacity duration-200 data-open:opacity-100 data-closed:opacity-0"
        />
        <DialogPrimitive.Popup
          className={cn(
            // Shared
            "fixed z-50 w-full bg-popover text-popover-foreground ring-1 ring-foreground/10 outline-none overflow-y-auto max-h-[85dvh]",
            // Mobile: bottom sheet
            "inset-x-0 bottom-0 rounded-t-2xl p-4 pt-2",
            "translate-y-full opacity-0 data-open:translate-y-0 data-open:opacity-100 transition-[transform,opacity] duration-200 ease-out",
            // Desktop: centered modal
            "md:bottom-auto md:right-auto md:left-1/2 md:top-1/2 md:-translate-x-1/2 md:-translate-y-1/2 md:data-open:-translate-y-1/2 md:max-w-2xl md:rounded-xl md:p-6",
          )}
        >
          {/* Mobile drag handle */}
          <div className="mx-auto mb-3 h-1 w-10 rounded-full bg-muted-foreground/30 md:hidden" aria-hidden />

          <div className="flex justify-end">
            <DialogPrimitive.Close
              render={<Button variant="ghost" size="icon-sm" />}
              aria-label="Close"
            >
              <X className="size-4" />
            </DialogPrimitive.Close>
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
        </DialogPrimitive.Popup>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  )
}
