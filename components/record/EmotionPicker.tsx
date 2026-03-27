"use client"

import { useCallback, useRef } from "react"
import { EMOTIONS } from "@/lib/config"
import { cn } from "@/lib/utils"

type EmotionPickerProps = {
  value: string
  onChange: (emotionId: string) => void
}

const COLS = 4

export function EmotionPicker({ value, onChange }: EmotionPickerProps) {
  const groupRef = useRef<HTMLUListElement>(null)

  const focusIndex = useCallback((index: number) => {
    const buttons = groupRef.current?.querySelectorAll<HTMLButtonElement>("button[role='radio']")
    buttons?.[index]?.focus()
  }, [])

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      const currentIdx = value
        ? EMOTIONS.findIndex((em) => em.id === value)
        : 0

      let next = currentIdx

      switch (e.key) {
        case "ArrowRight":
          e.preventDefault()
          next = (currentIdx + 1) % EMOTIONS.length
          break
        case "ArrowLeft":
          e.preventDefault()
          next = (currentIdx - 1 + EMOTIONS.length) % EMOTIONS.length
          break
        case "ArrowDown":
          e.preventDefault()
          next = (currentIdx + COLS) % EMOTIONS.length
          break
        case "ArrowUp":
          e.preventDefault()
          next = (currentIdx - COLS + EMOTIONS.length) % EMOTIONS.length
          break
        case "Home":
          e.preventDefault()
          next = 0
          break
        case "End":
          e.preventDefault()
          next = EMOTIONS.length - 1
          break
        default:
          return
      }

      onChange(EMOTIONS[next].id)
      focusIndex(next)
    },
    [value, onChange, focusIndex]
  )

  // Determine which button gets tabIndex 0: the selected one, or the first if none selected
  const focusableIdx = value
    ? EMOTIONS.findIndex((em) => em.id === value)
    : 0

  return (
    <fieldset>
      <legend className="text-sm font-medium">Emotion</legend>
      <ul
        ref={groupRef}
        role="radiogroup"
        aria-label="Emotion"
        className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-4"
        onKeyDown={handleKeyDown}
      >
        {EMOTIONS.map((emotion, i) => {
          const selected = value === emotion.id
          return (
            <li key={emotion.id}>
              <button
                type="button"
                role="radio"
                aria-checked={selected}
                aria-label={emotion.name}
                tabIndex={i === focusableIdx ? 0 : -1}
                onClick={() => onChange(emotion.id)}
                className={cn(
                  "flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
                  selected
                    ? "ring-2 ring-current"
                    : "bg-muted/50 hover:bg-muted"
                )}
                style={
                  selected
                    ? {
                        backgroundColor: `${emotion.color}20`,
                        color: emotion.color,
                      }
                    : undefined
                }
              >
                <span
                  className="inline-block size-3 shrink-0 rounded-full"
                  style={{ backgroundColor: emotion.color }}
                  aria-hidden="true"
                />
                {emotion.name}
              </button>
            </li>
          )
        })}
      </ul>
    </fieldset>
  )
}
