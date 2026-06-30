"use client"

import { useState, type ReactElement } from "react"
import { useTranslations } from "next-intl"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import type { GlyphSubmissionStatus } from "@/lib/types"

type SubmitToCommunityProps = {
  status?: GlyphSubmissionStatus // undefined = not submitted
  reviewNote?: string | null // admin's reason, shown when rejected
  onSubmit: () => Promise<void>
}

export function SubmitToCommunity({ status, reviewNote, onSubmit }: SubmitToCommunityProps) {
  const t = useTranslations("glyphs.submit")
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)

  if (status) {
    const variant =
      status === "approved"
        ? "default"
        : status === "rejected"
          ? "destructive"
          : "secondary"
    const badge = <Badge variant={variant}>{t(status)}</Badge>
    if (status === "rejected" && reviewNote) {
      return (
        <div className="flex flex-col gap-1">
          {badge}
          <p className="text-xs text-muted-foreground">
            {t("rejectedReason", { reason: reviewNote })}
          </p>
        </div>
      )
    }
    return badge
  }

  const confirm = async () => {
    setBusy(true)
    try {
      await onSubmit()
      setOpen(false)
    } finally {
      setBusy(false)
    }
  }

  const trigger: ReactElement = (
    <Button variant="outline" size="sm">
      {t("cta")}
    </Button>
  )

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <AlertDialogTrigger render={trigger} />
      <AlertDialogContent size="sm">
        <AlertDialogHeader>
          <AlertDialogTitle>{t("confirmTitle")}</AlertDialogTitle>
          <AlertDialogDescription>
            {t("confirmDescription")}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={busy}>{t("cancel")}</AlertDialogCancel>
          <AlertDialogAction
            onClick={(e) => {
              e.preventDefault()
              void confirm()
            }}
            disabled={busy}
          >
            {t("confirm")}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
