"use client"

import { Info } from "lucide-react"
import { STONE_SIZES, type StoneSize } from "./stone-sizes"
import { SANDBOX_SCENARIOS } from "@/lib/seed/sandbox-pebbles"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { cn } from "@/lib/utils"

const FIELD =
  "h-8 min-w-0 flex-1 rounded-md border border-border bg-card px-2 text-xs text-foreground " +
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/50"

function Field<T extends string>({
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
    <label className="flex min-w-0 flex-1 flex-col gap-1">
      <span className="sr-only">{label}</span>
      <select
        aria-label={label}
        className={cn(FIELD)}
        value={value}
        onChange={(e) => onChange(e.target.value as T)}
      >
        {options.map((option) => (
          <option key={option.key} value={option.key}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  )
}

export function SandboxToolbar({
  scenario,
  onScenario,
  stoneSize,
  onStoneSize,
  dark,
  onDark,
}: {
  scenario: string
  onScenario: (key: string) => void
  stoneSize: StoneSize
  onStoneSize: (v: StoneSize) => void
  dark: boolean
  onDark: (v: boolean) => void
}) {
  const activeScenario = SANDBOX_SCENARIOS.find((s) => s.key === scenario)
  const activeStone = STONE_SIZES.find((v) => v.key === stoneSize)

  return (
    <div className="sticky top-0 z-50 flex items-center gap-2 border-b border-border bg-surface/90 px-3 py-2 backdrop-blur dark:bg-background/90">
      <Field
        label="Scenario"
        options={SANDBOX_SCENARIOS.map((s) => ({ key: s.key, label: s.label }))}
        value={scenario}
        onChange={onScenario}
      />
      <Field
        label="Stone size"
        options={STONE_SIZES.map((v) => ({ key: v.key, label: v.label }))}
        value={stoneSize}
        onChange={onStoneSize}
      />
      <Field
        label="Theme"
        options={[
          { key: "light" as const, label: "Light" },
          { key: "dark" as const, label: "Dark" },
        ]}
        value={dark ? "dark" : "light"}
        onChange={(v) => onDark(v === "dark")}
      />

      {/* The notes explaining the current scenario and stone used to sit under the
          toolbar, where they cost two lines of a phone screen permanently. */}
      <Popover>
        <PopoverTrigger
          aria-label="About this scenario"
          className="grid size-8 shrink-0 place-items-center rounded-md border border-border text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/50"
        >
          <Info className="size-4" aria-hidden="true" />
        </PopoverTrigger>
        <PopoverContent align="end" className="max-w-72 p-3 text-xs">
          <div className="flex flex-col gap-2">
            {activeScenario && (
              <p>
                <span className="font-semibold">{activeScenario.label}</span> — {activeScenario.note}
              </p>
            )}
            {activeStone && (
              <p className="text-muted-foreground">
                <span className="font-semibold">{activeStone.label}</span> — {activeStone.note}
              </p>
            )}
          </div>
        </PopoverContent>
      </Popover>
    </div>
  )
}
