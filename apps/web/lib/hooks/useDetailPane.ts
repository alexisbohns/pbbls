"use client"

import { createContext, useContext } from "react"

/** The one detail title's id: one detail shows at a time, and the right area is labelled by it. */
export const DETAIL_TITLE_ID = "detail-title"

/**
 * What a detail's page reads from the pane it opens in (#1384, two-area spec
 * D5): whether it sits in the right area (wide) or is the overlay, and how to
 * close it.
 */
export type DetailPaneState = {
  wide: boolean
  close: () => void
}

export const DetailPaneContext = createContext<DetailPaneState | null>(null)

export function useDetailPane(): DetailPaneState {
  const pane = useContext(DetailPaneContext)
  if (!pane) throw new Error("useDetailPane must be used inside DetailPane")
  return pane
}
