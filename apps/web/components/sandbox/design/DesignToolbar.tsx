"use client"

import { usePathname } from "next/navigation"
import { COLOR_WORLDS } from "@/lib/config/color-worlds"
import { CANVAS_ZOOMS, type CanvasZoom, type DeviceChoice } from "@/lib/design/devices"
import { tuningProgress } from "@/lib/design/specimens"
import type { PbblsIconSize } from "@/lib/icons/pbbls-icon"
import type { ColorWorld } from "@/lib/types"
import type { DesignContrast, DesignMode, DesignTheme, DesignThemeName } from "@/lib/theme/design-theme"
import { DesignSearchField } from "./DesignSearchField"
import { DesignSelect, type SelectOption } from "./DesignSelect"
import { currentPage } from "./directory-pages"

const THEME_OPTIONS: SelectOption<DesignThemeName>[] = [
  { value: "current", label: "Current" },
  { value: "m3", label: "M3" },
]
const MODE_OPTIONS: SelectOption<DesignMode>[] = [
  { value: "light", label: "Light" },
  { value: "dark", label: "Dark" },
]
const CONTRAST_OPTIONS: SelectOption<DesignContrast>[] = [
  { value: "standard", label: "Standard" },
  { value: "medium", label: "Medium" },
  { value: "high", label: "High" },
]
const DEVICE_OPTIONS: SelectOption<DeviceChoice>[] = [
  { value: "both", label: "Both" },
  { value: "mobile", label: "Mobile" },
  { value: "desktop", label: "Desktop" },
]
/** The Icons page's drawing size: the five `PBBLS_ICON_SIZE` steps, in px at the default root. */
const SIZE_OPTIONS: SelectOption<PbblsIconSize>[] = [
  { value: "inline", label: "Inline · 16" },
  { value: "row", label: "Row · 20" },
  { value: "toolbar", label: "Toolbar · 22" },
  { value: "tile", label: "Tile · 28" },
  { value: "hero", label: "Hero · 48" },
]
const WORLD_OPTIONS: SelectOption<ColorWorld>[] = COLOR_WORLDS.map((w) => ({ value: w.id, label: w.label }))
const ZOOM_OPTIONS: SelectOption<CanvasZoom>[] = CANVAS_ZOOMS.map((z) => ({
  value: z,
  label: z === "fit" ? "Fit" : `${z * 100}%`,
}))

type DesignToolbarProps = {
  theme: DesignTheme
  onChange: (patch: Partial<DesignTheme>) => void
  zoom: CanvasZoom
  onZoom: (zoom: CanvasZoom) => void
  devices: DeviceChoice
  onDevices: (devices: DeviceChoice) => void
  iconQuery: string
  onIconQuery: (query: string) => void
  iconSize: PbblsIconSize
  onIconSize: (size: PbblsIconSize) => void
}

export function DesignToolbar({
  theme,
  onChange,
  zoom,
  onZoom,
  devices,
  onDevices,
  iconQuery,
  onIconQuery,
  iconSize,
  onIconSize,
}: DesignToolbarProps) {
  const pathname = usePathname()
  const { tuned, total } = tuningProgress()
  const current = currentPage(pathname)
  const icons = current?.path === "/icons"
  return (
    <header className="sticky top-0 z-40 border-b border-border bg-background/90 backdrop-blur">
      <div className="flex flex-col gap-2 px-4 py-3">
        <div className="flex flex-wrap items-center gap-x-5 gap-y-2">
          <h1 className="font-heading text-lg">
            Pebbles design system{current && <span className="text-muted-foreground"> · {current.label}</span>}
          </h1>
          <span className="text-xs text-muted-foreground">
            {tuned}/{total} tuned · <kbd>T</kbd> theme · <kbd>D</kbd> mode
          </span>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {icons && (
            <DesignSearchField
              label="Search icons"
              placeholder="Search a name, a meaning or a Lucide name"
              value={iconQuery}
              onChange={onIconQuery}
            />
          )}
          <DesignSelect label="Theme" options={THEME_OPTIONS} value={theme.theme} onChange={(v) => onChange({ theme: v })} />
          {theme.theme === "current" ? (
            <DesignSelect label="World" options={WORLD_OPTIONS} value={theme.world} onChange={(v) => onChange({ world: v })} />
          ) : (
            <DesignSelect label="Contrast" options={CONTRAST_OPTIONS} value={theme.contrast} onChange={(v) => onChange({ contrast: v })} />
          )}
          <DesignSelect label="Mode" options={MODE_OPTIONS} value={theme.mode} onChange={(v) => onChange({ mode: v })} />
          {icons && <DesignSelect label="Size" options={SIZE_OPTIONS} value={iconSize} onChange={onIconSize} />}
          {current?.canvas && (
            <>
              <DesignSelect label="Screens" options={DEVICE_OPTIONS} value={devices} onChange={onDevices} />
              <DesignSelect label="Zoom" options={ZOOM_OPTIONS} value={zoom} onChange={onZoom} />
            </>
          )}
        </div>
      </div>
    </header>
  )
}
