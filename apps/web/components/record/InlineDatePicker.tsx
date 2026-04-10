"use client"

import { Calendar } from "@/components/ui/calendar"

type InlineDatePickerProps = {
  value: Date
  onChange: (date: Date) => void
}

export function InlineDatePicker({ value, onChange }: InlineDatePickerProps) {
  return (
    <div className="flex justify-center">
      <Calendar
        mode="single"
        selected={value}
        onSelect={(date) => {
          if (date) onChange(date)
        }}
        defaultMonth={value}
        aria-label="Select date"
      />
    </div>
  )
}
