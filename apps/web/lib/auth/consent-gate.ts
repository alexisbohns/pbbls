import { documentVersionFor, type ConsentKind } from "@/lib/config/consent"

/**
 * The post-auth consent gate (#788), mirroring Android's `ConsentGateLogic`
 * (#967, design `docs/superpowers/specs/2026-09-27-android-consent-gate-design.md`).
 * Pure, so the rule that decides whether someone can use the app is tested
 * without a browser or a network.
 */

/** The acts a gate can ask for. */
export type GateKind = Extract<ConsentKind, "terms" | "privacy" | "health_data" | "age_assurance">

/** Every act a signed-in user must have on record, in the order the gate shows them. */
export const GATE_KINDS: readonly GateKind[] = ["terms", "privacy", "health_data", "age_assurance"]

/** One live ledger row, reduced to what the gate reads. */
export type ActiveConsent = { kind: string; document_version: string }

/**
 * Numeric semver comparison: is `version` the same as or newer than `required`?
 * An unparsable version satisfies nothing. Port of Android's
 * `ConsentGateLogic.isAtLeast`.
 */
export function isAtLeast(version: string, required: string): boolean {
  const have = parseVersion(version)
  const need = parseVersion(required)
  if (!have || !need) return false
  for (let i = 0; i < 3; i++) {
    if (have[i] !== need[i]) return have[i] > need[i]
  }
  return true
}

function parseVersion(version: string): number[] | null {
  const parts = version.split(".")
  if (parts.length !== 3 || !parts.every((p) => /^\d+$/.test(p))) return null
  return parts.map(Number)
}

/**
 * The required kinds `active` does not satisfy, in display order. A row
 * satisfies when its version is the same as or NEWER than this build's
 * (Android design D8).
 *
 * The comparison has to live here: `record_consent` supersedes an active grant
 * at ANY different version, newer included, so asking again for a kind already
 * held at a newer version (a stale bundle, or a native release ahead of this
 * deploy) would downgrade an accountability record.
 */
export function missingConsents(active: readonly ActiveConsent[]): GateKind[] {
  return GATE_KINDS.filter(
    (kind) =>
      !active.some((row) => row.kind === kind && isAtLeast(row.document_version, documentVersionFor(kind))),
  )
}

/**
 * Identifies the set of versions this build requires, in Android's format. A
 * passed check is cached against it, so any version bump invalidates the cache.
 */
export function gateFingerprint(): string {
  return GATE_KINDS.map((kind) => `${kind}@${documentVersionFor(kind)}`).join(",")
}

/** The device-cache value for a passed check: one user, one set of versions. */
export function gateCacheValue(userId: string): string {
  return `${userId}|${gateFingerprint()}`
}

/**
 * Narrows a PostgREST `select kind, document_version` payload without a type
 * assertion. A row with a missing or non-string field satisfies nothing (it is
 * dropped, and its kind is then asked for). A payload that is not an array at
 * all is a failed read, not an empty ledger: it throws, and the gate fails
 * closed.
 */
export function parseActiveConsents(data: unknown): ActiveConsent[] {
  if (!Array.isArray(data)) throw new Error("consent read returned a non-array payload")
  const items: unknown[] = data
  const rows: ActiveConsent[] = []
  for (const item of items) {
    if (typeof item !== "object" || item === null) continue
    const kind: unknown = Reflect.get(item, "kind")
    const version: unknown = Reflect.get(item, "document_version")
    if (typeof kind === "string" && typeof version === "string") {
      rows.push({ kind, document_version: version })
    }
  }
  return rows
}

/** The gate's answer for one signed-in user. */
export type GateVerdict =
  | { status: "satisfied" }
  | { status: "required"; missing: GateKind[] }
  /** The ledger could not be read. Fails closed (Android design D4). */
  | { status: "failed" }

/**
 * Reads the ledger and decides. Any failure, a thrown transport error or a
 * returned PostgREST error alike, is `failed`: never `satisfied`, and never
 * `required` either, because an empty answer from a failed read would look
 * like a brand-new account.
 */
export async function checkConsentGate(
  loadActive: () => Promise<ActiveConsent[]>,
): Promise<GateVerdict> {
  let active: ActiveConsent[]
  try {
    active = await loadActive()
  } catch (err) {
    console.error("[consent-gate] consent read failed:", err)
    return { status: "failed" }
  }
  const missing = missingConsents(active)
  return missing.length === 0 ? { status: "satisfied" } : { status: "required", missing }
}

/**
 * Records each missing act, then re-reads. One idempotent `record_consent` per
 * act, as the OAuth callback does: the acts are independent, so a partial
 * failure leaves the gate asking only for what is still missing.
 *
 * `record-failed` keeps the user on the same screen with an error. It still
 * re-reads, so its `missing` drops the acts recorded before the failure and a
 * retry does not ask for them again. If that re-read fails too, `missing`
 * stays as it was (re-recording an act at the same version is a no-op). A
 * record that succeeded followed by a failed re-read is `failed`, like any
 * read.
 */
export async function submitConsentGate(
  missing: readonly GateKind[],
  record: (kind: GateKind, version: string) => Promise<void>,
  loadActive: () => Promise<ActiveConsent[]>,
): Promise<GateVerdict | { status: "record-failed"; missing: GateKind[] }> {
  try {
    for (const kind of missing) {
      await record(kind, documentVersionFor(kind))
    }
  } catch (err) {
    console.error("[consent-gate] record consent failed:", err)
    const verdict = await checkConsentGate(loadActive)
    if (verdict.status === "satisfied") return verdict
    return {
      status: "record-failed",
      missing: verdict.status === "required" ? verdict.missing : [...missing],
    }
  }
  return checkConsentGate(loadActive)
}

/** Whether the gate covers this session; see `gateApplies`. */
export type GateApplies = "skip" | "pending" | "applies"

/** What the auth context knows, reduced to what the gate needs. */
export type GateAuthInput = {
  /** The session check has not finished, so `isAuthenticated` means nothing yet. */
  isLoading: boolean
  isAuthenticated: boolean
  isProfileLoading: boolean
  /** null when there is no profile row OR when it could not be read (#784). */
  profile: { onboarding_completed: boolean } | null
}

/**
 * Whether the gate applies to this session.
 *
 * - `pending` while the session check is still running. Until it finishes
 *   there is no user to read, and "no user yet" must not pass for "signed
 *   out": outside AuthGate's protected routes the page would render before
 *   the gate decides.
 * - `skip` for a signed-out visitor, and for an account the profile PROVES is
 *   mid-onboarding: onboarding's own `ConsentGate` covers Art. 9 there, and the
 *   gate takes over once onboarding completes.
 * - `pending` while the profile is still loading.
 * - `applies` otherwise, including when the profile is null. A null profile
 *   cannot tell "no row yet" from "could not be read" (#784), so it fails
 *   closed: asking a new account for all four acts up front is a smaller harm
 *   than letting an established one through with nothing on record.
 */
export function gateApplies(auth: GateAuthInput): GateApplies {
  if (auth.isLoading) return "pending"
  if (!auth.isAuthenticated) return "skip"
  if (auth.isProfileLoading) return "pending"
  if (auth.profile && auth.profile.onboarding_completed === false) return "skip"
  return "applies"
}

export type ConsentGateState =
  /** Not gating: a signed-out visitor, a pass, or a session the gate skips. */
  | { status: "open" }
  /** Deciding. Blocks, because the gate fails closed. */
  | { status: "checking" }
  | { status: "required"; missing: GateKind[]; submitting: boolean; recordFailed: boolean }
  | { status: "failed" }

/** Everything the gate's render decision reads. */
export type GateStateInput = {
  /** The signed-in user; null when signed out AND while the session loads. */
  userId: string | null
  applies: GateApplies
  /** A pass for this user at these versions is cached on the device. */
  cached: boolean
  /** The latest verdict, with the user it was reached for. */
  result: { userId: string; verdict: GateVerdict } | null
  submitting: boolean
  recordFailed: boolean
}

/**
 * What the gate renders. Only `open` renders the route.
 *
 * A null user is `open` only once `applies` is `skip`, i.e. once the session
 * check has finished and found nobody. While it runs, `applies` is `pending`
 * and the answer is `checking`. A pass (cached, or reached this session) keeps
 * the gate open through later `pending` flips, so a token refresh never
 * unmounts the app. Every verdict carries the user it was reached for, so a
 * stale verdict never lets a different user through.
 */
export function resolveGateState(input: GateStateInput): ConsentGateState {
  const { userId, applies, cached, result } = input
  if (applies === "skip") return { status: "open" }
  if (userId === null) return { status: "checking" }
  const verdict = result !== null && result.userId === userId ? result.verdict : null
  if (cached || verdict?.status === "satisfied") return { status: "open" }
  // No verdict for this user yet (the profile may still be loading). An
  // earlier verdict stays on screen while a re-check runs, so boxes the user
  // already ticked are not thrown away.
  if (verdict === null) return { status: "checking" }
  if (verdict.status === "failed") return { status: "failed" }
  return {
    status: "required",
    missing: verdict.missing,
    submitting: input.submitting,
    recordFailed: input.recordFailed,
  }
}

/**
 * Routes the gate never covers. The legal documents must stay readable while
 * the gate asks you to accept them; its links open them in a new tab.
 */
export function isGateExemptPath(pathname: string): boolean {
  return pathname === "/docs" || pathname.startsWith("/docs/")
}
