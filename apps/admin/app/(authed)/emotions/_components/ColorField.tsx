"use client"

import { useId } from "react"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { alphaToPercent, isHex8, rgbPart, withRgb } from "@/lib/emotions/color"

// Classic CSS checkerboard so translucent swatches (e.g. the derived surface)
// read as transparent rather than as a solid near-white.
const CHECKER: React.CSSProperties = {
  backgroundImage:
    "linear-gradient(45deg, #00000014 25%, transparent 25%), linear-gradient(-45deg, #00000014 25%, transparent 25%), linear-gradient(45deg, transparent 75%, #00000014 75%), linear-gradient(-45deg, transparent 75%, #00000014 75%)",
  backgroundSize: "8px 8px",
  backgroundPosition: "0 0, 0 4px, 4px -4px, -4px 0",
}

export function ColorField({
  label,
  value,
  onChange,
}: {
  label: string
  value: string
  onChange: (next: string) => void
}) {
  const id = useId()
  const valid = isHex8(value)
  // The native picker only speaks #RRGGBB; keep the alpha byte we already have.
  const pickerRgb = valid ? rgbPart(value) : "#000000"

  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between gap-2">
        <Label htmlFor={id}>{label}</Label>
        {valid ? (
          <span className="text-xs tabular-nums text-muted-foreground">
            {alphaToPercent(value)}%
          </span>
        ) : null}
      </div>
      <div className="flex items-center gap-2">
        <label
          className="relative size-9 shrink-0 overflow-hidden rounded-md border"
          style={CHECKER}
          aria-label={`${label} colour picker`}
        >
          {valid ? (
            <span className="absolute inset-0" style={{ backgroundColor: value }} />
          ) : null}
          <input
            type="color"
            value={pickerRgb}
            onChange={(e) => onChange(withRgb(e.target.value, valid ? value : "#000000FF"))}
            className="absolute inset-0 size-full cursor-pointer opacity-0"
          />
        </label>
        <Input
          id={id}
          value={value}
          spellCheck={false}
          autoComplete="off"
          onChange={(e) => onChange(e.target.value)}
          aria-invalid={!valid}
          className="font-mono uppercase"
        />
      </div>
      {!valid ? (
        <p className="text-xs text-destructive">Use 8-digit hex (#RRGGBBAA).</p>
      ) : null}
    </div>
  )
}
