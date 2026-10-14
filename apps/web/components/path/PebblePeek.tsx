"use client"

import { useCallback, useRef, useState } from "react"
import { useRouter } from "next/navigation"
import { useTranslations } from "next-intl"
import { Dialog as DialogPrimitive } from "@base-ui/react/dialog"
import { cn } from "@/lib/utils"
import { useMoment } from "@/lib/data/useMoment"
import { useSouls } from "@/lib/data/useSouls"
import { useCollections } from "@/lib/data/useCollections"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"
import { MomentRead } from "@/components/pebble/MomentRead"

type PebblePeekProps = {
  /** The moment to read (#1050), null when the peek is closed. */
  momentId: string | null
  onClose: () => void
}

// Swipe-down-to-close threshold for the mobile bottom sheet. Past this in
// translateY (or with strong downward velocity), release dismisses the peek.
const SWIPE_CLOSE_DISTANCE = 100
const SWIPE_VELOCITY_THRESHOLD = 0.5 // px per ms

export function PebblePeek({ momentId, onClose }: PebblePeekProps) {
  const open = momentId !== null
  const t = useTranslations("pebble")
  const tMoment = useTranslations("moment")
  const router = useRouter()

  const { moment, loading: momentLoading } = useMoment(momentId ?? "")
  const { souls, loading: soulsLoading } = useSouls()
  const { collections, loading: collectionsLoading } = useCollections()
  const { glyphs: marks, loading: marksLoading } = useUsableGlyphs()

  const loading = momentLoading || soulsLoading || collectionsLoading || marksLoading

  // Leaving for another screen closes the peek first.
  const handleNavigate = useCallback(
    (href: string) => {
      onClose()
      router.push(href)
    },
    [onClose, router],
  )

  // Swipe-down-to-close: only intercepts when the popup is scrolled to its
  // top, so vertical scrolling inside the sheet still works. We translate the
  // popup with inline style during the drag and snap back (or close) on
  // release — the existing transition-[transform] class handles the snap-back
  // animation when the inline style clears.
  const popupRef = useRef<HTMLDivElement | null>(null)
  const dragStartRef = useRef<{ y: number; time: number } | null>(null)
  const [dragOffset, setDragOffset] = useState(0)

  const handleTouchStart = (e: React.TouchEvent<HTMLDivElement>) => {
    const el = popupRef.current
    if (!el || el.scrollTop > 0) return
    dragStartRef.current = { y: e.touches[0].clientY, time: Date.now() }
  }

  const handleTouchMove = (e: React.TouchEvent<HTMLDivElement>) => {
    if (!dragStartRef.current) return
    const delta = e.touches[0].clientY - dragStartRef.current.y
    if (delta > 0) setDragOffset(delta)
  }

  const handleTouchEnd = (e: React.TouchEvent<HTMLDivElement>) => {
    const start = dragStartRef.current
    if (!start) return
    const delta = e.changedTouches[0].clientY - start.y
    const elapsed = Date.now() - start.time
    const velocity = elapsed > 0 ? delta / elapsed : 0
    dragStartRef.current = null
    setDragOffset(0)
    if (delta > SWIPE_CLOSE_DISTANCE || velocity > SWIPE_VELOCITY_THRESHOLD) {
      onClose()
    }
  }

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
          ref={popupRef}
          onTouchStart={handleTouchStart}
          onTouchMove={handleTouchMove}
          onTouchEnd={handleTouchEnd}
          style={
            dragOffset > 0
              ? { transform: `translateY(${dragOffset}px)`, transition: "none" }
              : undefined
          }
          className={cn(
            // Shared
            "fixed z-50 w-full bg-background text-popover-foreground ring-1 ring-foreground/10 outline-none overflow-y-auto",
            // Mobile: bottom sheet
            "inset-x-0 bottom-0 h-[95dvh] max-h-[95dvh] rounded-t-2xl p-4 pt-4",
            "translate-y-full opacity-0 data-open:translate-y-0 data-open:opacity-100 transition-[transform,opacity] duration-200 ease-out",
            // Desktop: centered modal — reset mobile height + position
            "md:h-auto md:max-h-[85dvh] md:bottom-auto md:right-auto md:left-1/2 md:top-1/2 md:-translate-x-1/2 md:-translate-y-1/2 md:data-open:-translate-y-1/2 md:max-w-2xl md:rounded-xl md:p-6",
          )}
        >
          {loading ? (
            <p className="text-sm text-muted-foreground">{t("loading")}</p>
          ) : moment ? (
            <MomentRead
              moment={moment}
              souls={souls}
              collections={collections}
              marks={marks}
              onNavigate={handleNavigate}
              onClose={onClose}
              onDeleted={onClose}
            />
          ) : (
            <p className="text-sm text-muted-foreground">{tMoment("notFoundInline")}</p>
          )}
        </DialogPrimitive.Popup>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  )
}
