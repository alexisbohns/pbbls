"use client"

import { Trash2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { ConfirmDialog } from "@/components/ui/ConfirmDialog"

type DeleteSoulDialogProps = {
  soulName: string
  onConfirm: () => void
}

export function DeleteSoulDialog({ soulName, onConfirm }: DeleteSoulDialogProps) {
  return (
    <ConfirmDialog
      trigger={
        <Button
          variant="ghost"
          size="icon-xs"
          aria-label={`Delete ${soulName}`}
        >
          <Trash2 className="size-3.5" />
        </Button>
      }
      title="Delete soul?"
      description={`This will remove ${soulName} from your directory and unlink them from all pebbles. This cannot be undone.`}
      confirmLabel="Delete"
      onConfirm={onConfirm}
    />
  )
}
