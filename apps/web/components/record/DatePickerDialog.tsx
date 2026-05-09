"use client"

import { useState, useCallback } from "react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogClose,
} from "@/components/ui/dialog"
import { InlineDatePicker } from "@/components/record/InlineDatePicker"
import { TimeStepPicker } from "@/components/record/TimeStepPicker"

type DatePickerDialogProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  initialDate: Date
  onSave: (date: Date) => void
}

export function DatePickerDialog({ open, onOpenChange, initialDate, onSave }: DatePickerDialogProps) {
  const [tempDate, setTempDate] = useState(initialDate)
  const t = useTranslations("record.date")

  const handleOpenChange = useCallback(
    (nextOpen: boolean) => {
      onOpenChange(nextOpen)
      if (nextOpen) setTempDate(initialDate)
    },
    [initialDate, onOpenChange],
  )

  const handleDateChange = useCallback((date: Date) => {
    setTempDate(date)
  }, [])

  const handleTimeChange = useCallback((date: Date) => {
    setTempDate((prev) => {
      const next = new Date(prev)
      next.setHours(date.getHours(), date.getMinutes(), 0, 0)
      return next
    })
  }, [])

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <InlineDatePicker value={tempDate} onChange={handleDateChange} />
          <div className="flex items-center justify-center gap-3">
            <TimeStepPicker value={tempDate} onChange={handleTimeChange} />
            <Button
              variant="outline"
              onClick={() => setTempDate(new Date())}
            >
              {t("now")}
            </Button>
          </div>
        </div>
        <DialogFooter>
          <DialogClose>{t("cancel")}</DialogClose>
          <Button
            onClick={() => {
              onSave(tempDate)
              onOpenChange(false)
            }}
          >
            {t("done")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
