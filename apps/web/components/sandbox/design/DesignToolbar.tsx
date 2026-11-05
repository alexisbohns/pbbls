"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { Button } from "@/components/ui/button"
import { COLOR_WORLDS } from "@/lib/config/color-worlds"
import { CANVAS_ZOOMS, type CanvasZoom, type DeviceChoice } from "@/lib/design/devices"
import { tuningProgress } from "@/lib/design/specimens"
import type { DesignContrast, DesignMode, DesignTheme, DesignThemeName } from "@/lib/theme/design-theme"
import { cn } from "@/lib/utils"
import { directoryHref } from "./DesignContext"

type Option<T extends string | number> = { value: T; label: string }

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
const DEVICE_OPTIONS: Option<DeviceChoice>[] = [
  { value: "both", label: "Both" },
  { value: "mobile", label: "Mobile" },
  { value: "desktop", label: "Desktop" },
]
const ZOOM_OPTIONS: Option<CanvasZoom>[] = CANVAS_ZOOMS.map((z) => ({
  value: z,
  label: z === "fit" ? "Fit" : `${z * 100}%`,
}))

/** The directory's pages; the canvas controls show on the ones drawn as screens. */
export const DIRECTORY_PAGES = [
  { path: "", label: "Overview", canvas: false },
  { path: "/colors", label: "Colors & type", canvas: false },
  { path: "/icons", label: "Icons", canvas: false },
  { path: "/components", label: "Components", canvas: true },
  { path: "/views", label: "Views", canvas: true },
] as const

function Segmented<T extends string | number>({
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
  themeKey: string
  onChange: (patch: Partial<DesignTheme>) => void
  zoom: CanvasZoom
  onZoom: (zoom: CanvasZoom) => void
  devices: DeviceChoice
  onDevices: (devices: DeviceChoice) => void
}

export function DesignToolbar({ theme, themeKey, onChange, zoom, onZoom, devices, onDevices }: DesignToolbarProps) {
  const pathname = usePathname()
  const { tuned, total } = tuningProgress()
  const current = DIRECTORY_PAGES.find((p) => pathname === `/sandbox/design${p.path}`)
  return (
    <header className="sticky top-0 z-40 border-b border-border bg-background/90 backdrop-blur">
      <div className="mx-auto flex max-w-[1600px] flex-col gap-2 px-4 py-3">
        <div className="flex flex-wrap items-center gap-x-5 gap-y-2">
          <h1 className="font-heading text-lg">Pebbles design system</h1>
          <nav aria-label="Design system" className="flex flex-wrap gap-1">
            {DIRECTORY_PAGES.map((page) => (
              <Link
                key={page.path}
                href={directoryHref(page.path, themeKey)}
                aria-current={page === current ? "page" : undefined}
                className={cn(
                  "rounded-full px-3 py-1 text-sm transition-colors hover:bg-muted",
                  page === current && "bg-secondary text-secondary-foreground",
                )}
              >
                {page.label}
              </Link>
            ))}
          </nav>
          <span className="text-xs text-muted-foreground">
            {tuned}/{total} tuned · <kbd>T</kbd> theme · <kbd>D</kbd> mode
          </span>
        </div>
        <div className="flex flex-wrap items-center gap-x-5 gap-y-2">
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
          {current?.canvas && (
            <>
              <Segmented label="Screens" options={DEVICE_OPTIONS} value={devices} onChange={onDevices} />
              <Segmented label="Zoom" options={ZOOM_OPTIONS} value={zoom} onChange={onZoom} />
            </>
          )}
        </div>
      </div>
    </header>
  )
}
