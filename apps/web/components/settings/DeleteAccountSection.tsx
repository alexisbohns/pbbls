"use client"

import { useState } from "react"
import { Trash2 } from "lucide-react"
import { toast } from "sonner"
import { useTranslations } from "next-intl"
import { useAuth } from "@/lib/data/auth-context"
import { SectionLabel } from "@/components/ui/SectionLabel"
import { SettingsGroup } from "@/components/settings/SettingsGroup"
import { SettingsRow } from "@/components/settings/SettingsRow"
import { Button } from "@/components/ui/button"
import { ReauthDialog } from "@/components/settings/ReauthDialog"
import { isReauthRequired } from "@/lib/auth/recent-auth"
import { stashPendingReauth } from "@/lib/auth/pending-reauth"
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"

type DeleteAccountSectionProps = {
  /** Called after the account is deleted and the local session is cleared. */
  onDeleted: () => void
  /** Back from a provider re-auth that was started here: reopen the confirmation. */
  resumeAfterReauth?: boolean
  /** Where a provider re-auth comes back to. */
  reauthReturnTo: string
}

/**
 * The "Delete account" settings entry (M46). Store policy requires account
 * deletion to be easy to find, hence a dedicated section at the bottom of
 * Settings on every surface.
 *
 * Built on the AlertDialog primitives rather than ConfirmDialog: the dialog
 * must stay open in a busy state while the delete-account edge function runs,
 * and ConfirmDialog's AlertDialogAction closes the dialog on click.
 *
 * Deletion needs a recent sign-in (#976, #977): a stale session goes through
 * "Confirm it's you" between the confirmation and the call, and a server 428
 * (clock skew, or a sign-in that aged while the dialog was up) goes back there.
 */
export function DeleteAccountSection({ onDeleted, resumeAfterReauth = false, reauthReturnTo }: DeleteAccountSectionProps) {
  const { user, deleteAccount, isSignInRecent } = useAuth()
  const t = useTranslations("settings")
  const tCommon = useTranslations("common")
  const [open, setOpen] = useState(resumeAfterReauth)
  const [deleting, setDeleting] = useState(false)
  const [reauthOpen, setReauthOpen] = useState(false)

  const askToReauth = () => {
    setDeleting(false)
    setOpen(false)
    setReauthOpen(true)
  }

  const runDelete = async () => {
    setOpen(true)
    setDeleting(true)
    try {
      await deleteAccount()
      onDeleted()
    } catch (err) {
      if (isReauthRequired(err)) {
        askToReauth()
        return
      }
      console.error("[settings] account deletion failed:", err)
      toast.error(t("deleteAccountError"))
      setDeleting(false)
      setOpen(false)
    }
  }

  const handleConfirm = async () => {
    setDeleting(true)
    if (!(await isSignInRecent())) {
      askToReauth()
      return
    }
    await runDelete()
  }

  return (
    <section className="flex flex-col gap-2">
      <SectionLabel id="settings-account">{t("account")}</SectionLabel>
      <SettingsGroup aria-labelledby="settings-account">
        <SettingsRow onClick={() => setOpen(true)}>
          <span className="flex items-center gap-3 text-destructive">
            <Trash2 className="size-5 shrink-0" aria-hidden />
            {t("deleteAccount")}
          </span>
        </SettingsRow>
      </SettingsGroup>

      <AlertDialog open={open} onOpenChange={(next) => !deleting && setOpen(next)}>
        <AlertDialogContent size="sm">
          <AlertDialogHeader>
            <AlertDialogTitle>{t("deleteAccountTitle")}</AlertDialogTitle>
            <AlertDialogDescription>{t("deleteAccountBody")}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleting}>{tCommon("cancel")}</AlertDialogCancel>
            <Button variant="destructive" disabled={deleting} onClick={handleConfirm}>
              {deleting ? t("deleteAccountDeleting") : t("deleteAccountConfirm")}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <ReauthDialog
        open={reauthOpen}
        returnTo={reauthReturnTo}
        onBeforeRedirect={() => {
          if (user) stashPendingReauth({ purpose: "delete", userId: user.id, savedAt: Date.now() })
        }}
        onCancel={() => setReauthOpen(false)}
        onConfirmed={() => {
          setReauthOpen(false)
          void runDelete()
        }}
      />
    </section>
  )
}
