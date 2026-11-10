"use client"

import { useCallback, useMemo, useSyncExternalStore } from "react"

/** The maintainer's verdict on a glyph (#1607 review), in the order the dialog's footer lists them. */
export const VERDICTS = ["ok", "progress", "solid", "mark", "drop-twin", "incorrect", "fix"] as const
export type Verdict = (typeof VERDICTS)[number]

/** One key per verdict in the review dialog; every key but F moves on to the next glyph. */
export const VERDICT_KEYS: Readonly<Record<string, Verdict>> = { " ": "ok", p: "progress", s: "solid", m: "mark", d: "drop-twin", i: "incorrect", f: "fix" }

const LABELS: Readonly<Record<Verdict, string>> = {
  ok: "ok",
  progress: "needs progress",
  solid: "needs a solid",
  mark: "add a mark",
  "drop-twin": "drop twin",
  incorrect: "incorrect",
  fix: "fix",
}

export function verdictLabel(v: Verdict): string {
  return LABELS[v]
}

export function isVerdict(v: unknown): v is Verdict {
  return typeof v === "string" && (VERDICTS as readonly string[]).includes(v)
}

/** Every verdict but ok asks for a change. */
export function needsWork(v: Verdict | undefined): boolean {
  return v !== undefined && v !== "ok"
}

/** The tile pill cycles: none → ok → … → fix → none. */
export function nextVerdict(current: Verdict | undefined): Verdict | undefined {
  if (current === undefined) return VERDICTS[0]
  return VERDICTS[VERDICTS.indexOf(current) + 1]
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

const VERDICT_STORE = localStore("pbbls-glyph-review-v2")
const NOTES = localStore("pbbls-glyph-review-notes-v2")
const serverSnapshot = () => null

/**
 * Verdicts, and a note per glyph saying what a fix needs, kept in this browser
 * only; `copy` puts both on the clipboard as JSON (notes only for glyphs that
 * still need work).
 */
export function useReviewVerdicts() {
  const rawVerdicts = useSyncExternalStore(VERDICT_STORE.subscribe, VERDICT_STORE.getSnapshot, serverSnapshot)
  const rawNotes = useSyncExternalStore(NOTES.subscribe, NOTES.getSnapshot, serverSnapshot)
  const verdicts = useMemo(() => parseRecord(rawVerdicts, isVerdict), [rawVerdicts])
  // A note being typed may be blank for a moment, so the store keeps any string; readers see the non-blank ones.
  const notes = useMemo(() => parseRecord(rawNotes, isNote), [rawNotes])

  const set = useCallback(
    (name: string, verdict: Verdict | undefined) => {
      const next = { ...verdicts }
      if (verdict) next[name] = verdict
      else delete next[name]
      VERDICT_STORE.write(next)
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
    const workNotes = Object.fromEntries(Object.entries(notes).filter(([name]) => needsWork(verdicts[name])))
    await navigator.clipboard.writeText(JSON.stringify({ round: 2, verdicts, notes: workNotes, copiedAt: new Date().toISOString() }, null, 1))
  }, [verdicts, notes])
  return { verdicts, notes, set, cycle, setNote, draft, copy }
}
