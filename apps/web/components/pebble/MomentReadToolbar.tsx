"use client"

import { useCallback } from "react"
import { MoreHorizontal, Pencil, Share2, Shapes, Trash2, X } from "lucide-react"
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
  /** Shown only when given (the peek, and the page's way back). */
  onClose?: () => void
}

const ICON_BUTTON =
  "grid size-10 place-items-center rounded-full bg-surface text-muted-foreground transition-colors hover:bg-primary hover:text-surface active:scale-95 outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50"

/**
 * The read page's top bar (R7, PD-W3): Share for a public pebble (the PEBBLE's
 * share URL, `/p/<pebbleId>`), then the overflow menu (Edit moment, Edit
 * pebble when the caller's pebble exists, Delete) and Close.
 */
export function MomentReadToolbar({
  moment,
  busy,
  onEditMoment,
  onEditPebble,
  onDelete,
  onClose,
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
    <header className="flex items-center justify-between">
      <div className="flex items-center gap-2">
        {shareId ? (
          <button type="button" aria-label={tPebble("shareAria")} onClick={() => void handleShare()} className={ICON_BUTTON}>
            <Share2 className="size-4" aria-hidden />
          </button>
        ) : (
          <span className="size-10" aria-hidden />
        )}
      </div>
      <div className="flex items-center gap-2">
        <DropdownMenu>
          <DropdownMenuTrigger aria-label={t("more")} disabled={busy} className={ICON_BUTTON}>
            <MoreHorizontal className="size-4" aria-hidden />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="min-w-[180px]">
            <DropdownMenuItem onClick={onEditMoment}>
              <Pencil aria-hidden />
              {t("editMoment")}
            </DropdownMenuItem>
            {pebble && (
              <DropdownMenuItem onClick={onEditPebble}>
                <Shapes aria-hidden />
                {t("editPebble")}
              </DropdownMenuItem>
            )}
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onClick={onDelete}
              className="text-destructive data-highlighted:bg-destructive/10 data-highlighted:text-destructive"
            >
              <Trash2 aria-hidden />
              {t("delete")}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
        {onClose && (
          <button type="button" onClick={onClose} aria-label={tPebble("peek.close")} className={ICON_BUTTON}>
            <X className="size-4" aria-hidden />
          </button>
        )}
      </div>
    </header>
  )
}
