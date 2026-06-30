"use client"

import { useState, type ReactElement } from "react"
import { useTranslations } from "next-intl"
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
  AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog"

type ErrorKey =
  | "insufficient"
  | "notInMarket"
  | "cannotBuyOwn"
  | "alreadyOwned"
  | "generic"

function messageKey(error: unknown): ErrorKey {
  const msg = error instanceof Error ? error.message : ""
  if (msg.includes("insufficient_karma")) return "insufficient"
  if (msg.includes("not_in_market")) return "notInMarket"
  if (msg.includes("cannot_buy_own")) return "cannotBuyOwn"
  if (msg.includes("already_owned")) return "alreadyOwned"
  return "generic"
}

type BuyGlyphDialogProps = {
  trigger: ReactElement
  amount: number
  onBuy: () => Promise<void>
}

export function BuyGlyphDialog({ trigger, amount, onBuy }: BuyGlyphDialogProps) {
  const t = useTranslations("market")
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [errorKey, setErrorKey] = useState<ErrorKey | null>(null)

  const handleConfirm = async () => {
    setBusy(true)
    setErrorKey(null)
    try {
      await onBuy()
      setOpen(false)
    } catch (e) {
      setErrorKey(messageKey(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <AlertDialogTrigger render={trigger} />
      <AlertDialogContent size="sm">
        <AlertDialogHeader>
          <AlertDialogTitle>{t("buyTitle")}</AlertDialogTitle>
          <AlertDialogDescription>{t("buyDescription", { amount })}</AlertDialogDescription>
        </AlertDialogHeader>
        {errorKey && (
          <p role="alert" className="text-sm text-destructive">
            {t(`errors.${errorKey}`)}
          </p>
        )}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={busy}>{t("cancel")}</AlertDialogCancel>
          <AlertDialogAction
            onClick={(e) => {
              e.preventDefault() // keep the dialog open to show errors / busy state
              void handleConfirm()
            }}
            disabled={busy}
          >
            {t("buyConfirm", { amount })}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
