"use client"

import { useCallback, useEffect, useState } from "react"
import { createClient } from "@/lib/supabase/client"
import { withTimeout } from "@/lib/utils/with-timeout"
import { activeConsent, type ConsentRow } from "@/lib/data/consent"
import {
  CONSENT_DOCUMENT_VERSION,
  type ConsentKind,
  type ConsentSource,
  type WithdrawableConsentKind,
} from "@/lib/config/consent"

const CONSENT_COLUMNS =
  "id, kind, document_version, source, granted_at, withdrawn_at, superseded_at"

/**
 * The signed-in user's consent ledger (Art. 9 explicit consent, F-2026-08-GDP-web-01).
 *
 * Reads are a plain owner select — single table, so no RPC is needed. Writes go
 * through the two definer RPCs, because `user_consents` has no client write
 * policy at all: the absence of a policy is the guard.
 *
 * `loading` is true for the initial load only. A `refresh()` triggered by a
 * write deliberately leaves it false so a consumer gating its render on it
 * (the onboarding gate) does not flash back to its loading state after the
 * user has just granted — and `record`/`withdraw` only resolve once that
 * refresh has landed, so anyone awaiting a write already sees fresh rows.
 */
export function useConsents() {
  const [rows, setRows] = useState<ConsentRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)

  const refresh = useCallback(async () => {
    // `createClient()` reads public env vars — instantiate per call inside the
    // async path so it never runs during SSR/prerender (mirrors useDomains).
    const supabase = createClient()
    const { data, error: loadError } = await withTimeout(
      supabase.from("user_consents").select(CONSENT_COLUMNS).order("granted_at", {
        ascending: false,
      }),
      10000,
      "load consents",
    )
    if (loadError) {
      console.error("[consents] load failed:", loadError.message)
      setError(new Error(loadError.message))
      setRows([])
    } else {
      setError(null)
      // The web Supabase client is untyped (`createBrowserClient` is called
      // without the generated `Database` generic), so PostgREST hands back
      // `any` here and the shape has to be asserted at the boundary. Dropping
      // the assertion needs the client typed first — a known separate finding
      // (F-2026-08-ARC-web-01), not this change. Same pattern as useDomains.
      setRows((data ?? []) as ConsentRow[])
    }
    setLoading(false)
  }, [])

  useEffect(() => {
    let cancelled = false
    // Defer past the synchronous render boundary to satisfy
    // react-hooks/set-state-in-effect (mirrors useConnections).
    void (async () => {
      await Promise.resolve()
      if (cancelled) return
      await refresh()
    })()
    return () => {
      cancelled = true
    }
  }, [refresh])

  const record = useCallback(
    async (kind: ConsentKind, source: ConsentSource) => {
      const supabase = createClient()
      const { error: rpcError } = await withTimeout(
        supabase.rpc("record_consent", {
          p_kind: kind,
          p_document_version: CONSENT_DOCUMENT_VERSION,
          p_source: source,
        }),
        10000,
        "record consent",
      )
      // The RPCs raise bare named messages (`invalid_kind`, `invalid_source`,
      // `no_active_consent`), so callers match on the message the way
      // useSupabaseAuth does for set_handle's codes.
      if (rpcError) throw new Error(rpcError.message)
      await refresh()
    },
    [refresh],
  )

  const withdraw = useCallback(
    async (kind: WithdrawableConsentKind) => {
      const supabase = createClient()
      const { error: rpcError } = await withTimeout(
        supabase.rpc("withdraw_consent", { p_kind: kind }),
        10000,
        "withdraw consent",
      )
      if (rpcError) throw new Error(rpcError.message)
      await refresh()
    },
    [refresh],
  )

  return {
    rows,
    loading,
    error,
    healthData: activeConsent(rows, "health_data"),
    publicProfile: activeConsent(rows, "public_profile"),
    record,
    withdraw,
    refresh,
  }
}
