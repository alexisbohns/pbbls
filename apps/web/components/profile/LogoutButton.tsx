"use client"

import { useState } from "react"
import { LogOut } from "lucide-react"
import { Button } from "@/components/ui/button"

interface LogoutButtonProps {
  onLogout: () => Promise<void>
}

export function LogoutButton({ onLogout }: LogoutButtonProps) {
  const [isLoggingOut, setIsLoggingOut] = useState(false)

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
      {isLoggingOut ? "Logging out\u2026" : "Log out"}
    </Button>
  )
}
