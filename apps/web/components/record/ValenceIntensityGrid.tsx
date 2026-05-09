"use client"

import { useTranslations } from "next-intl"
import { cn } from "@/lib/utils"
import {
  Popover,
  PopoverTrigger,
  PopoverContent,
} from "@/components/ui/popover"

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

const VALENCE_ROWS: Valence[] = [1, 0, -1]
const INTENSITY_COLS: Intensity[] = [1, 2, 3]

export function ValenceIntensityGrid({
  intensity,
  valence,
  onIntensityChange,
  onValenceChange,
}: ValenceIntensityGridProps) {
  const tIntensity = useTranslations("record.intensity")
  const tValence = useTranslations("record.valence")

  return (
    <Popover>
      <PopoverTrigger
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
      </PopoverTrigger>
      <PopoverContent align="end" className="w-auto p-3">
        <div
          role="grid"
          aria-label={tIntensity("ariaTitle")}
          className="grid gap-1"
          style={{ gridTemplateColumns: "auto repeat(3, 1fr)" }}
        >
          <div />
          {INTENSITY_COLS.map((col) => (
            <div
              key={col}
              className="flex items-center justify-center px-2 py-1 text-xs font-medium text-muted-foreground"
              role="columnheader"
            >
              {tIntensity(INTENSITY_KEY[col])}
            </div>
          ))}

          {VALENCE_ROWS.map((row) => (
            <div key={row} role="row" className="contents">
              <div
                className="flex items-center pr-3 text-[0.65rem] font-semibold uppercase tracking-wider text-muted-foreground"
                role="rowheader"
              >
                {tValence(VALENCE_KEY[row])}
              </div>
              {INTENSITY_COLS.map((col) => {
                const selected = intensity === col && valence === row
                return (
                  <button
                    key={col}
                    type="button"
                    role="gridcell"
                    aria-selected={selected}
                    onClick={() => {
                      onIntensityChange(col)
                      onValenceChange(row)
                    }}
                    className={cn(
                      "flex size-9 items-center justify-center rounded-full text-sm font-medium transition-all duration-100 outline-none focus-visible:ring-2 focus-visible:ring-ring",
                      selected
                        ? "bg-primary text-primary-foreground"
                        : "text-muted-foreground hover:bg-muted",
                    )}
                  >
                    {tIntensity(INTENSITY_KEY[col])}
                  </button>
                )
              })}
            </div>
          ))}
        </div>
      </PopoverContent>
    </Popover>
  )
}
