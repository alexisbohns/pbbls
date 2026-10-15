"use client"

import { useState } from "react"
import { Loader2 } from "lucide-react"
import { toast } from "sonner"
import { useTranslations } from "next-intl"
import type { Moment } from "@/lib/types"
import { useMoments } from "@/lib/data/useMoments"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"

type Pending = "pebble" | "moment" | null

type MomentDeleteDialogProps = {
  moment: Moment
  open: boolean
  onOpenChange: (open: boolean) => void
  /** The moment is gone: the page leaves, the peek closes. */
  onDeleted: () => void
  /** Mirrors the write in flight so the host can lock its own actions. */
  onPendingChange?: (pending: boolean) => void
}

/**
 * The one delete confirmation (R8), ported from iOS `MomentDeleteDialog`. A
 * bare moment offers "Delete moment"; a moment carrying the caller's pebble
 * offers "Delete the pebble, keep the moment" (`delete_pebble`, the page stays
 * as a bare moment) and "Delete the moment too" (`delete_moment`). A failure
 * says "Couldn't delete" (R15, PD-W13) and leaves the dialog open.
 */
export function MomentDeleteDialog({
  moment,
  open,
  onOpenChange,
  onDeleted,
  onPendingChange,
}: MomentDeleteDialogProps) {
  const t = useTranslations("moment.delete")
  const tErrors = useTranslations("moment.errors")
  const { deleteMoment, deletePebble } = useMoments()
  const [pending, setPendingState] = useState<Pending>(null)
  // Which variant the dialog shows is decided when it opens and held until it
  // opens again: once "Delete the pebble, keep the moment" succeeds the moment
  // turns bare, and the closing dialog must not re-render as the bare variant
  // during its fade.
  const [shown, setShown] = useState<{ open: boolean; pebbleId: string | null }>({
    open,
    pebbleId: moment.pebble?.id ?? null,
  })
  if (shown.open !== open) {
    setShown({ open, pebbleId: open ? (moment.pebble?.id ?? null) : shown.pebbleId })
  }
  const pebbleId = shown.pebbleId
  const hasPebble = pebbleId !== null

  const setPending = (next: Pending) => {
    setPendingState(next)
    onPendingChange?.(next !== null)
  }

  const run = async (which: Exclude<Pending, null>) => {
    if (pending) return
    setPending(which)
    try {
      if (which === "pebble") {
        // Never fall through to deleting the moment when the pebble is gone.
        if (!pebbleId) return
        await deletePebble(pebbleId)
        onOpenChange(false)
      } else {
        await deleteMoment(moment.id)
        onOpenChange(false)
        onDeleted()
      }
    } catch (err) {
      console.error(`[moment-delete] delete_${which} failed`, err)
      toast.error(tErrors("deleteTitle"), { description: tErrors("deleteMessage") })
    } finally {
      setPending(null)
    }
  }

  const spinner = <Loader2 className="size-4 animate-spin" aria-hidden />

  return (
    <AlertDialog open={open} onOpenChange={(next) => !pending && onOpenChange(next)}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{hasPebble ? t("pebbleTitle") : t("bareTitle")}</AlertDialogTitle>
          <AlertDialogDescription>{hasPebble ? t("pebbleMessage") : t("bareMessage")}</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter className={hasPebble ? "sm:flex-col-reverse sm:justify-start" : undefined}>
          <AlertDialogCancel disabled={pending !== null}>{t("cancel")}</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            disabled={pending !== null}
            aria-busy={pending === "moment"}
            onClick={() => void run("moment")}
          >
            {pending === "moment" && spinner}
            {hasPebble ? t("deleteMomentToo") : t("deleteMoment")}
          </AlertDialogAction>
          {hasPebble && (
            <AlertDialogAction
              variant="destructive"
              disabled={pending !== null}
              aria-busy={pending === "pebble"}
              onClick={() => void run("pebble")}
            >
              {pending === "pebble" && spinner}
              {t("deletePebbleKeepMoment")}
            </AlertDialogAction>
          )}
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
