"use client"

import { createContext, useContext } from "react"

/**
 * The pathname the app was on before this one, within this page load (#1384,
 * two-area spec D5); null after a hard load. A detail's close reads it to
 * pop the history entry it pushed rather than push a new one.
 */
export const PreviousPathnameContext = createContext<string | null>(null)

export function usePreviousPathname(): string | null {
  return useContext(PreviousPathnameContext)
}
