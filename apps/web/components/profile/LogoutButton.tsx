"use client"

import { useState } from "react"
import { LogOut } from "lucide-react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"

interface LogoutButtonProps {
  onLogout: () => Promise<void>
}

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
    <Button
      variant="destructive"
      disabled={isLoggingOut}
      onClick={handleLogout}
    >
      <LogOut data-icon="inline-start" />
      {isLoggingOut ? t("loggingOut") : t("logout")}
    </Button>
  )
}
