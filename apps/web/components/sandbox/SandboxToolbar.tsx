"use client"

import { GLYPH_VARIANTS, type GlyphVariant } from "./PolaroidGlyph"
import { SANDBOX_SCENARIOS } from "@/lib/seed/sandbox-pebbles"
import { cn } from "@/lib/utils"

function Group<T extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string
  options: { key: T; label: string }[]
  value: T
  onChange: (next: T) => void
}) {
  return (
    <div className="flex flex-col gap-1">
      <span className="text-[10px] uppercase tracking-[0.14em] text-muted-foreground">{label}</span>
      <div className="flex flex-wrap gap-1">
        {options.map((option) => (
          <button
            key={option.key}
            type="button"
            onClick={() => onChange(option.key)}
            aria-pressed={value === option.key}
            className={cn(
              "rounded-full px-2.5 py-1 text-xs transition-colors",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/50",
              value === option.key
                ? "bg-foreground text-background"
                : "bg-muted text-muted-foreground hover:bg-muted/70",
            )}
          >
            {option.label}
          </button>
        ))}
      </div>
    </div>
  )
}

export function SandboxToolbar({
  scenario,
  onScenario,
  glyphVariant,
  onGlyphVariant,
  dark,
  onDark,
}: {
  scenario: string
  onScenario: (key: string) => void
  glyphVariant: GlyphVariant
  onGlyphVariant: (v: GlyphVariant) => void
  dark: boolean
  onDark: (v: boolean) => void
}) {
  const activeScenario = SANDBOX_SCENARIOS.find((s) => s.key === scenario)
  const activeVariant = GLYPH_VARIANTS.find((v) => v.key === glyphVariant)

  return (
    <div className="sticky top-0 z-50 border-b border-border bg-background/90 px-4 py-3 backdrop-blur">
      <div className="flex flex-col gap-3">
        <div className="flex flex-wrap items-start gap-x-6 gap-y-3">
          <Group
            label="Scenario"
            options={SANDBOX_SCENARIOS.map((s) => ({ key: s.key, label: s.label }))}
            value={scenario}
            onChange={onScenario}
          />
          <Group
            label="Glyph"
            options={GLYPH_VARIANTS.map((v) => ({ key: v.key, label: v.label }))}
            value={glyphVariant}
            onChange={onGlyphVariant}
          />
          <Group
            label="Theme"
            options={[
              { key: "light" as const, label: "Light" },
              { key: "dark" as const, label: "Dark" },
            ]}
            value={dark ? "dark" : "light"}
            onChange={(v) => onDark(v === "dark")}
          />
        </div>
        <div className="flex flex-col gap-0.5 text-xs text-muted-foreground">
          {activeScenario && <p>{activeScenario.note}</p>}
          {activeVariant && <p className="opacity-70">Glyph — {activeVariant.note}</p>}
        </div>
      </div>
    </div>
  )
}
