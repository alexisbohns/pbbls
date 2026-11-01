"use client"

import { createContext, useContext } from "react"

/**
 * The tab last shown in this page load (#1384, two-area spec D2): the one a
 * pebble opened over, since a pebble opens from every tab. Null after a hard
 * load of a pebble, where the Path is the tab under it.
 */
export const LastTabContext = createContext<string | null>(null)

export function useLastTab(): string | null {
  return useContext(LastTabContext)
}
