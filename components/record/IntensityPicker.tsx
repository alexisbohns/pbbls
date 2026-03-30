"use client"

import { useCallback, useRef } from "react"
import { cn } from "@/lib/utils"

type IntensityPickerProps = {
  value: 1 | 2 | 3
  onChange: (intensity: 1 | 2 | 3) => void
}

const LEVELS: { value: 1 | 2 | 3; label: string; size: string }[] = [
  { value: 1, label: "Small", size: "size-5" },
  { value: 2, label: "Medium", size: "size-7" },
  { value: 3, label: "Large", size: "size-9" },
]

export function IntensityPicker({ value, onChange }: IntensityPickerProps) {
  const groupRef = useRef<HTMLDivElement>(null)

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      const idx = LEVELS.findIndex((l) => l.value === value)
      let next = idx

      if (e.key === "ArrowRight" || e.key === "ArrowDown") {
        e.preventDefault()
        next = (idx + 1) % LEVELS.length
      } else if (e.key === "ArrowLeft" || e.key === "ArrowUp") {
        e.preventDefault()
        next = (idx - 1 + LEVELS.length) % LEVELS.length
      } else {
        return
      }

      onChange(LEVELS[next].value)

      // Move focus to the newly selected button
      const buttons = groupRef.current?.querySelectorAll<HTMLButtonElement>("button[role='radio']")
      buttons?.[next]?.focus()
    },
    [value, onChange]
  )

  return (
    <fieldset>
      <legend className="text-sm font-medium">Intensity</legend>
      <div
        ref={groupRef}
        role="radiogroup"
        aria-label="Intensity"
        className="mt-2 flex items-end gap-3"
        onKeyDown={handleKeyDown}
      >
        {LEVELS.map((level) => {
          const selected = value === level.value
          return (
            <button
              key={level.value}
              type="button"
              role="radio"
              aria-checked={selected}
              aria-label={level.label}
              tabIndex={selected ? 0 : -1}
              onClick={() => onChange(level.value)}
              className={cn(
                "rounded-full transition-all duration-100 active:scale-90 outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
                level.size,
                selected
                  ? "bg-primary"
                  : "border border-border bg-muted hover:bg-muted/80"
              )}
            />
          )
        })}
      </div>
    </fieldset>
  )
}
