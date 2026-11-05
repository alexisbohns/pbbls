"use client"

import { createContext, useContext } from "react"

/**
 * Which specimens a frame of /sandbox/design shows (`?specimens=a,b`). Null
 * renders everything. Under a scope, sections drop their headings so a frame
 * holds only its group.
 */
export const SpecimenScope = createContext<ReadonlySet<string> | null>(null)

export function useSpecimenScope(): ReadonlySet<string> | null {
  return useContext(SpecimenScope)
}
