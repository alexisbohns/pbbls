"use client"

import { useState, useCallback } from "react"
import type { Collection } from "@/lib/types"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog"
import { MODE_META } from "@/components/collections/ModeBadge"

type CollectionFormDialogProps = {
  trigger: React.ReactElement
  title: string
  submitLabel: string
  initialName?: string
  initialMode?: Collection["mode"]
  onSubmit: (data: { name: string; mode?: Collection["mode"] }) => void
}

const MODES = Object.entries(MODE_META) as [
  NonNullable<Collection["mode"]>,
  { emoji: string; label: string },
][]

export function CollectionFormDialog({
  trigger,
  title,
  submitLabel,
  initialName = "",
  initialMode,
  onSubmit,
}: CollectionFormDialogProps) {
  const [open, setOpen] = useState(false)
  const [name, setName] = useState(initialName)
  const [mode, setMode] = useState<Collection["mode"]>(initialMode)

  const resetForm = useCallback(() => {
    setName(initialName)
    setMode(initialMode)
  }, [initialName, initialMode])

  const handleOpenChange = useCallback(
    (nextOpen: boolean) => {
      setOpen(nextOpen)
      if (nextOpen) resetForm()
    },
    [resetForm],
  )

  const handleSubmit = useCallback(() => {
    const trimmed = name.trim()
    if (!trimmed) return
    onSubmit({ name: trimmed, mode })
    setOpen(false)
  }, [name, mode, onSubmit])

  const canSubmit = name.trim().length > 0

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <AlertDialogTrigger render={trigger} />
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
        </AlertDialogHeader>

        <div className="grid gap-4">
          <div className="grid gap-1.5">
            <label htmlFor="collection-name" className="text-sm font-medium">
              Name
            </label>
            <Input
              id="collection-name"
              placeholder="e.g. Morning gratitudes"
              value={name}
              onChange={(e) => setName(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && canSubmit) handleSubmit()
              }}
              autoFocus
            />
          </div>

          <fieldset className="grid gap-1.5">
            <legend className="text-sm font-medium">Mode (optional)</legend>
            <div className="flex gap-2">
              {MODES.map(([key, meta]) => (
                <Button
                  key={key}
                  type="button"
                  variant={mode === key ? "secondary" : "outline"}
                  size="sm"
                  onClick={() => setMode(mode === key ? undefined : key)}
                  aria-pressed={mode === key}
                >
                  <span aria-hidden="true">{meta.emoji}</span> {meta.label}
                </Button>
              ))}
            </div>
          </fieldset>
        </div>

        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <AlertDialogAction disabled={!canSubmit} onClick={handleSubmit}>
            {submitLabel}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
