"use client"

import { useState } from "react"
import { useTranslations } from "next-intl"
import { useAuth } from "@/lib/data/auth-context"
import { reauthMethod } from "@/lib/auth/recent-auth"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"

type ReauthDialogProps = {
  open: boolean
  /** The password re-sign-in landed: run the pending action. */
  onConfirmed: () => void
  /** Closed without confirming: abandon the pending action, change nothing. */
  onCancel: () => void
  /**
   * Provider accounts only: called right before the OAuth redirect, so the
   * caller can stash its pending action (`lib/auth/pending-reauth.ts`).
   */
  onBeforeRedirect: () => void
  /** Where the OAuth callback brings the person back. Strictly relative. */
  returnTo: string
}

/**
 * "Confirm it's you" (#976, #977): the recent sign-in step before account
 * deletion, a password change, or turning the public profile on. Web port of
 * Android's ReauthDialog.
 *
 * Accounts with an email identity re-enter their password and stay on the
 * page. Google- or Apple-only accounts re-run their provider through a
 * full-page redirect, so the action resumes from the stash on return.
 */
export function ReauthDialog({ open, onConfirmed, onCancel, onBeforeRedirect, returnTo }: ReauthDialogProps) {
  const { user, reauthenticate, reauthenticateWithProvider } = useAuth()
  const t = useTranslations("settings")
  const tCommon = useTranslations("common")
  const [password, setPassword] = useState("")
  const [working, setWorking] = useState(false)
  const [error, setError] = useState<"wrong" | "other" | null>(null)

  const method = reauthMethod(user?.providers ?? [])

  const close = () => {
    setPassword("")
    setError(null)
    setWorking(false)
    onCancel()
  }

  const submit = async () => {
    if (working) return
    if (method === "password" && password.length === 0) return
    setWorking(true)
    setError(null)
    try {
      if (method === "password") {
        await reauthenticate(password)
        setPassword("")
        setWorking(false)
        onConfirmed()
      } else {
        onBeforeRedirect()
        // Navigates away on success; `working` stays on until the page unloads.
        await reauthenticateWithProvider(method, returnTo)
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err)
      console.error("[settings] re-auth failed:", message)
      setPassword("")
      setError(message === "invalid_credentials" ? "wrong" : "other")
      setWorking(false)
    }
  }

  const description =
    method === "password"
      ? t("reauthMessagePassword")
      : method === "google"
        ? t("reauthMessageGoogle")
        : t("reauthMessageApple")

  const confirmLabel = working
    ? t("reauthConfirming")
    : method === "password"
      ? t("reauthConfirm")
      : method === "google"
        ? t("reauthContinueGoogle")
        : t("reauthContinueApple")

  return (
    <AlertDialog open={open} onOpenChange={(next) => !next && !working && close()}>
      <AlertDialogContent size="sm">
        <form
          className="contents"
          onSubmit={(e) => {
            e.preventDefault()
            void submit()
          }}
        >
          <AlertDialogHeader>
            <AlertDialogTitle>{t("reauthTitle")}</AlertDialogTitle>
            <AlertDialogDescription>{description}</AlertDialogDescription>
          </AlertDialogHeader>
          {method === "password" && (
            <div className="flex flex-col gap-1.5">
              <label htmlFor="reauth-password" className="sr-only">
                {t("reauthPasswordLabel")}
              </label>
              <Input
                id="reauth-password"
                type="password"
                // The saved password is exactly what this asks for.
                autoComplete="current-password"
                placeholder={t("reauthPasswordLabel")}
                value={password}
                onChange={(e) => {
                  setPassword(e.target.value)
                  setError(null)
                }}
                disabled={working}
                aria-invalid={error !== null}
                aria-describedby={error ? "reauth-error" : undefined}
                autoFocus
              />
            </div>
          )}
          {error && (
            // Appears after a submit, away from focus: announce it.
            <p id="reauth-error" role="alert" className="text-xs text-destructive">
              {error === "wrong" ? t("reauthWrongPassword") : t("reauthError")}
            </p>
          )}
          <AlertDialogFooter>
            <AlertDialogCancel type="button" disabled={working}>
              {tCommon("cancel")}
            </AlertDialogCancel>
            <Button type="submit" disabled={working || (method === "password" && password.length === 0)}>
              {confirmLabel}
            </Button>
          </AlertDialogFooter>
        </form>
      </AlertDialogContent>
    </AlertDialog>
  )
}
