"use client"

import { useState } from "react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"

type UnfoldDialogProps = {
  /** The folded month's name and count; null closes the dialog. */
  month: { label: string; count: number } | null
  onUnfold: () => Promise<void>
  onClose: () => void
}

/** Unfolding a month (#1384, spec D13; 2026-10-26 D5): its count and one filled button, which reveals it once. */
export function UnfoldDialog({ month, onUnfold, onClose }: UnfoldDialogProps) {
  const t = useTranslations("home.wraps")
  const [busy, setBusy] = useState(false)
  const [failed, setFailed] = useState(false)
  return (
    <Dialog
      open={month !== null}
      onOpenChange={(open) => {
        if (!open && !busy) {
          setFailed(false)
          onClose()
        }
      }}
    >
      <DialogContent className="max-sm:top-auto max-sm:bottom-0 max-sm:max-w-none max-sm:translate-y-0 max-sm:rounded-b-none max-sm:pb-[calc(1rem+var(--safe-area-bottom))]">
        {month && (
          <>
            <DialogHeader>
              <DialogTitle className="pb-type-card-title">{t("unfoldTitle", { month: month.label })}</DialogTitle>
              <DialogDescription className="pb-type-row-line">{t("unfoldBody", { count: month.count })}</DialogDescription>
            </DialogHeader>
            {failed && (
              <p role="alert" className="pb-type-row-line italic text-m3-on-surface-variant">
                {t("unfoldFailed")}
              </p>
            )}
            <DialogFooter>
              <Button
                disabled={busy}
                className="h-10 rounded-full px-5"
                onClick={async () => {
                  setBusy(true)
                  setFailed(false)
                  try {
                    await onUnfold()
                    onClose()
                  } catch {
                    setFailed(true)
                  } finally {
                    setBusy(false)
                  }
                }}
              >
                {t("unfold")}
              </Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
