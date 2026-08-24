"use client"

import { Sparkles } from "lucide-react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import { InlineDatePicker } from "@/components/record/InlineDatePicker"
import { TimeStepPicker } from "@/components/record/TimeStepPicker"

type RecordWhenStepProps = {
  happenedAt: string
  /** True when the value on screen came from the photo rather than from now. */
  seededFromPhoto: boolean
  onChange: (iso: string) => void
}

/**
 * Step 1 — the moment. Seeded from the photo's EXIF when it had one, so the
 * common case (recording from a picture taken earlier today) needs no input at
 * all beyond confirming.
 */
export function RecordWhenStep({ happenedAt, seededFromPhoto, onChange }: RecordWhenStepProps) {
  const t = useTranslations("record.flow.when")
  const tDate = useTranslations("record.date")
  const value = new Date(happenedAt)

  const handleDate = (date: Date) => {
    const next = new Date(value)
    next.setFullYear(date.getFullYear(), date.getMonth(), date.getDate())
    onChange(next.toISOString())
  }

  const handleTime = (date: Date) => {
    const next = new Date(value)
    next.setHours(date.getHours(), date.getMinutes(), 0, 0)
    onChange(next.toISOString())
  }

  return (
    <div className="flex flex-col items-center gap-4">
      <InlineDatePicker value={value} onChange={handleDate} />
      <div className="flex items-center gap-3">
        <TimeStepPicker value={value} onChange={handleTime} />
        <Button variant="outline" onClick={() => onChange(new Date().toISOString())}>
          {tDate("now")}
        </Button>
      </div>
      {seededFromPhoto && (
        <p className="flex items-center gap-1.5 text-sm text-muted-foreground">
          <Sparkles className="size-4" aria-hidden />
          {t("fromPhoto")}
        </p>
      )}
    </div>
  )
}
