"use client"

import { useTranslations } from "next-intl"
import { SectionLabel } from "@/components/ui/SectionLabel"
import { LogoutButton } from "@/components/profile/LogoutButton"

/**
 * Log out, in Settings (#1384 D20): it left You on every surface. It sits
 * above Delete account, so signing out comes before the destructive action.
 */
export function SignOutSection({ onSignOut }: { onSignOut: () => Promise<void> }) {
  const t = useTranslations("settings.signOut")
  return (
    <section aria-labelledby="settings-sign-out" className="flex flex-col gap-2">
      <SectionLabel id="settings-sign-out">{t("title")}</SectionLabel>
      <LogoutButton onLogout={onSignOut} />
    </section>
  )
}
