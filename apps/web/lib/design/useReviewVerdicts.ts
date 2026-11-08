"use client"

import { useCallback, useMemo, useSyncExternalStore } from "react"

/** The maintainer's verdict on a flagged glyph (#1607 review). */
export type Verdict = "ok" | "fix" | "drop-twin"

const ORDER: readonly Verdict[] = ["ok", "fix", "drop-twin"]
const KEY = "pbbls-glyph-review-v1"

export function nextVerdict(current: Verdict | undefined): Verdict | undefined {
  if (current === undefined) return ORDER[0]
  return ORDER[ORDER.indexOf(current) + 1]
}

function parse(raw: string | null): Record<string, Verdict> {
  try {
    const parsed: unknown = raw ? JSON.parse(raw) : {}
    if (typeof parsed !== "object" || parsed === null) return {}
    const out: Record<string, Verdict> = {}
    for (const [name, v] of Object.entries(parsed)) if (v === "ok" || v === "fix" || v === "drop-twin") out[name] = v
    return out
  } catch {
    return {}
  }
}

// The raw JSON is the store's snapshot (a string is stable between reads); `undefined` means not read yet.
let current: string | null | undefined
const listeners = new Set<() => void>()

function getSnapshot(): string | null {
  if (current === undefined) {
    try {
      current = window.localStorage.getItem(KEY)
    } catch {
      current = null
    }
  }
  return current
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  const onStorage = (e: StorageEvent) => {
    if (e.key !== KEY) return
    current = e.newValue
    listener()
  }
  window.addEventListener("storage", onStorage)
  return () => {
    listeners.delete(listener)
    window.removeEventListener("storage", onStorage)
  }
}

function write(next: Record<string, Verdict>) {
  current = JSON.stringify(next)
  try {
    window.localStorage.setItem(KEY, current)
  } catch {
    // Private mode or blocked storage: the verdicts live until reload.
  }
  listeners.forEach((l) => l())
}

/** Verdicts kept in this browser only; `copy` puts them on the clipboard as JSON. */
export function useReviewVerdicts() {
  const raw = useSyncExternalStore(subscribe, getSnapshot, () => null)
  const verdicts = useMemo(() => parse(raw), [raw])
  const cycle = useCallback(
    (name: string) => {
      const next = { ...verdicts }
      const v = nextVerdict(verdicts[name])
      if (v) next[name] = v
      else delete next[name]
      write(next)
    },
    [verdicts],
  )
  const set = useCallback(
    (name: string, verdict: Verdict | undefined) => {
      const next = { ...verdicts }
      if (verdict) next[name] = verdict
      else delete next[name]
      write(next)
    },
    [verdicts],
  )
  const copy = useCallback(async () => {
    await navigator.clipboard.writeText(JSON.stringify({ verdicts, copiedAt: new Date().toISOString() }, null, 1))
  }, [verdicts])
  return { verdicts, cycle, set, copy }
}
