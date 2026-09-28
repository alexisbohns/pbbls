"use client"

import { Button } from "@/components/ui/button"
import { COLOR_WORLDS } from "@/lib/config/color-worlds"
import { tuningProgress } from "@/lib/design/specimens"
import type { DesignContrast, DesignMode, DesignTheme, DesignThemeName } from "@/lib/theme/design-theme"

type Option<T extends string> = { value: T; label: string }

const THEME_OPTIONS: Option<DesignThemeName>[] = [
  { value: "current", label: "Current" },
  { value: "m3", label: "M3" },
]
const MODE_OPTIONS: Option<DesignMode>[] = [
  { value: "light", label: "Light" },
  { value: "dark", label: "Dark" },
]
const CONTRAST_OPTIONS: Option<DesignContrast>[] = [
  { value: "standard", label: "Standard" },
  { value: "medium", label: "Medium" },
  { value: "high", label: "High" },
]
const SECTIONS = [
  { id: "foundations", label: "Foundations" },
  { id: "primitives", label: "Primitives" },
  { id: "components", label: "Components" },
  { id: "views", label: "Views" },
]

function Segmented<T extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string
  options: Option<T>[]
  value: T
  onChange: (next: T) => void
}) {
  return (
    <div role="group" aria-label={label} className="flex items-center gap-1">
      <span className="mr-1 text-xs text-muted-foreground">{label}</span>
      {options.map((option) => (
        <Button
          key={option.value}
          size="xs"
          variant={option.value === value ? "default" : "outline"}
          aria-pressed={option.value === value}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </Button>
      ))}
    </div>
  )
}

type DesignToolbarProps = {
  theme: DesignTheme
  onChange: (patch: Partial<DesignTheme>) => void
}

export function DesignToolbar({ theme, onChange }: DesignToolbarProps) {
  const { tuned, total } = tuningProgress()
  return (
    <header className="sticky top-0 z-40 border-b border-border bg-background/90 backdrop-blur">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-5 gap-y-2 px-4 py-3">
        <h1 className="font-heading text-lg">Design</h1>
        <Segmented label="Theme" options={THEME_OPTIONS} value={theme.theme} onChange={(v) => onChange({ theme: v })} />
        {theme.theme === "current" ? (
          <label className="flex items-center gap-2 text-xs text-muted-foreground">
            World
            <select
              className="h-6 rounded-md border border-border bg-card px-1 text-xs text-foreground"
              value={theme.world}
              onChange={(e) => {
                const world = COLOR_WORLDS.find((w) => w.id === e.target.value)
                if (world) onChange({ world: world.id })
              }}
            >
              {COLOR_WORLDS.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.label}
                </option>
              ))}
            </select>
          </label>
        ) : (
          <Segmented
            label="Contrast"
            options={CONTRAST_OPTIONS}
            value={theme.contrast}
            onChange={(v) => onChange({ contrast: v })}
          />
        )}
        <Segmented label="Mode" options={MODE_OPTIONS} value={theme.mode} onChange={(v) => onChange({ mode: v })} />
        <span className="text-xs text-muted-foreground">
          {tuned}/{total} tuned · <kbd>T</kbd> theme · <kbd>D</kbd> mode
        </span>
        <nav aria-label="Sections" className="flex gap-3 text-xs">
          {SECTIONS.map((s) => (
            <a key={s.id} href={`#${s.id}`} className="underline-offset-4 hover:underline">
              {s.label}
            </a>
          ))}
        </nav>
      </div>
    </header>
  )
}
