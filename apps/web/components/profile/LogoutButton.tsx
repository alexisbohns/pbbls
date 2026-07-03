"use client"

import { useState } from "react"
import { useTranslations } from "next-intl"

interface LogoutButtonProps {
  onLogout: () => Promise<void>
}

/**
 * Full-width logout button — web port of the iOS `ProfileLogoutButton`.
 * Branded accent-surface fill with an accent-primary label (no longer a
 * destructive red).
 */
export function LogoutButton({ onLogout }: LogoutButtonProps) {
  const [isLoggingOut, setIsLoggingOut] = useState(false)
  const t = useTranslations("profile")

  const handleLogout = async () => {
    setIsLoggingOut(true)
    try {
      await onLogout()
    } finally {
      setIsLoggingOut(false)
    }
  }

  return (
    <button
      type="button"
      onClick={handleLogout}
      disabled={isLoggingOut}
      className="w-full rounded-2xl bg-accent px-4 py-3 text-[17px] font-semibold text-primary transition-colors hover:bg-accent/80 disabled:opacity-60"
    >
      {isLoggingOut ? t("loggingOut") : t("logout")}
    </button>
  )
}
