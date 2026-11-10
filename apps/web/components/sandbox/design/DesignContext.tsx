"use client"

import { createContext, useContext } from "react"
import type { CanvasZoom, DeviceChoice } from "@/lib/design/devices"
import type { CategoryFilter, GlyphShow, GlyphView } from "@/lib/design/glyph-forms"
import type { PbblsIconSize } from "@/lib/icons/pbbls-icon"
import type { DesignTheme } from "@/lib/theme/design-theme"

export interface DesignContextValue {
  theme: DesignTheme
  /** The serialized theme, the key every frame and swatch re-reads on. */
  themeKey: string
  zoom: CanvasZoom
  devices: DeviceChoice
  /** The directory's content width in page pixels, for "fit". */
  canvasWidth: number
  /** The Icons page's search and drawing size, set from the toolbar. */
  iconQuery: string
  iconSize: PbblsIconSize
  /** The Icons page's Fill, Mark and Progress pickers (#1607 review): what is listed and how it is drawn. */
  glyphView: GlyphView
  /** Which glyphs the Icons page lists: all, the flagged ones, or those marked fix. */
  glyphShow: GlyphShow
  /** The Icons page's Category picker: `all` or a category id from packages/icons/categories.json. */
  glyphCategory: CategoryFilter
}

const DesignContext = createContext<DesignContextValue | null>(null)

export const DesignContextProvider = DesignContext.Provider

/** The directory's theme and canvas settings, for its pages. */
export function useDesign(): DesignContextValue {
  const value = useContext(DesignContext)
  if (!value) throw new Error("[design] useDesign outside the design directory")
  return value
}

/** A link to another page of the directory that keeps the theme. */
export function directoryHref(path: string, themeKey: string): string {
  return `/sandbox/design${path}?${themeKey}`
}
