"use client"

import { useMemo } from "react"
import { COLOR_WORLDS } from "@/lib/config/color-worlds"
import { CANVAS_ZOOMS, type CanvasZoom, type DeviceChoice } from "@/lib/design/devices"
import { CATEGORIES, GLYPHS, glyphInCategory, glyphMatchesQuery, isDefaultView, showsGlyph, viewMatches, type CategoryFilter, type FillFilter, type GlyphShow, type GlyphView, type MarkFilter } from "@/lib/design/glyph-forms"
import { useReviewVerdicts } from "@/lib/design/useReviewVerdicts"
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

const FILL_OPTIONS: SelectOption<FillFilter>[] = [
  { value: "all", label: "All" },
  { value: "regular", label: "Regular only" },
  { value: "dotted", label: "Dotted" },
  { value: "solid", label: "Solid" },
  { value: "parts", label: "Parts" },
]
const MARK_OPTIONS: SelectOption<MarkFilter>[] = [
  { value: "any", label: "Any" },
  { value: "marked", label: "Marked" },
  { value: "unmarked", label: "Unmarked" },
]
const PROGRESS_OPTIONS: SelectOption<"off" | "on">[] = [
  { value: "off", label: "Off" },
  { value: "on", label: "Steps" },
]
const STEPS_OPTIONS: SelectOption<number>[] = [0, 1, 2, 3, 4, 5, 6, 7].map((k) => ({ value: k, label: String(k) }))
const SHOW_OPTIONS: SelectOption<GlyphShow>[] = [
  { value: "all", label: "All glyphs" },
  { value: "review", label: "Needs review" },
  { value: "round2", label: "Round 2" },
  { value: "fix", label: "Marked fix" },
]

const CATEGORY_OPTIONS: SelectOption<CategoryFilter>[] = [{ value: "all", label: "All" }, ...CATEGORIES.map((c) => ({ value: c.id, label: c.label }))]

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
  glyphView: GlyphView
  onGlyphView: (view: GlyphView) => void
  glyphShow: GlyphShow
  onGlyphShow: (show: GlyphShow) => void
  glyphCategory: CategoryFilter
  onGlyphCategory: (category: CategoryFilter) => void
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
  const { verdicts } = useReviewVerdicts()
  const filtering = icons && (s.iconQuery.trim() !== "" || s.glyphShow !== "all" || s.glyphCategory !== "all" || !isDefaultView(s.glyphView))
  const matching = useMemo(() => {
    if (!filtering) return null
    const list = GLYPHS.filter((g) => glyphMatchesQuery(g, s.iconQuery) && showsGlyph(g.name, s.glyphShow, verdicts) && glyphInCategory(g, s.glyphCategory) && viewMatches(g, s.glyphView))
    return { count: list.length, fixes: list.filter((g) => verdicts[g.name] === "fix").length }
  }, [filtering, s.iconQuery, s.glyphShow, s.glyphCategory, s.glyphView, verdicts])

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
      {icons && <DesignSelect label="Fill" options={FILL_OPTIONS} value={s.glyphView.fill} onChange={(fill) => s.onGlyphView({ ...s.glyphView, fill })} />}
      {icons && <DesignSelect label="Mark" options={MARK_OPTIONS} value={s.glyphView.mark} onChange={(mark) => s.onGlyphView({ ...s.glyphView, mark })} />}
      {icons && (
        <DesignSelect
          label="Progress"
          options={PROGRESS_OPTIONS}
          value={s.glyphView.progress ? "on" : "off"}
          onChange={(v) => s.onGlyphView({ ...s.glyphView, progress: v === "on" })}
        />
      )}
      {icons && s.glyphView.progress && (
        <DesignSelect label="Steps lit" options={STEPS_OPTIONS} value={s.glyphView.k} onChange={(k) => s.onGlyphView({ ...s.glyphView, k })} />
      )}
      {icons && <DesignSelect label="Show" options={SHOW_OPTIONS} value={s.glyphShow} onChange={s.onGlyphShow} />}
      {icons && <DesignSelect label="Category" options={CATEGORY_OPTIONS} value={s.glyphCategory} onChange={s.onGlyphCategory} />}
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
            : `${count.format(matching.count)} of ${count.format(GLYPHS.length)} glyphs${matching.fixes > 0 ? ` · ${count.format(matching.fixes)} marked fix` : ""}`}
        </p>
      )}
    </div>
  )
}
