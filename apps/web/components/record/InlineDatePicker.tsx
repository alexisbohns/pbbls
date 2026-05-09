"use client"

import { useTranslations } from "next-intl"
import { Calendar } from "@/components/ui/calendar"

type InlineDatePickerProps = {
  value: Date
  onChange: (date: Date) => void
}

export function InlineDatePicker({ value, onChange }: InlineDatePickerProps) {
  const t = useTranslations("record.date")
  return (
    <div className="flex justify-center">
      <Calendar
        mode="single"
        selected={value}
        onSelect={(date) => {
          if (date) onChange(date)
        }}
        defaultMonth={value}
        aria-label={t("selectDate")}
      />
    </div>
  )
}
