"use client"

import { useState, useCallback } from "react"
import { useTranslations } from "next-intl"
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
import { MODE_EMOJI, MODE_KEYS } from "@/components/collections/ModeBadge"

type CollectionFormDialogProps = {
  trigger: React.ReactElement
  title: string
  submitLabel: string
  initialName?: string
  initialMode?: Collection["mode"]
  onSubmit: (data: { name: string; mode?: Collection["mode"] }) => void
}

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
  const t = useTranslations("collections.form")
  const tModes = useTranslations("collections.modes")

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
              {t("nameLabel")}
            </label>
            <Input
              id="collection-name"
              placeholder={t("namePlaceholder")}
              value={name}
              onChange={(e) => setName(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && canSubmit) handleSubmit()
              }}
              autoFocus
            />
          </div>

          <fieldset className="grid gap-1.5">
            <legend className="text-sm font-medium">{t("modeLegend")}</legend>
            <div className="flex gap-2">
              {MODE_KEYS.map((key) => (
                <Button
                  key={key}
                  type="button"
                  variant={mode === key ? "secondary" : "outline"}
                  size="sm"
                  onClick={() => setMode(mode === key ? undefined : key)}
                  aria-pressed={mode === key}
                >
                  <span aria-hidden="true">{MODE_EMOJI[key]}</span> {tModes(key)}
                </Button>
              ))}
            </div>
          </fieldset>
        </div>

        <AlertDialogFooter>
          <AlertDialogCancel>{t("cancel")}</AlertDialogCancel>
          <AlertDialogAction disabled={!canSubmit} onClick={handleSubmit}>
            {submitLabel}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
