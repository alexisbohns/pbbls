"use client"

import { useCallback, useRef } from "react"
import { cn } from "@/lib/utils"
import type { MarkStroke } from "@/lib/types"

type Stamp = {
  id: string
  name: string
  strokes: MarkStroke[]
}

const STAMPS: Stamp[] = [
  {
    id: "star",
    name: "Star",
    strokes: [
      { d: "M100,20 L115,75 L170,75 L125,110 L140,165 L100,135 L60,165 L75,110 L30,75 L85,75 Z", width: 3 },
    ],
  },
  {
    id: "spiral",
    name: "Spiral",
    strokes: [
      { d: "M100,100 Q100,80 115,80 Q130,80 130,100 Q130,120 110,120 Q85,120 85,95 Q85,65 110,65 Q140,65 140,100 Q140,130 105,130", width: 3 },
    ],
  },
  {
    id: "wave",
    name: "Wave",
    strokes: [
      { d: "M30,100 Q55,70 80,100 Q105,130 130,100 Q155,70 180,100", width: 3 },
    ],
  },
  {
    id: "cross",
    name: "Cross",
    strokes: [
      { d: "M100,40 L100,160", width: 3 },
      { d: "M40,100 L160,100", width: 3 },
    ],
  },
  {
    id: "circle",
    name: "Circle",
    strokes: [
      { d: "M100,40 Q140,40 160,70 Q180,100 160,130 Q140,160 100,160 Q60,160 40,130 Q20,100 40,70 Q60,40 100,40 Z", width: 3 },
    ],
  },
  {
    id: "zigzag",
    name: "Zigzag",
    strokes: [
      { d: "M30,130 L60,70 L90,130 L120,70 L150,130 L180,70", width: 3 },
    ],
  },
  {
    id: "diamond",
    name: "Diamond",
    strokes: [
      { d: "M100,30 L160,100 L100,170 L40,100 Z", width: 3 },
    ],
  },
  {
    id: "heart",
    name: "Heart",
    strokes: [
      { d: "M100,160 Q40,120 40,80 Q40,40 70,40 Q100,40 100,70 Q100,40 130,40 Q160,40 160,80 Q160,120 100,160 Z", width: 3 },
    ],
  },
]

type StampPickerProps = {
  onSelect: (strokes: MarkStroke[]) => void
}

const COLS = 4

export function StampPicker({ onSelect }: StampPickerProps) {
  const groupRef = useRef<HTMLUListElement>(null)

  const focusIndex = useCallback((index: number) => {
    const buttons = groupRef.current?.querySelectorAll<HTMLButtonElement>("button")
    buttons?.[index]?.focus()
  }, [])

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      const buttons = groupRef.current?.querySelectorAll<HTMLButtonElement>("button")
      if (!buttons) return
      const currentIdx = Array.from(buttons).indexOf(e.target as HTMLButtonElement)
      if (currentIdx === -1) return

      let next = currentIdx

      switch (e.key) {
        case "ArrowRight":
          e.preventDefault()
          next = (currentIdx + 1) % STAMPS.length
          break
        case "ArrowLeft":
          e.preventDefault()
          next = (currentIdx - 1 + STAMPS.length) % STAMPS.length
          break
        case "ArrowDown":
          e.preventDefault()
          next = (currentIdx + COLS) % STAMPS.length
          break
        case "ArrowUp":
          e.preventDefault()
          next = (currentIdx - COLS + STAMPS.length) % STAMPS.length
          break
        default:
          return
      }

      focusIndex(next)
    },
    [focusIndex],
  )

  return (
    <fieldset>
      <legend className="text-sm font-medium">Stamps</legend>
      <ul
        ref={groupRef}
        role="group"
        aria-label="Pre-made stamp symbols"
        className="mt-2 grid grid-cols-4 gap-2"
        onKeyDown={handleKeyDown}
      >
        {STAMPS.map((stamp, i) => (
          <li key={stamp.id}>
            <button
              type="button"
              aria-label={stamp.name}
              tabIndex={i === 0 ? 0 : -1}
              onClick={() => onSelect(stamp.strokes)}
              className={cn(
                "flex w-full items-center justify-center rounded-lg p-2",
                "bg-muted/50 hover:bg-muted",
                "transition-all duration-100 active:scale-[0.95]",
                "outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
              )}
            >
              <svg
                viewBox="0 0 200 200"
                className="h-10 w-10"
                aria-hidden="true"
              >
                {stamp.strokes.map((s, si) => (
                  <path
                    key={si}
                    d={s.d}
                    fill="none"
                    stroke="currentColor"
                    strokeWidth={s.width}
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                ))}
              </svg>
            </button>
          </li>
        ))}
      </ul>
    </fieldset>
  )
}
