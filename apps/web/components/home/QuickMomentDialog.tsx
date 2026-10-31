"use client"

import { useId, useState } from "react"
import { useRouter } from "next/navigation"
import { useTranslations } from "next-intl"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { InlineDatePicker } from "@/components/record/InlineDatePicker"
import { TimeStepPicker } from "@/components/record/TimeStepPicker"
import { captureSavePayload } from "@/lib/data/moment-payloads"
import { momentErrorKey } from "@/lib/data/moment-write-error"
import { useMoments } from "@/lib/data/useMoments"

type QuickMomentDialogProps = { open: boolean; onOpenChange: (open: boolean) => void }

/**
 * Quick moment (#1384, spec D11; iOS D13): a name first, then when, prefilled
 * to now. "Save & close" keeps it bare; "Save & complete" goes on to make it a
 * pebble. Bottom-aligned on a phone.
 */
export function QuickMomentDialog({ open, onOpenChange }: QuickMomentDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[calc(100dvh-1rem)] overflow-y-auto max-sm:top-auto max-sm:bottom-0 max-sm:max-w-none max-sm:translate-y-0 max-sm:rounded-b-none max-sm:pb-[calc(1rem+var(--safe-area-bottom))]">
        {open && <QuickMomentForm onDone={() => onOpenChange(false)} />}
      </DialogContent>
    </Dialog>
  )
}

function QuickMomentForm({ onDone }: { onDone: () => void }) {
  const t = useTranslations("home.quick")
  const tErrors = useTranslations("moment.errors")
  const router = useRouter()
  const { saveMoment } = useMoments()
  const nameId = useId()
  const [name, setName] = useState("")
  const [happenedAt, setHappenedAt] = useState(() => new Date().toISOString())
  const [saving, setSaving] = useState<"save" | "complete" | null>(null)
  const [error, setError] = useState<string | null>(null)
  const value = new Date(happenedAt)
  const canSave = name.trim() !== "" && saving === null

  const save = async (then: "save" | "complete") => {
    if (!canSave) return
    setSaving(then)
    setError(null)
    let id: string
    try {
      id = await saveMoment(captureSavePayload({ name, happenedAt, snap: undefined }))
    } catch (err) {
      console.error("[home] quick moment save failed", err)
      setError(tErrors(momentErrorKey(err)))
      setSaving(null)
      return
    }
    onDone()
    if (then === "complete") router.push(`/record/complete/${id.toLowerCase()}?from=capture`)
    else toast.success(t("saved"))
  }

  return (
    <form
      className="flex flex-col gap-4"
      onSubmit={(e) => {
        e.preventDefault()
        void save("save")
      }}
    >
      <DialogHeader>
        <DialogTitle className="pb-type-card-title">{t("title")}</DialogTitle>
      </DialogHeader>
      <div className="flex flex-col gap-1.5">
        <label htmlFor={nameId} className="pb-type-row-title">
          {t("name")}
        </label>
        <Input id={nameId} autoFocus value={name} onChange={(e) => setName(e.target.value)} className="h-10" />
      </div>
      <fieldset className="flex flex-col items-center gap-3">
        <legend className="pb-type-row-title mb-1.5 self-start">{t("when")}</legend>
        <InlineDatePicker
          value={value}
          onChange={(date) => {
            const next = new Date(value)
            next.setFullYear(date.getFullYear(), date.getMonth(), date.getDate())
            setHappenedAt(next.toISOString())
          }}
        />
        <TimeStepPicker
          value={value}
          onChange={(date) => {
            const next = new Date(value)
            next.setHours(date.getHours(), date.getMinutes(), 0, 0)
            setHappenedAt(next.toISOString())
          }}
        />
      </fieldset>
      {error && (
        <p role="alert" className="pb-type-row-line text-destructive">
          {error}
        </p>
      )}
      <DialogFooter>
        <Button type="button" variant="outline" disabled={!canSave} onClick={() => void save("complete")}>
          {t("saveAndComplete")}
        </Button>
        <Button type="submit" disabled={!canSave}>
          {t("save")}
        </Button>
      </DialogFooter>
    </form>
  )
}
