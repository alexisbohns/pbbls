"use client"

import { useCallback, useRef } from "react"
import { cn } from "@/lib/utils"
import { POSITIVENESS_SIGNS, POSITIVENESS_LABELS } from "@/lib/config/positiveness"

type PositivenessPickerProps = {
  value: -2 | -1 | 0 | 1 | 2
  onChange: (positiveness: -2 | -1 | 0 | 1 | 2) => void
}

const VALUES = [-2, -1, 0, 1, 2] as const

const COLORS: Record<number, string> = {
  [-2]: "bg-red-500/20 text-red-600 dark:text-red-400",
  [-1]: "bg-orange-500/20 text-orange-600 dark:text-orange-400",
  [0]: "bg-muted text-muted-foreground",
  [1]: "bg-emerald-500/20 text-emerald-600 dark:text-emerald-400",
  [2]: "bg-green-500/20 text-green-600 dark:text-green-400",
}

const SELECTED_COLORS: Record<number, string> = {
  [-2]: "bg-red-500/40 text-red-700 dark:text-red-300 ring-2 ring-red-500/50",
  [-1]: "bg-orange-500/40 text-orange-700 dark:text-orange-300 ring-2 ring-orange-500/50",
  [0]: "bg-muted ring-2 ring-border text-foreground",
  [1]: "bg-emerald-500/40 text-emerald-700 dark:text-emerald-300 ring-2 ring-emerald-500/50",
  [2]: "bg-green-500/40 text-green-700 dark:text-green-300 ring-2 ring-green-500/50",
}

export function PositivenessPicker({ value, onChange }: PositivenessPickerProps) {
  const groupRef = useRef<HTMLDivElement>(null)

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      const idx = VALUES.indexOf(value)
      let next = idx

      if (e.key === "ArrowRight" || e.key === "ArrowDown") {
        e.preventDefault()
        next = (idx + 1) % VALUES.length
      } else if (e.key === "ArrowLeft" || e.key === "ArrowUp") {
        e.preventDefault()
        next = (idx - 1 + VALUES.length) % VALUES.length
      } else {
        return
      }

      onChange(VALUES[next])

      const buttons = groupRef.current?.querySelectorAll<HTMLButtonElement>("button[role='radio']")
      buttons?.[next]?.focus()
    },
    [value, onChange]
  )

  return (
    <fieldset>
      <legend className="text-sm font-medium">Positiveness</legend>
      <div
        ref={groupRef}
        role="radiogroup"
        aria-label="Positiveness"
        className="mt-2 flex gap-1.5"
        onKeyDown={handleKeyDown}
      >
        {VALUES.map((v) => {
          const selected = value === v
          return (
            <button
              key={v}
              type="button"
              role="radio"
              aria-checked={selected}
              aria-label={POSITIVENESS_LABELS[v]}
              tabIndex={selected ? 0 : -1}
              onClick={() => onChange(v)}
              className={cn(
                "flex size-9 items-center justify-center rounded-lg text-sm font-medium transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
                selected ? SELECTED_COLORS[v] : COLORS[v]
              )}
            >
              {POSITIVENESS_SIGNS[v]}
            </button>
          )
        })}
      </div>
    </fieldset>
  )
}
