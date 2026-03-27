"use client"

import { useCallback, useMemo } from "react"
import { Button } from "@/components/ui/button"

type TimePickerProps = {
  value: string
  onChange: (iso: string) => void
}

function toLocalDate(iso: string): string {
  const d = new Date(iso)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, "0")
  const day = String(d.getDate()).padStart(2, "0")
  return `${year}-${month}-${day}`
}

function toLocalTime(iso: string): string {
  const d = new Date(iso)
  const hours = String(d.getHours()).padStart(2, "0")
  const minutes = String(d.getMinutes()).padStart(2, "0")
  return `${hours}:${minutes}`
}

export function TimePicker({ value, onChange }: TimePickerProps) {
  const dateValue = useMemo(() => toLocalDate(value), [value])
  const timeValue = useMemo(() => toLocalTime(value), [value])

  const handleDateChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const [year, month, day] = e.target.value.split("-").map(Number)
      const d = new Date(value)
      d.setFullYear(year, month - 1, day)
      onChange(d.toISOString())
    },
    [value, onChange]
  )

  const handleTimeChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const [hours, minutes] = e.target.value.split(":").map(Number)
      const d = new Date(value)
      d.setHours(hours, minutes)
      onChange(d.toISOString())
    },
    [value, onChange]
  )

  const handleNow = useCallback(() => {
    onChange(new Date().toISOString())
  }, [onChange])

  return (
    <fieldset>
      <legend className="text-sm font-medium">When</legend>
      <div className="mt-2 flex flex-wrap items-end gap-3">
        <div className="flex flex-col gap-1">
          <label htmlFor="record-date" className="sr-only">
            Date
          </label>
          <input
            id="record-date"
            type="date"
            value={dateValue}
            onChange={handleDateChange}
            className="h-8 rounded-lg border border-input bg-background px-3 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
          />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="record-time" className="sr-only">
            Time
          </label>
          <input
            id="record-time"
            type="time"
            value={timeValue}
            onChange={handleTimeChange}
            className="h-8 rounded-lg border border-input bg-background px-3 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
          />
        </div>
        <Button variant="outline" size="sm" onClick={handleNow}>
          Now
        </Button>
      </div>
    </fieldset>
  )
}
