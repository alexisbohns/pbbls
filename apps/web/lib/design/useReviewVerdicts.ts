"use client"

import { useCallback, useMemo, useSyncExternalStore } from "react"

/** The maintainer's verdict on a flagged glyph (#1607 review). */
export type Verdict = "ok" | "fix" | "drop-twin"

const ORDER: readonly Verdict[] = ["ok", "fix", "drop-twin"]

export function nextVerdict(current: Verdict | undefined): Verdict | undefined {
  if (current === undefined) return ORDER[0]
  return ORDER[ORDER.indexOf(current) + 1]
}

function isVerdict(v: unknown): v is Verdict {
  return v === "ok" || v === "fix" || v === "drop-twin"
}

/** A JSON object of `name → T` read back from storage, dropping any entry `keep` refuses. */
export function parseRecord<T>(raw: string | null, keep: (v: unknown) => v is T): Record<string, T> {
  try {
    const parsed: unknown = raw ? JSON.parse(raw) : {}
    if (typeof parsed !== "object" || parsed === null) return {}
    const out: Record<string, T> = {}
    for (const [name, v] of Object.entries(parsed)) if (keep(v)) out[name] = v
    return out
  } catch {
    return {}
  }
}

const isNote = (v: unknown): v is string => typeof v === "string" && v.trim() !== ""

/**
 * One localStorage key as an external store: the raw JSON is the snapshot (a
 * string is stable between reads), `undefined` meaning not read yet.
 */
function localStore(key: string) {
  let current: string | null | undefined
  const listeners = new Set<() => void>()
  return {
    getSnapshot(): string | null {
      if (current === undefined) {
        try {
          current = window.localStorage.getItem(key)
        } catch {
          current = null
        }
      }
      return current
    },
    subscribe(listener: () => void): () => void {
      listeners.add(listener)
      const onStorage = (e: StorageEvent) => {
        if (e.key !== key) return
        current = e.newValue
        listener()
      }
      window.addEventListener("storage", onStorage)
      return () => {
        listeners.delete(listener)
        window.removeEventListener("storage", onStorage)
      }
    },
    write(next: Record<string, unknown>) {
      current = JSON.stringify(next)
      try {
        window.localStorage.setItem(key, current)
      } catch {
        // Private mode or blocked storage: the review lives until reload.
      }
      listeners.forEach((l) => l())
    },
  }
}

const VERDICTS = localStore("pbbls-glyph-review-v1")
const NOTES = localStore("pbbls-glyph-review-notes-v1")
const serverSnapshot = () => null

/**
 * Verdicts, and a note per glyph saying what a fix needs, kept in this browser
 * only; `copy` puts both on the clipboard as JSON (notes only for glyphs still
 * marked fix).
 */
export function useReviewVerdicts() {
  const rawVerdicts = useSyncExternalStore(VERDICTS.subscribe, VERDICTS.getSnapshot, serverSnapshot)
  const rawNotes = useSyncExternalStore(NOTES.subscribe, NOTES.getSnapshot, serverSnapshot)
  const verdicts = useMemo(() => parseRecord(rawVerdicts, isVerdict), [rawVerdicts])
  // A note being typed may be blank for a moment, so the store keeps any string; readers see the non-blank ones.
  const notes = useMemo(() => parseRecord(rawNotes, isNote), [rawNotes])

  const set = useCallback(
    (name: string, verdict: Verdict | undefined) => {
      const next = { ...verdicts }
      if (verdict) next[name] = verdict
      else delete next[name]
      VERDICTS.write(next)
    },
    [verdicts],
  )
  const cycle = useCallback((name: string) => set(name, nextVerdict(verdicts[name])), [set, verdicts])
  const setNote = useCallback(
    (name: string, text: string) => {
      const next: Record<string, string> = { ...parseRecord(rawNotes, (v): v is string => typeof v === "string") }
      if (text === "") delete next[name]
      else next[name] = text
      NOTES.write(next)
    },
    [rawNotes],
  )
  const draft = useCallback(
    (name: string): string => parseRecord(rawNotes, (v): v is string => typeof v === "string")[name] ?? "",
    [rawNotes],
  )
  const copy = useCallback(async () => {
    const fixNotes = Object.fromEntries(Object.entries(notes).filter(([name]) => verdicts[name] === "fix"))
    await navigator.clipboard.writeText(JSON.stringify({ verdicts, notes: fixNotes, copiedAt: new Date().toISOString() }, null, 1))
  }, [verdicts, notes])
  return { verdicts, notes, set, cycle, setNote, draft, copy }
}
