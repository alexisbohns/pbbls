"use client"

import { useState } from "react"
import { HeartHandshake } from "lucide-react"
import { toast } from "sonner"
import { useTranslations } from "next-intl"
import { useAuth } from "@/lib/data/auth-context"
import { useFormatDate } from "@/lib/i18n/format"
import { SectionLabel } from "@/components/ui/SectionLabel"
import { SettingsGroup } from "@/components/settings/SettingsGroup"
import { SettingsRow } from "@/components/settings/SettingsRow"
import { Button } from "@/components/ui/button"
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import type { ConsentRow } from "@/lib/data/consent"

type ConsentSectionProps = {
  /** The live health-data consent, or null if it was never recorded. */
  consent: ConsentRow | null
  /** Called after the account is deleted and the local session is cleared. */
  onWithdrawn: () => void
}

/**
 * Art. 9 consent, and the Art. 7(3) right to take it back.
 *
 * Withdrawal routes into the existing account-deletion flow rather than a
 * toggle, because this consent is the lawful basis for the product's core
 * activity: there is no version of Pebbles that keeps your journal and stops
 * processing it. The dialog says exactly that. Burying the consequence behind a
 * friendly toggle would be the dark pattern; stating it is the honest version.
 *
 * The public-profile consent is deliberately NOT mirrored here — it is one
 * control, and it lives in PublicProfileSection where the handle it depends on
 * is edited.
 *
 * Built on the AlertDialog primitives rather than ConfirmDialog for the same
 * reason DeleteAccountSection is: the dialog must stay open in a busy state
 * while the delete-account edge function runs, and ConfirmDialog's
 * AlertDialogAction closes on click.
 */
export function ConsentSection({ consent, onWithdrawn }: ConsentSectionProps) {
  const { deleteAccount } = useAuth()
  const t = useTranslations("settings.consent")
  const tCommon = useTranslations("common")
  const formatDate = useFormatDate()
  const [open, setOpen] = useState(false)
  const [withdrawing, setWithdrawing] = useState(false)

  const handleConfirm = async () => {
    setWithdrawing(true)
    try {
      await deleteAccount()
      onWithdrawn()
    } catch (err) {
      console.error("[settings] consent withdrawal failed:", err)
      toast.error(t("withdrawError"))
      setWithdrawing(false)
      setOpen(false)
    }
  }

  return (
    <section className="flex flex-col gap-2">
      <SectionLabel id="settings-consent">{t("title")}</SectionLabel>
      <SettingsGroup aria-labelledby="settings-consent">
        <SettingsRow
          icon={HeartHandshake}
          trailing={
            consent ? (
              <Button variant="ghost" size="sm" onClick={() => setOpen(true)}>
                {t("withdraw")}
              </Button>
            ) : null
          }
        >
          <span className="flex flex-col text-left">
            <span>{t("healthData")}</span>
            <span className="text-xs font-normal text-muted-foreground">
              {consent
                ? t("grantedOn", {
                    date: formatDate(consent.granted_at),
                    version: consent.document_version,
                  })
                : t("notRecorded")}
            </span>
          </span>
        </SettingsRow>
      </SettingsGroup>

      {/* Dismissal is blocked while the delete runs — the request is already in
          flight and closing the dialog would strand the user on a live page. */}
      <AlertDialog open={open} onOpenChange={(next) => !withdrawing && setOpen(next)}>
        <AlertDialogContent size="sm">
          <AlertDialogHeader>
            <AlertDialogTitle>{t("confirmTitle")}</AlertDialogTitle>
            <AlertDialogDescription>{t("confirmBody")}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={withdrawing}>{tCommon("cancel")}</AlertDialogCancel>
            <Button variant="destructive" disabled={withdrawing} onClick={handleConfirm}>
              {withdrawing ? t("withdrawing") : t("confirmAction")}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  )
}
