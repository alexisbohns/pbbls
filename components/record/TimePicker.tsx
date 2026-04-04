"use client"

import { useCallback, useMemo } from "react"
import { Button } from "@/components/ui/button"
import { InlineDatePicker } from "@/components/record/InlineDatePicker"
import { TimeStepPicker } from "@/components/record/TimeStepPicker"

type TimePickerProps = {
  value: string
  onChange: (iso: string) => void
}

function roundToQuarterHour(date: Date): Date {
  const d = new Date(date)
  d.setMinutes(Math.round(d.getMinutes() / 15) * 15, 0, 0)
  return d
}

export function TimePicker({ value, onChange }: TimePickerProps) {
  const dateObj = useMemo(() => new Date(value), [value])

  const handleDateChange = useCallback(
    (date: Date) => {
      const next = new Date(dateObj)
      next.setFullYear(date.getFullYear(), date.getMonth(), date.getDate())
      onChange(next.toISOString())
    },
    [dateObj, onChange]
  )

  const handleTimeChange = useCallback(
    (date: Date) => {
      const next = new Date(dateObj)
      next.setHours(date.getHours(), date.getMinutes(), 0, 0)
      onChange(next.toISOString())
    },
    [dateObj, onChange]
  )

  const handleNow = useCallback(() => {
    onChange(roundToQuarterHour(new Date()).toISOString())
  }, [onChange])

  return (
    <fieldset>
      <legend className="text-sm font-medium">When</legend>
      <div className="mt-2 space-y-4">
        <InlineDatePicker value={dateObj} onChange={handleDateChange} />

        <div className="flex items-center justify-center gap-3">
          <TimeStepPicker value={dateObj} onChange={handleTimeChange} />
          <Button variant="outline" className="h-11 md:h-8" onClick={handleNow}>
            Now
          </Button>
        </div>
      </div>
    </fieldset>
  )
}
