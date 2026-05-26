"use client"

import { useCallback } from "react"
import { useRouter } from "next/navigation"
import { useTranslations } from "next-intl"
import { Dialog as DialogPrimitive } from "@base-ui/react/dialog"
import { cn } from "@/lib/utils"
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
  const t = useTranslations("pebble")
  const router = useRouter()

  const { pebble, loading: pebbleLoading, updatePebble, uploadSnap } = usePebble(id)
  const { souls, loading: soulsLoading, addSoul } = useSouls()
  const { collections, loading: collectionsLoading } = useCollections()
  const { marks, loading: marksLoading } = useMarks()

  const loading = pebbleLoading || soulsLoading || collectionsLoading || marksLoading

  const mark = pebble ? marks.find((m) => m.id === pebble.mark_id) : undefined

  const handleAddSoul = useCallback(
    async (name: string) => {
      const soul = await addSoul({ name })
      if (pebble) {
        await updatePebble({ soul_ids: [...pebble.soul_ids, soul.id] })
      }
    },
    [addSoul, updatePebble, pebble],
  )

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
            "fixed z-50 w-full bg-background text-popover-foreground ring-1 ring-foreground/10 outline-none overflow-y-auto max-h-[85dvh]",
            // Mobile: bottom sheet
            "inset-x-0 bottom-0 rounded-t-2xl p-4 pt-2",
            "translate-y-full opacity-0 data-open:translate-y-0 data-open:opacity-100 transition-[transform,opacity] duration-200 ease-out",
            // Desktop: centered modal
            "md:bottom-auto md:right-auto md:left-1/2 md:top-1/2 md:-translate-x-1/2 md:-translate-y-1/2 md:data-open:-translate-y-1/2 md:max-w-2xl md:rounded-xl md:p-6",
          )}
        >
          {/* Mobile drag handle */}
          <div className="mx-auto mb-3 h-1 w-10 rounded-full bg-muted-foreground/30 md:hidden" aria-hidden />

          {loading ? (
            <p className="text-sm text-muted-foreground">{t("loading")}</p>
          ) : pebble ? (
            <PebbleDetail
              pebble={pebble}
              souls={souls}
              collections={collections}
              marks={marks}
              mark={mark}
              onUpdatePebble={updatePebble}
              onUploadSnap={uploadSnap}
              onAddSoul={handleAddSoul}
              onClose={onClose}
              onEdit={() => {
                onClose()
                router.push(`/pebble/${id}/edit`)
              }}
            />
          ) : (
            <p className="text-sm text-muted-foreground">{t("notFoundInline")}</p>
          )}
        </DialogPrimitive.Popup>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  )
}
