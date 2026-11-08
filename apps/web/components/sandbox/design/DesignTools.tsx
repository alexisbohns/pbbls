"use client"

import { useMemo } from "react"
import { COLOR_WORLDS } from "@/lib/config/color-worlds"
import { CANVAS_ZOOMS, type CanvasZoom, type DeviceChoice } from "@/lib/design/devices"
import { GLYPHS, glyphMatchesQuery, needsReview, type FormChoice } from "@/lib/design/glyph-forms"
import type { PbblsIconSize } from "@/lib/icons/pbbls-icon"
import type { ColorWorld } from "@/lib/types"
import type { DesignContrast, DesignMode, DesignTheme, DesignThemeName } from "@/lib/theme/design-theme"
import { cn } from "@/lib/utils"
import { DesignSearchField } from "./DesignSearchField"
import { DesignSelect, type SelectOption } from "./DesignSelect"
import type { DirectoryPage } from "./directory-pages"

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

const FORM_OPTIONS: SelectOption<FormChoice>[] = [
  { value: "regular", label: "Regular" },
  { value: "half", label: "Half" },
  { value: "solid", label: "Solid" },
  { value: "progress", label: "Progress" },
]
const PROGRESS_OPTIONS: SelectOption<number>[] = [0, 1, 2, 3, 4, 5].map((k) => ({ value: k, label: String(k) }))
const SHOW_OPTIONS: SelectOption<"all" | "review">[] = [
  { value: "all", label: "All glyphs" },
  { value: "review", label: "Needs review" },
]

const count = new Intl.NumberFormat("en")

/** Everything the toolbar and the mobile bar can set, and how. */
export type DesignToolsState = {
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
  glyphForm: FormChoice
  onGlyphForm: (form: FormChoice) => void
  glyphProgress: number
  onGlyphProgress: (k: number) => void
  reviewOnly: boolean
  onReviewOnly: (on: boolean) => void
}

type DesignToolsProps = DesignToolsState & {
  page: DirectoryPage | undefined
  /**
   * `row`: one wrapping row (the stacked header, 600–1023 px) that becomes a
   * two-column grid from 1024 px (the side area). `grid`: always two columns
   * (the mobile bar's fold).
   */
  layout: "row" | "grid"
}

/**
 * The page's tools: the theme selects every page has, then the page's own
 * (the icons' search and size, the canvases' screens and zoom).
 */
export function DesignTools({ page, layout, ...s }: DesignToolsProps) {
  const icons = page?.path === "/icons"
  const wide = layout === "row" ? "lg:col-span-2" : "col-span-2"
  const matching = useMemo(
    () => (icons && (s.iconQuery.trim() || s.reviewOnly) ? GLYPHS.filter((g) => glyphMatchesQuery(g, s.iconQuery) && (!s.reviewOnly || needsReview(g.name))).length : null),
    [icons, s.iconQuery, s.reviewOnly],
  )

  return (
    <div className={cn(layout === "row" ? "flex flex-wrap items-center gap-2 lg:grid lg:grid-cols-2" : "grid grid-cols-2 gap-2")}>
      {icons && (
        <DesignSearchField
          label="Search icons"
          placeholder="Search a name, a meaning or a tag"
          value={s.iconQuery}
          onChange={s.onIconQuery}
          className={cn(wide, layout === "row" && "rail:w-auto rail:max-w-xl rail:flex-1 rail:basis-80 lg:w-full lg:max-w-none")}
        />
      )}
      <DesignSelect label="Theme" options={THEME_OPTIONS} value={s.theme.theme} onChange={(v) => s.onChange({ theme: v })} />
      {s.theme.theme === "current" ? (
        <DesignSelect label="World" options={WORLD_OPTIONS} value={s.theme.world} onChange={(v) => s.onChange({ world: v })} />
      ) : (
        <DesignSelect label="Contrast" options={CONTRAST_OPTIONS} value={s.theme.contrast} onChange={(v) => s.onChange({ contrast: v })} />
      )}
      <DesignSelect label="Mode" options={MODE_OPTIONS} value={s.theme.mode} onChange={(v) => s.onChange({ mode: v })} />
      {icons && <DesignSelect label="Size" options={SIZE_OPTIONS} value={s.iconSize} onChange={s.onIconSize} />}
      {icons && <DesignSelect label="Form" options={FORM_OPTIONS} value={s.glyphForm} onChange={s.onGlyphForm} />}
      {icons && s.glyphForm === "progress" && (
        <DesignSelect label="Marks lit" options={PROGRESS_OPTIONS} value={s.glyphProgress} onChange={s.onGlyphProgress} />
      )}
      {icons && <DesignSelect label="Show" options={SHOW_OPTIONS} value={s.reviewOnly ? "review" : "all"} onChange={(v) => s.onReviewOnly(v === "review")} />}
      {page?.canvas && (
        <>
          <DesignSelect label="Screens" options={DEVICE_OPTIONS} value={s.devices} onChange={s.onDevices} />
          <DesignSelect label="Zoom" options={ZOOM_OPTIONS} value={s.zoom} onChange={s.onZoom} />
        </>
      )}
      {icons && (
        <p aria-live="polite" className={cn(wide, "px-1 text-xs text-muted-foreground tabular-nums")}>
          {matching === null
            ? `${count.format(GLYPHS.length)} glyphs`
            : `${count.format(matching)} of ${count.format(GLYPHS.length)} glyphs`}
        </p>
      )}
    </div>
  )
}
