"use client"

import { useState } from "react"
import { useTranslations } from "next-intl"
import { ListGroupHeader } from "@/components/m3/ListGroupHeader"
import { SettingsGroup } from "@/components/settings/SettingsGroup"
import { SettingsRow } from "@/components/settings/SettingsRow"

/**
 * Log out, in Settings (#1384 D20): it left You on every surface. Its own
 * group at the very end, a plain list item in `onSurface` (no error role),
 * as Android sets it (#1437).
 */
export function SignOutSection({ onSignOut }: { onSignOut: () => Promise<void> }) {
  const t = useTranslations("settings.signOut")
  const tProfile = useTranslations("profile")
  const [signingOut, setSigningOut] = useState(false)

  const signOut = async () => {
    setSigningOut(true)
    try {
      await onSignOut()
    } finally {
      setSigningOut(false)
    }
  }

  return (
    <section aria-labelledby="settings-sign-out" className="flex flex-col gap-2">
      <ListGroupHeader id="settings-sign-out">{t("title")}</ListGroupHeader>
      <SettingsGroup aria-labelledby="settings-sign-out">
        <SettingsRow onClick={signingOut ? undefined : signOut}>{signingOut ? tProfile("loggingOut") : tProfile("logout")}</SettingsRow>
      </SettingsGroup>
    </section>
  )
}
