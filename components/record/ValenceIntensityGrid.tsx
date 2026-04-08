"use client"

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

const INTENSITY_LABELS: Record<Intensity, string> = {
  1: "S",
  2: "M",
  3: "L",
}

const VALENCE_ROWS: { value: Valence; label: string }[] = [
  { value: 1, label: "HIGHLIGHT" },
  { value: 0, label: "NEUTRAL" },
  { value: -1, label: "LOWLIGHT" },
]

const INTENSITY_COLS: Intensity[] = [1, 2, 3]

export function ValenceIntensityGrid({
  intensity,
  valence,
  onIntensityChange,
  onValenceChange,
}: ValenceIntensityGridProps) {
  return (
    <Popover>
      <PopoverTrigger
        className="inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-xs font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
        aria-label={`Intensity: ${INTENSITY_LABELS[intensity]}, Valence: ${VALENCE_ROWS.find((r) => r.value === valence)?.label}`}
      >
        <span className="font-semibold">{INTENSITY_LABELS[intensity]}</span>
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
          aria-label="Intensity and valence"
          className="grid gap-1"
          style={{ gridTemplateColumns: "auto repeat(3, 1fr)" }}
        >
          {/* Column headers */}
          <div />
          {INTENSITY_COLS.map((col) => (
            <div
              key={col}
              className="flex items-center justify-center px-2 py-1 text-xs font-medium text-muted-foreground"
              role="columnheader"
            >
              {INTENSITY_LABELS[col]}
            </div>
          ))}

          {/* Rows */}
          {VALENCE_ROWS.map((row) => (
            <div key={row.value} role="row" className="contents">
              <div
                className="flex items-center pr-3 text-[0.65rem] font-semibold uppercase tracking-wider text-muted-foreground"
                role="rowheader"
              >
                {row.label}
              </div>
              {INTENSITY_COLS.map((col) => {
                const selected = intensity === col && valence === row.value
                return (
                  <button
                    key={col}
                    type="button"
                    role="gridcell"
                    aria-selected={selected}
                    onClick={() => {
                      onIntensityChange(col)
                      onValenceChange(row.value)
                    }}
                    className={cn(
                      "flex size-9 items-center justify-center rounded-full text-sm font-medium transition-all duration-100 outline-none focus-visible:ring-2 focus-visible:ring-ring",
                      selected
                        ? "bg-primary text-primary-foreground"
                        : "text-muted-foreground hover:bg-muted",
                    )}
                  >
                    {INTENSITY_LABELS[col]}
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
