"use client"

import { createContext, useContext, useEffect, useMemo, useRef } from "react"

/**
 * The stones of one week, pressable by id (#1052, spec D8). A contents link
 * scrolls to its stone and ripples it without opening the pebble, so it needs
 * the stone's own press rather than a click. `SpineWeek` owns the registry;
 * each `SpinePebble` registers its press under its MOMENT id (#1050).
 */
export type SpinePresses = {
  register: (id: string, press: () => void) => () => void
  press: (id: string) => void
}

export const SpinePressContext = createContext<SpinePresses | null>(null)

export function useSpinePresses(): SpinePresses {
  const presses = useRef(new Map<string, () => void>())
  return useMemo(
    () => ({
      register(id, press) {
        presses.current.set(id, press)
        return () => {
          if (presses.current.get(id) === press) presses.current.delete(id)
        }
      },
      press(id) {
        presses.current.get(id)?.()
      },
    }),
    [],
  )
}

/** Registers a stone's press for the week's contents. A no-op outside a week. */
export function useRegisterSpinePress(id: string, press: () => void): void {
  const presses = useContext(SpinePressContext)
  useEffect(() => presses?.register(id, press), [presses, id, press])
}
