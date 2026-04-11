"use client"

import { RotateCcw } from "lucide-react"
import { Button } from "@/components/ui/button"
import { ConfirmDialog } from "@/components/ui/ConfirmDialog"
import { useReset } from "@/lib/data/useReset"

export function ResetDataButton() {
  const { reset } = useReset()

  return (
    <ConfirmDialog
      trigger={
        <Button variant="ghost" size="icon" aria-label="Reset to seed data">
          <RotateCcw className="size-4" />
        </Button>
      }
      title="Reset all data?"
      description="This will replace your pebbles, souls, and collections with the original seed data. This cannot be undone."
      confirmLabel="Reset data"
      onConfirm={reset}
    />
  )
}
