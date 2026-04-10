"use client"

import { useCallback, useSyncExternalStore } from "react"

const STORAGE_KEY = "pbbls-haptics"

function subscribe(callback: () => void) {
  window.addEventListener("storage", callback)
  return () => window.removeEventListener("storage", callback)
}

function getSnapshot(): boolean {
  return localStorage.getItem(STORAGE_KEY) !== "off"
}

function getServerSnapshot(): boolean {
  return true
}

export function useHaptics() {
  const enabled = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot)

  const setEnabled = useCallback((value: boolean) => {
    localStorage.setItem(STORAGE_KEY, value ? "on" : "off")
    window.dispatchEvent(new StorageEvent("storage", { key: STORAGE_KEY }))
  }, [])

  const vibrate = useCallback(
    (pattern: number | number[] = 10) => {
      if (enabled && typeof navigator !== "undefined" && navigator.vibrate) {
        navigator.vibrate(pattern)
      }
    },
    [enabled],
  )

  return { enabled, setEnabled, vibrate }
}
