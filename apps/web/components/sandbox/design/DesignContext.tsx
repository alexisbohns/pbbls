"use client"

import { createContext, useContext } from "react"
import type { CanvasZoom, DeviceChoice } from "@/lib/design/devices"
import type { DesignTheme } from "@/lib/theme/design-theme"

export interface DesignContextValue {
  theme: DesignTheme
  /** The serialized theme, the key every frame and swatch re-reads on. */
  themeKey: string
  zoom: CanvasZoom
  devices: DeviceChoice
  /** The directory's content width in page pixels, for "fit". */
  canvasWidth: number
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
