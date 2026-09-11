"use client"

import { useState } from "react"
import { toast } from "sonner"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"

type ConsentGateProps = {
  /** Records the Art. 9 consent. Resolves once the ledger row exists. */
  onAccept: () => Promise<void>
}

/**
 * Art. 9 consent for accounts that reached onboarding without one.
 *
 * Only the login page's OAuth buttons can produce such an account: /register
 * gates all three of its paths on the consent checkbox. This is a backstop for
 * that one route, not a gate every signup passes through — which is why it
 * lives inside onboarding rather than ahead of it.
 */
export function ConsentGate({ onAccept }: ConsentGateProps) {
  const t = useTranslations("onboarding.consent")
  const [accepted, setAccepted] = useState(false)
  const [saving, setSaving] = useState(false)

  const handleAccept = async () => {
    setSaving(true)
    try {
      await onAccept()
      // No `setSaving(false)` on success: the gate unmounts as soon as the
      // parent re-renders with the new consent row, and clearing the flag
      // first would flash the button back to its enabled state.
    } catch (err) {
      console.error("[onboarding] consent record failed:", err)
      toast.error(t("error"))
      setSaving(false)
    }
  }

  return (
    <div className="flex min-h-dvh flex-col justify-center gap-6 px-6">
      <div className="flex flex-col gap-2 text-left">
        <h1 className="text-2xl font-semibold">{t("title")}</h1>
        <p className="text-sm text-muted-foreground">{t("body")}</p>
      </div>

      <div className="flex items-start gap-2 text-left">
        <Checkbox
          id="onboarding-health-consent"
          checked={accepted}
          onCheckedChange={(checked) => setAccepted(checked === true)}
          disabled={saving}
          required
        />
        <label
          htmlFor="onboarding-health-consent"
          className="text-sm text-muted-foreground"
        >
          {t("checkbox")}
        </label>
      </div>

      <Button size="lg" onClick={handleAccept} disabled={saving || !accepted}>
        {saving ? t("saving") : t("continue")}
      </Button>
    </div>
  )
}
