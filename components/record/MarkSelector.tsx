"use client"

import { useCallback, useRef } from "react"
import { cn } from "@/lib/utils"
import type { Mark } from "@/lib/types"
import { GlyphPreview } from "@/components/glyphs/GlyphPreview"

type MarkSelectorProps = {
  marks: Mark[]
  selectedMarkId?: string
  onSelect: (markId: string) => void
}

const COLS = 3

export function MarkSelector({ marks, selectedMarkId, onSelect }: MarkSelectorProps) {
  const groupRef = useRef<HTMLUListElement>(null)

  const focusIndex = useCallback((index: number) => {
    const buttons = groupRef.current?.querySelectorAll<HTMLButtonElement>("button")
    buttons?.[index]?.focus()
  }, [])

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      const buttons = groupRef.current?.querySelectorAll<HTMLButtonElement>("button")
      if (!buttons || buttons.length === 0) return
      const currentIdx = Array.from(buttons).indexOf(e.target as HTMLButtonElement)
      if (currentIdx === -1) return

      let next = currentIdx

      switch (e.key) {
        case "ArrowRight":
          e.preventDefault()
          next = (currentIdx + 1) % marks.length
          break
        case "ArrowLeft":
          e.preventDefault()
          next = (currentIdx - 1 + marks.length) % marks.length
          break
        case "ArrowDown":
          e.preventDefault()
          next = (currentIdx + COLS) % marks.length
          break
        case "ArrowUp":
          e.preventDefault()
          next = (currentIdx - COLS + marks.length) % marks.length
          break
        default:
          return
      }

      focusIndex(next)
    },
    [focusIndex, marks.length],
  )

  if (marks.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        No glyphs yet. Draw one above!
      </p>
    )
  }

  const selectedIndex = marks.findIndex((m) => m.id === selectedMarkId)

  return (
    <ul
      ref={groupRef}
      role="radiogroup"
      aria-label="Select an existing glyph"
      className="grid grid-cols-3 gap-3"
      onKeyDown={handleKeyDown}
    >
      {marks.map((mark, i) => {
        const isSelected = mark.id === selectedMarkId
        return (
          <li key={mark.id}>
            <button
              type="button"
              role="radio"
              aria-checked={isSelected}
              aria-label={mark.name || "Untitled glyph"}
              tabIndex={isSelected ? 0 : (selectedIndex === -1 && i === 0 ? 0 : -1)}
              onClick={() => onSelect(mark.id)}
              className={cn(
                "flex w-full items-center justify-center rounded-lg p-3",
                "transition-all duration-100 active:scale-[0.95]",
                "outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
                isSelected
                  ? "bg-primary/10 ring-2 ring-primary"
                  : "bg-muted/50 hover:bg-muted",
              )}
            >
              <GlyphPreview
                mark={mark}
                className="h-16 w-16"
              />
            </button>
          </li>
        )
      })}
    </ul>
  )
}
