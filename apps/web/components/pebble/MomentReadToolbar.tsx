"use client"

import { useCallback } from "react"
import { MoreHorizontal, Share2 } from "lucide-react"
import { DrawDotted, PebbleRound, Trash } from "@pbbls/icons"
import { toast } from "sonner"
import { useTranslations } from "next-intl"
import type { Moment } from "@/lib/types"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"

type MomentReadToolbarProps = {
  moment: Moment
  /** Locks the menu while a delete is in flight. */
  busy: boolean
  onEditMoment: () => void
  onEditPebble: () => void
  onDelete: () => void
}

// M3 standard icon buttons (#1384, two-area spec D5): 44 px, the state layer
// on hover, the ring on focus.
const ICON_BUTTON =
  "grid size-11 place-items-center rounded-full text-m3-on-surface-variant transition-colors hover:bg-m3-surface-container-high outline-none focus-visible:ring-2 focus-visible:ring-m3-primary disabled:pointer-events-none disabled:opacity-40 motion-reduce:transition-none"

/**
 * The read page's actions (R7, PD-W3), on the detail's top bar since #1384:
 * Share for a public pebble (the PEBBLE's share URL, `/p/<pebbleId>`), then
 * the overflow menu (Edit moment, Edit pebble when the caller's pebble
 * exists, Delete). The pane's own control closes it.
 */
export function MomentReadToolbar({
  moment,
  busy,
  onEditMoment,
  onEditPebble,
  onDelete,
}: MomentReadToolbarProps) {
  const t = useTranslations("moment.read")
  const tPebble = useTranslations("pebble")
  const pebble = moment.pebble
  const shareId = pebble?.visibility === "public" ? pebble.id : null

  const handleShare = useCallback(async () => {
    if (!shareId) return
    const url = `${window.location.origin}/p/${shareId}`
    // navigator.share needs a secure context and a user gesture; when it is
    // unavailable we fall back to copying the link.
    try {
      await navigator.share({ url })
    } catch (err) {
      // The user dismissed the share sheet: not a failure, nothing to copy.
      if (err instanceof DOMException && err.name === "AbortError") return
      try {
        await navigator.clipboard.writeText(url)
        toast.success(tPebble("shareCopied"))
      } catch (copyErr) {
        console.error("[moment-read] share failed", copyErr)
      }
    }
  }, [shareId, tPebble])

  return (
    <div className="flex items-center">
      {shareId && (
        <button type="button" aria-label={tPebble("shareAria")} onClick={() => void handleShare()} className={ICON_BUTTON}>
          <Share2 className="size-5" aria-hidden />
        </button>
      )}
      <DropdownMenu>
        <DropdownMenuTrigger aria-label={t("more")} disabled={busy} className={ICON_BUTTON}>
          <MoreHorizontal className="size-5" aria-hidden />
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="min-w-[180px]">
          <DropdownMenuItem onClick={onEditMoment}>
            <DrawDotted />
            {t("editMoment")}
          </DropdownMenuItem>
          {pebble && (
            <DropdownMenuItem onClick={onEditPebble}>
              <PebbleRound />
              {t("editPebble")}
            </DropdownMenuItem>
          )}
          <DropdownMenuSeparator />
          <DropdownMenuItem
            onClick={onDelete}
            className="text-destructive data-highlighted:bg-destructive/10 data-highlighted:text-destructive"
          >
            <Trash />
            {t("delete")}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  )
}
