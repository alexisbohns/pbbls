"use client"

import { useState } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useTranslations } from "next-intl"
import { toast } from "sonner"
import { useAuth } from "@/lib/data/auth-context"
import type { GateKind } from "@/lib/auth/consent-gate"
import type { ConsentGateState } from "@/lib/data/useConsentGate"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"

type ReconsentScreenProps = {
  state: Exclude<ConsentGateState, { status: "open" }>
  /** Records every listed act; resolves once the gate has re-read the ledger. */
  onSubmit: (missing: GateKind[]) => Promise<void>
  onRetry: () => void
}

const LINK_CLASSES = "underline underline-offset-4 hover:text-foreground"

/**
 * What the consent gate shows: the missing or outdated acts as checkboxes,
 * Continue, and Log out as the way to decline (Android's ConsentGateScreen).
 *
 * The checkbox copy is /register's, word for word: the gate collects the same
 * acts, and the Terms and Privacy rows link to the same documents. Neither the
 * Art. 9 row nor the age row is a document link, for the reasons /register
 * gives. Nothing here offers to withdraw anything — terms, privacy and
 * age_assurance cannot be withdrawn at all.
 */
export function ReconsentScreen({ state, onSubmit, onRetry }: ReconsentScreenProps) {
  const t = useTranslations("consentGate")
  const tRegister = useTranslations("auth.register")
  const { logout } = useAuth()
  const router = useRouter()
  const [ticked, setTicked] = useState<ReadonlySet<GateKind>>(new Set())
  const [loggingOut, setLoggingOut] = useState(false)

  const handleLogout = async () => {
    setLoggingOut(true)
    try {
      await logout()
      router.replace("/")
    } catch (err) {
      console.error("[consent-gate] log out failed:", err)
      toast.error(t("logOutError"))
      setLoggingOut(false)
    }
  }

  if (state.status === "checking") {
    return (
      <div className="flex min-h-dvh items-center justify-center px-6" aria-busy="true">
        <p role="status" className="text-sm text-muted-foreground">
          {t("checking")}
        </p>
      </div>
    )
  }

  const logOutButton = (
    <Button variant="ghost" size="lg" onClick={handleLogout} disabled={loggingOut}>
      {t("logOut")}
    </Button>
  )

  if (state.status === "failed") {
    return (
      <div className="mx-auto flex min-h-dvh w-full max-w-sm flex-col justify-center gap-6 px-6">
        <div className="flex flex-col gap-2 text-left">
          <h1 className="text-2xl font-semibold">{t("title")}</h1>
          <p role="alert" className="text-sm text-muted-foreground">
            {t("loadError")}
          </p>
        </div>
        <div className="flex flex-col gap-2">
          <Button size="lg" onClick={onRetry} disabled={loggingOut}>
            {t("retry")}
          </Button>
          {logOutButton}
        </div>
      </div>
    )
  }

  const { missing, submitting, recordFailed } = state
  const busy = submitting || loggingOut
  const canContinue = !busy && missing.every((kind) => ticked.has(kind))

  const toggle = (kind: GateKind, checked: boolean) => {
    setTicked((prev) => {
      const next = new Set(prev)
      if (checked) next.add(kind)
      else next.delete(kind)
      return next
    })
  }

  const label = (kind: GateKind) => {
    switch (kind) {
      case "terms":
        return (
          <>
            {tRegister("termsPrefix")}
            <Link href="/docs/terms" target="_blank" rel="noopener noreferrer" className={LINK_CLASSES}>
              {tRegister("termsLink")}
            </Link>
          </>
        )
      case "privacy":
        return (
          <>
            {tRegister("privacyPrefix")}
            <Link href="/docs/privacy" target="_blank" rel="noopener noreferrer" className={LINK_CLASSES}>
              {tRegister("privacyLink")}
            </Link>
          </>
        )
      case "health_data":
        return tRegister("healthConsent")
      case "age_assurance":
        return tRegister("ageAttestation")
    }
  }

  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-sm flex-col justify-center gap-6 px-6">
      <div className="flex flex-col gap-2 text-left">
        <h1 id="reconsent-title" className="text-2xl font-semibold">
          {t("title")}
        </h1>
        <p className="text-sm text-muted-foreground">{t("body")}</p>
      </div>

      <div role="group" aria-labelledby="reconsent-title" className="flex flex-col gap-4">
        {missing.map((kind) => (
          <div key={kind} className="flex items-start gap-2 text-left">
            <Checkbox
              id={`reconsent-${kind}`}
              checked={ticked.has(kind)}
              onCheckedChange={(checked) => toggle(kind, checked === true)}
              disabled={busy}
              required
            />
            <label htmlFor={`reconsent-${kind}`} className="text-sm text-muted-foreground">
              {label(kind)}
            </label>
          </div>
        ))}
      </div>

      {recordFailed && (
        <p role="alert" className="text-sm text-destructive">
          {t("recordError")}
        </p>
      )}

      <div className="flex flex-col gap-2">
        <Button size="lg" onClick={() => void onSubmit(missing)} disabled={!canContinue}>
          {submitting ? t("saving") : t("continue")}
        </Button>
        {logOutButton}
      </div>
    </div>
  )
}
