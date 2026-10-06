"use client"

import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { createClient } from "@/lib/supabase/client"
import { withTimeout } from "@/lib/utils/with-timeout"
import {
  checkConsentGate,
  gateCacheValue,
  parseActiveConsents,
  submitConsentGate,
  resolveGateState,
  type ActiveConsent,
  type ConsentGateState,
  type GateApplies,
  type GateKind,
  type GateVerdict,
} from "@/lib/auth/consent-gate"

const CACHE_KEY = "pbbls-consent-gate"

/**
 * A passed check, cached on the device as one `"<userId>|<fingerprint>"`
 * string (Android design D7). Safe to trust: no act the gate asks for can stop
 * being satisfied without a version bump, which changes the fingerprint —
 * `terms`, `privacy` and `age_assurance` cannot be withdrawn, and withdrawing
 * `health_data` deletes the account. Storage can throw (private mode, blocked
 * site data); a failed read is simply a cache miss, which checks the network.
 */
function readCache(userId: string): boolean {
  try {
    return window.localStorage.getItem(CACHE_KEY) === gateCacheValue(userId)
  } catch {
    return false
  }
}

function writeCache(userId: string) {
  try {
    window.localStorage.setItem(CACHE_KEY, gateCacheValue(userId))
  } catch {
    // A cache miss next time costs one read; nothing to do.
  }
}

/**
 * The live ledger rows, reduced to kind + version. A plain owner select —
 * single table, RLS-scoped to the caller. It deliberately selects no
 * timestamps (as Android's ConsentService does): a column the gate does not
 * need is a cross-surface decoder it does not need either. A returned
 * PostgREST error is thrown, not laundered into an empty ledger (#784).
 */
async function loadActiveConsents(): Promise<ActiveConsent[]> {
  const supabase = createClient()
  const { data, error } = await withTimeout(
    supabase
      .from("user_consents")
      .select("kind, document_version")
      .is("withdrawn_at", null)
      .is("superseded_at", null),
    10000,
    "consent gate read",
  )
  if (error) throw new Error(error.message)
  return parseActiveConsents(data)
}

/**
 * Re-consent is collected outside signup, so it is recorded as `web_settings`,
 * as the Settings grant is (#788, age spec §8).
 */
async function recordConsent(kind: GateKind, version: string): Promise<void> {
  const supabase = createClient()
  const { error } = await withTimeout(
    supabase.rpc("record_consent", {
      p_kind: kind,
      p_document_version: version,
      p_source: "web_settings",
    }),
    10000,
    "record consent",
  )
  if (error) throw new Error(error.message)
}

export type { ConsentGateState }

type Result = { userId: string; verdict: GateVerdict }

/**
 * The post-auth consent gate's state for one session (#788).
 *
 * `applies` comes from `gateApplies`. The check runs when it becomes
 * `applies`, which is also the moment an account finishes onboarding, so the
 * verdict always reflects what onboarding's own ConsentGate just recorded.
 * Every verdict carries the user id it was reached for, so a stale verdict can
 * never let a different user through.
 */
export function useConsentGate(
  userId: string | null,
  applies: GateApplies,
) {
  const [result, setResult] = useState<Result | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [recordFailed, setRecordFailed] = useState(false)
  // Bumped by retry() to re-run the effect's check.
  const [attempt, setAttempt] = useState(0)
  const currentUser = useRef(userId)
  useEffect(() => {
    currentUser.current = userId
  }, [userId])

  // Read once per user; a pass reached in this session lands in `result`.
  const cached = useMemo(() => (userId ? readCache(userId) : false), [userId])
  // Once passed, a session stays open. The auth hook re-fetches the profile on
  // every auth event (token refresh included), which flips `applies` through
  // `pending`; re-gating on that would unmount the app under the user's hands.
  const satisfied =
    result !== null && result.userId === userId && result.verdict.status === "satisfied"
  const shouldCheck = userId !== null && applies === "applies" && !cached && !satisfied

  useEffect(() => {
    if (!shouldCheck || userId === null) return
    let cancelled = false
    // Defer past the synchronous render boundary to satisfy
    // react-hooks/set-state-in-effect (mirrors useConsents).
    void (async () => {
      await Promise.resolve()
      if (cancelled) return
      const verdict = await checkConsentGate(loadActiveConsents)
      if (cancelled || currentUser.current !== userId) return
      if (verdict.status === "satisfied") writeCache(userId)
      setResult({ userId, verdict })
    })()
    return () => {
      cancelled = true
    }
  }, [shouldCheck, userId, attempt])

  const retry = useCallback(() => {
    setResult(null)
    setRecordFailed(false)
    setAttempt((n) => n + 1)
  }, [])

  const submit = useCallback(async (missing: GateKind[]) => {
    const uid = currentUser.current
    if (uid === null) return
    setSubmitting(true)
    setRecordFailed(false)
    const verdict = await submitConsentGate(missing, recordConsent, loadActiveConsents)
    if (currentUser.current !== uid) return
    setSubmitting(false)
    if (verdict.status === "record-failed") {
      setRecordFailed(true)
      return
    }
    if (verdict.status === "satisfied") writeCache(uid)
    setResult({ userId: uid, verdict })
  }, [])

  const state = resolveGateState({ userId, applies, cached, result, submitting, recordFailed })

  return { state, submit, retry }
}
