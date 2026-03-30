"use client"

import { useSyncExternalStore } from "react"

const QUERY = "(display-mode: standalone)"

function subscribe(callback: () => void) {
  const mql = window.matchMedia(QUERY)
  mql.addEventListener("change", callback)
  return () => mql.removeEventListener("change", callback)
}

function getSnapshot(): boolean {
  if (window.matchMedia(QUERY).matches) return true
  if (
    "standalone" in navigator &&
    (navigator as Navigator & { standalone: boolean }).standalone
  )
    return true
  return false
}

function getServerSnapshot(): boolean {
  return false
}

export function useStandalone() {
  const isStandalone = useSyncExternalStore(
    subscribe,
    getSnapshot,
    getServerSnapshot,
  )
  return { isStandalone }
}
