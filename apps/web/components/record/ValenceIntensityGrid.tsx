"use client"

import { useEffect, useId, useRef, useState } from "react"
import Image from "next/image"
import { useTranslations } from "next-intl"
import { cn } from "@/lib/utils"
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet"

type Intensity = 1 | 2 | 3
type Valence = -1 | 0 | 1

type ValenceIntensityGridProps = {
  intensity: Intensity
  valence: Valence
  onIntensityChange: (v: Intensity) => void
  onValenceChange: (v: Valence) => void
}

const INTENSITY_KEY: Record<Intensity, "small" | "medium" | "large"> = {
  1: "small",
  2: "medium",
  3: "large",
}

const VALENCE_KEY: Record<Valence, "highlight" | "neutral" | "lowlight"> = {
  1: "highlight",
  0: "neutral",
  [-1]: "lowlight",
}

const SIZE_GROUPS: Intensity[] = [1, 2, 3]
const POLARITIES: Valence[] = [-1, 0, 1]

const ASSET_NAME: Record<Intensity, Record<Valence, string>> = {
  1: {
    [-1]: "valence-lowlightSmall.svg",
    0: "valence-neutralSmall.svg",
    1: "valence-highlightSmall.svg",
  },
  2: {
    [-1]: "valence-lowlightMedium.svg",
    0: "valence-neutralMedium.svg",
    1: "valence-highlightMedium.svg",
  },
  3: {
    [-1]: "valence-lowlightLarge.svg",
    0: "valence-neutralLarge.svg",
    1: "valence-highlightLarge.svg",
  },
}

export function ValenceIntensityGrid({
  intensity,
  valence,
  onIntensityChange,
  onValenceChange,
}: ValenceIntensityGridProps) {
  const tIntensity = useTranslations("record.intensity")
  const tValence = useTranslations("record.valence")
  const [open, setOpen] = useState(false)

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger
        className="inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
        aria-label={tIntensity("ariaSelected", {
          intensity: tIntensity(INTENSITY_KEY[intensity]),
          valence: tValence(VALENCE_KEY[valence]),
        })}
      >
        <span className="font-semibold">{tIntensity(INTENSITY_KEY[intensity])}</span>
        <span
          className={cn(
            "inline-block size-3.5 rounded-sm border",
            valence === 1 && "border-primary bg-primary/30",
            valence === 0 && "border-muted-foreground bg-muted-foreground/30",
            valence === -1 && "border-muted-foreground/50 bg-muted-foreground/10",
          )}
          aria-hidden
        />
      </SheetTrigger>

      {/* Remount the picker body each time the sheet opens so the initial
          focus target is always derived fresh from the current selection. */}
      {open && (
        <ValencePickerBody
          intensity={intensity}
          valence={valence}
          onSelect={(size, polarity) => {
            onIntensityChange(size)
            onValenceChange(polarity)
            setOpen(false)
          }}
        />
      )}
    </Sheet>
  )
}

type ValencePickerBodyProps = {
  intensity: Intensity
  valence: Valence
  onSelect: (size: Intensity, polarity: Valence) => void
}

function ValencePickerBody({
  intensity,
  valence,
  onSelect,
}: ValencePickerBodyProps) {
  const tPicker = useTranslations("record.valencePicker")
  const titleId = useId()

  // cellsRef[row][col] where row = polarity index, col = size index.
  const cellsRef = useRef<Array<Array<HTMLButtonElement | null>>>(
    POLARITIES.map(() => SIZE_GROUPS.map(() => null)),
  )

  const initialRow = Math.max(0, POLARITIES.indexOf(valence))
  const initialCol = Math.max(0, SIZE_GROUPS.indexOf(intensity))
  const [focus, setFocus] = useState({ row: initialRow, col: initialCol })

  // Focus the active cell on mount (sheet just opened) and whenever the
  // user moves focus via keyboard.
  useEffect(() => {
    cellsRef.current[focus.row]?.[focus.col]?.focus()
  }, [focus])

  const moveFocus = (rowDelta: number, colDelta: number) => {
    setFocus((prev) => ({
      row: (prev.row + rowDelta + POLARITIES.length) % POLARITIES.length,
      col: (prev.col + colDelta + SIZE_GROUPS.length) % SIZE_GROUPS.length,
    }))
  }

  return (
    <SheetContent
      aria-labelledby={titleId}
      className="motion-reduce:transition-none"
    >
      <SheetHeader>
        <SheetTitle id={titleId}>{tPicker("title")}</SheetTitle>
      </SheetHeader>

      <div role="group" aria-labelledby={titleId} className="flex flex-col gap-6">
        {SIZE_GROUPS.map((size, colIndex) => (
          <section key={size} className="flex flex-col gap-3">
            <header className="flex flex-col gap-1">
              <h3 className="font-heading text-sm font-semibold text-foreground">
                {tPicker(`${INTENSITY_KEY[size]}.name`)}
              </h3>
              <p className="text-xs text-muted-foreground">
                {tPicker(`${INTENSITY_KEY[size]}.description`)}
              </p>
            </header>

            <div className="grid grid-cols-3 gap-2">
              {POLARITIES.map((polarity, rowIndex) => {
                const selected = intensity === size && valence === polarity
                const isFocusTarget =
                  focus.row === rowIndex && focus.col === colIndex
                return (
                  <button
                    key={polarity}
                    ref={(el) => {
                      cellsRef.current[rowIndex]![colIndex] = el
                    }}
                    type="button"
                    role="radio"
                    aria-checked={selected}
                    tabIndex={isFocusTarget ? 0 : -1}
                    onClick={() => onSelect(size, polarity)}
                    onKeyDown={(e) => {
                      switch (e.key) {
                        case "ArrowRight":
                          e.preventDefault()
                          moveFocus(0, 1)
                          break
                        case "ArrowLeft":
                          e.preventDefault()
                          moveFocus(0, -1)
                          break
                        case "ArrowDown":
                          e.preventDefault()
                          moveFocus(1, 0)
                          break
                        case "ArrowUp":
                          e.preventDefault()
                          moveFocus(-1, 0)
                          break
                        case " ":
                        case "Enter":
                          e.preventDefault()
                          onSelect(size, polarity)
                          break
                      }
                    }}
                    aria-label={tPicker("optionAria", {
                      section: tPicker(`${INTENSITY_KEY[size]}.name`),
                      polarity: tPicker(VALENCE_KEY[polarity]),
                    })}
                    className={cn(
                      "flex flex-col items-center gap-2 rounded-xl border px-2 py-3 outline-none transition-colors",
                      "focus-visible:ring-2 focus-visible:ring-ring",
                      selected
                        ? "border-primary bg-primary/10 text-primary"
                        : "border-border bg-card text-muted-foreground hover:bg-muted hover:text-foreground",
                    )}
                  >
                    <Image
                      src={`/valence/${ASSET_NAME[size][polarity]}`}
                      alt=""
                      width={48}
                      height={48}
                      aria-hidden
                      className="size-12"
                    />
                    <span className="text-xs font-medium">
                      {tPicker(VALENCE_KEY[polarity])}
                    </span>
                  </button>
                )
              })}
            </div>
          </section>
        ))}
      </div>
    </SheetContent>
  )
}
