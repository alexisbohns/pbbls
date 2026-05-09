"use client"

import { useSyncExternalStore } from "react"
import { Vibrate, VibrateOff } from "lucide-react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import { useHaptics } from "@/lib/hooks/useHaptics"

function getSupported() {
  return typeof navigator !== "undefined" && "vibrate" in navigator
}

function getServerSupported() {
  return false
}

const noop = () => () => {}

export function HapticsToggle() {
  const { enabled, setEnabled } = useHaptics()
  const supported = useSyncExternalStore(noop, getSupported, getServerSupported)
  const t = useTranslations("profile")

  if (!supported) return null

  return (
    <Button
      variant="ghost"
      size="icon"
      aria-label={enabled ? t("hapticsDisable") : t("hapticsEnable")}
      aria-pressed={enabled}
      onClick={() => setEnabled(!enabled)}
    >
      {enabled ? (
        <Vibrate className="size-4" />
      ) : (
        <VibrateOff className="size-4" />
      )}
    </Button>
  )
}
