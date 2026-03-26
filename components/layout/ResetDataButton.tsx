"use client"

import { RotateCcw } from "lucide-react"
import { Button } from "@/components/ui/button"
import { useDataProvider } from "@/lib/data/provider-context"

/**
 * Header action that resets all data to the approved seed dataset.
 * window.confirm() is the deliberate low-cost confirmation for the MVP;
 * it always runs inside the click handler — never at module scope.
 */
export function ResetDataButton() {
  const { provider, setStore } = useDataProvider()

  const handleReset = async () => {
    if (!window.confirm("Reset all data to seed? This cannot be undone.")) return
    const snapshot = await provider.reset()
    setStore(snapshot)
  }

  return (
    <Button
      variant="ghost"
      size="icon"
      aria-label="Reset to seed data"
      onClick={handleReset}
    >
      <RotateCcw className="size-4" />
    </Button>
  )
}
