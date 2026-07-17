"use client"

import { useEffect, useState, useSyncExternalStore } from "react"
import { LAB_NOTE_PREFILL_KEY, type LabNotePrefill } from "@/lib/logs/parse-lab-note"
import type { LogRow, LogSpecies } from "@/lib/logs/types"
import { LogForm } from "./LogForm"
import { createLog } from "../actions"

// Stable no-op subscription: the hydration flag flips exactly once (SSR→client)
// and useSyncExternalStore handles that transition itself.
const subscribeNoop = () => () => {}

/**
 * Wraps the shared LogForm on the New-log page. It consumes a Lab Note snippet
 * stashed by NewLogButton (from the clipboard) and hands its values to LogForm
 * as default values.
 *
 * The snippet is read once in a state initializer, but only applied once
 * hydration completes — `useSyncExternalStore` returns false during SSR and the
 * hydration render, then true on the client, so the server render and the first
 * client render are both the blank form (no hydration mismatch). The `key` flip
 * then remounts LogForm so its uncontrolled inputs pick up the prefill. The
 * effect clears the stash so a later refresh starts blank.
 */
export function PrefillableLogForm({ initialSpecies }: { initialSpecies?: LogSpecies }) {
  const isHydrated = useSyncExternalStore<boolean>(subscribeNoop, () => true, () => false)
  const [prefill] = useState<LabNotePrefill | null>(readStashedPrefill)

  useEffect(() => {
    try {
      window.sessionStorage.removeItem(LAB_NOTE_PREFILL_KEY)
    } catch {
      // Storage unavailable — nothing to clear.
    }
  }, [])

  const active = isHydrated ? prefill : null
  const log = active ? toSyntheticLog(active, initialSpecies) : null

  return (
    <div className="space-y-4">
      {active ? (
        <div
          role="status"
          className="border-border bg-muted text-muted-foreground rounded-lg border px-4 py-3 text-sm"
        >
          Prefilled from your clipboard — review the fields, then click{" "}
          <span className="text-foreground font-medium">Save draft</span>.
        </div>
      ) : null}
      <LogForm
        // Remount after hydration so the prefilled default values take effect.
        key={isHydrated ? "hydrated" : "ssr"}
        log={log}
        action={createLog}
        submitLabel="Save draft"
        initialSpecies={initialSpecies}
      />
    </div>
  )
}

// Read (but don't clear) the stashed snippet. Pure enough to run in a state
// initializer, including React's dev double-invoke — the effect clears it.
function readStashedPrefill(): LabNotePrefill | null {
  if (typeof window === "undefined") return null
  try {
    const raw = window.sessionStorage.getItem(LAB_NOTE_PREFILL_KEY)
    return raw ? (JSON.parse(raw) as LabNotePrefill) : null
  } catch {
    return null
  }
}

// LogForm reads its default values off a LogRow. Build a synthetic row from the
// parsed snippet; columns the snippet doesn't cover stay empty/null.
function toSyntheticLog(prefill: LabNotePrefill, initialSpecies?: LogSpecies): LogRow {
  return {
    id: "",
    species: (prefill.species ?? initialSpecies ?? "") as LogRow["species"],
    platform: (prefill.platform ?? "") as LogRow["platform"],
    status: (prefill.status ?? "") as LogRow["status"],
    title_en: prefill.title_en ?? "",
    title_fr: prefill.title_fr ?? null,
    summary_en: prefill.summary_en ?? "",
    summary_fr: prefill.summary_fr ?? null,
    body_md_en: null,
    body_md_fr: null,
    cover_image_path: null,
    external_url: null,
    published: prefill.published ?? false,
    published_at: null,
    released_at: prefill.released_at ?? null,
    created_at: "",
    updated_at: "",
  }
}
