"use client"

import { useState, useCallback, useRef } from "react"
import { useTranslations } from "next-intl"
import { toast } from "sonner"
import type { Collection } from "@/lib/types"
import { useUsableGlyphs } from "@/lib/data/useUsableGlyphs"
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
import { GlyphPickerDialog } from "@/components/record/GlyphPickerDialog"
import { SoulGlyphThumbnail } from "@/components/souls/SoulGlyphThumbnail"

type CollectionFormValues = {
  name: string
  mode?: Collection["mode"]
  /** Null for none; written in the same insert/update as name and mode (R11). */
  glyph_id: string | null
}

type CollectionFormDialogProps = {
  trigger: React.ReactElement
  title: string
  submitLabel: string
  initialName?: string
  initialMode?: Collection["mode"]
  initialGlyphId?: string | null
  /** Rejects on a refused write; the dialog then stays open and says so. */
  onSubmit: (data: CollectionFormValues) => Promise<void>
}

export function CollectionFormDialog({
  trigger,
  title,
  submitLabel,
  initialName = "",
  initialMode,
  initialGlyphId = null,
  onSubmit,
}: CollectionFormDialogProps) {
  const [open, setOpen] = useState(false)
  const [name, setName] = useState(initialName)
  const [mode, setMode] = useState<Collection["mode"]>(initialMode)
  // Held as the id, not the glyph: a glyph the picker no longer lists is kept
  // until the user changes or removes it.
  const [glyphId, setGlyphId] = useState<string | null>(initialGlyphId)
  const [pickerOpen, setPickerOpen] = useState(false)
  // "Remove glyph" unmounts itself; focus lands back on the choose button.
  const chooseGlyphRef = useRef<HTMLButtonElement>(null)
  const [submitting, setSubmitting] = useState(false)
  // The modal aria-hides the in-tree Toaster, so a refused save is also
  // announced inside the dialog; cleared on the next submit or edit.
  const [saveFailed, setSaveFailed] = useState(false)
  const { glyphs } = useUsableGlyphs()
  const t = useTranslations("collections.form")
  const tModes = useTranslations("collections.modes")
  const tErrors = useTranslations("moment.errors")

  const glyph = glyphId ? glyphs.find((g) => g.id === glyphId) : undefined

  const resetForm = useCallback(() => {
    setName(initialName)
    setMode(initialMode)
    setGlyphId(initialGlyphId)
    setSaveFailed(false)
  }, [initialName, initialMode, initialGlyphId])

  const handleOpenChange = useCallback(
    (nextOpen: boolean) => {
      if (submitting) return
      setOpen(nextOpen)
      if (nextOpen) resetForm()
    },
    [resetForm, submitting],
  )

  const handleSubmit = useCallback(async () => {
    const trimmed = name.trim()
    if (!trimmed || submitting) return
    setSubmitting(true)
    setSaveFailed(false)
    try {
      await onSubmit({ name: trimmed, mode, glyph_id: glyphId })
      setOpen(false)
    } catch (err) {
      // A glyph the owner cannot use is refused by the usability trigger
      // (42501); that and any other refusal read as the generic copy.
      console.error("[collections] save failed", err)
      toast.error(tErrors("generic"))
      setSaveFailed(true)
    } finally {
      setSubmitting(false)
    }
  }, [name, mode, glyphId, submitting, onSubmit, tErrors])

  const canSubmit = name.trim().length > 0 && !submitting

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
              onChange={(e) => {
                setName(e.target.value)
                setSaveFailed(false)
              }}
              onKeyDown={(e) => {
                if (e.key === "Enter" && canSubmit) void handleSubmit()
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
                  onClick={() => {
                    setMode(mode === key ? undefined : key)
                    setSaveFailed(false)
                  }}
                  aria-pressed={mode === key}
                >
                  <span aria-hidden="true">{MODE_EMOJI[key]}</span> {tModes(key)}
                </Button>
              ))}
            </div>
          </fieldset>

          <fieldset className="grid gap-1.5" aria-describedby="collection-glyph-footer">
            <legend className="text-sm font-medium">{t("glyphLabel")}</legend>
            <div className="flex flex-wrap items-center gap-2">
              <Button
                ref={chooseGlyphRef}
                type="button"
                variant="outline"
                onClick={() => setPickerOpen(true)}
              >
                <SoulGlyphThumbnail mark={glyph} className="size-5" />
                {glyphId ? t("glyphChange") : t("glyphChoose")}
                {glyph?.name && (
                  <span className="sr-only">, {t("glyphCurrent", { name: glyph.name })}</span>
                )}
              </Button>
              {glyphId && (
                <Button type="button" variant="ghost" onClick={() => {
                  setGlyphId(null)
                  setSaveFailed(false)
                  chooseGlyphRef.current?.focus()
                }}>
                  {t("removeGlyph")}
                </Button>
              )}
            </div>
            <p id="collection-glyph-footer" className="text-xs text-muted-foreground">
              {t("glyphFooter")}
            </p>
          </fieldset>
        </div>

        {saveFailed && (
          <p role="alert" className="text-sm text-muted-foreground">
            {tErrors("generic")}
          </p>
        )}

        <AlertDialogFooter>
          <AlertDialogCancel disabled={submitting}>{t("cancel")}</AlertDialogCancel>
          <AlertDialogAction disabled={!canSubmit} onClick={() => void handleSubmit()}>
            {submitLabel}
          </AlertDialogAction>
        </AlertDialogFooter>

        <GlyphPickerDialog
          open={pickerOpen}
          onOpenChange={setPickerOpen}
          selectedMarkId={glyphId ?? undefined}
          onSave={(markId) => {
            setGlyphId(markId ?? null)
            setSaveFailed(false)
          }}
        />
      </AlertDialogContent>
    </AlertDialog>
  )
}
