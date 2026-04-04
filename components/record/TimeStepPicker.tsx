"use client"

import { useCallback, useRef } from "react"
import { Minus, Plus } from "lucide-react"
import { Button } from "@/components/ui/button"

type TimeStepPickerProps = {
  value: Date
  onChange: (date: Date) => void
}

const STEP_MINUTES = 15

function formatTime(date: Date): string {
  const hours = String(date.getHours()).padStart(2, "0")
  const minutes = String(date.getMinutes()).padStart(2, "0")
  return `${hours}:${minutes}`
}

export function TimeStepPicker({ value, onChange }: TimeStepPickerProps) {
  const inputRef = useRef<HTMLInputElement>(null)

  const step = useCallback(
    (direction: 1 | -1) => {
      const next = new Date(value)
      next.setMinutes(next.getMinutes() + direction * STEP_MINUTES)
      // Keep same calendar date: if stepping crossed midnight, revert the date
      if (next.getDate() !== value.getDate()) {
        next.setFullYear(value.getFullYear(), value.getMonth(), value.getDate())
      }
      onChange(next)
    },
    [value, onChange]
  )

  const handleNativeChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const [hours, minutes] = e.target.value.split(":").map(Number)
      const next = new Date(value)
      next.setHours(hours, minutes)
      onChange(next)
    },
    [value, onChange]
  )

  return (
    <div role="group" aria-label="Time" className="flex items-center gap-2">
      <Button
        variant="outline"
        size="icon"
        aria-label="Subtract 15 minutes"
        onClick={() => step(-1)}
      >
        <Minus />
      </Button>

      <button
        type="button"
        className="relative flex h-11 items-center justify-center rounded-lg border border-input bg-transparent px-4 text-lg font-medium tabular-nums transition-colors hover:bg-muted focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 outline-none md:h-8 md:text-sm"
        onClick={() => inputRef.current?.showPicker()}
        aria-label="Select time"
      >
        <span aria-hidden="true">{formatTime(value)}</span>
        <input
          ref={inputRef}
          type="time"
          className="absolute inset-0 h-full w-full cursor-pointer opacity-0"
          value={formatTime(value)}
          onChange={handleNativeChange}
          tabIndex={-1}
          aria-hidden="true"
        />
      </button>

      <Button
        variant="outline"
        size="icon"
        aria-label="Add 15 minutes"
        onClick={() => step(1)}
      >
        <Plus />
      </Button>
    </div>
  )
}
