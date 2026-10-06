/**
 * What survives a provider re-auth (#977).
 *
 * On web, Google and Apple re-auth is a full-page OAuth redirect, so whatever
 * the settings page had staged would be lost. Before redirecting, the page
 * stashes the pending action and its staged edits in sessionStorage. When the
 * callback brings the person back (`/settings?reauth=1`), the page restores the
 * edits and reopens the step: the delete confirmation, or the form ready to
 * save. It never runs the action on its own.
 *
 * Never holds a password: provider-only accounts have no password field, and
 * the password path re-auths in place without a redirect.
 */

export const PENDING_REAUTH_KEY = "pbbls.pending-reauth"

/** The query flag the OAuth `next` carries. Without it, a stash is stale and discarded. */
export const REAUTH_RETURN_PARAM = "reauth"

/** Long enough for a slow OAuth round trip; a stash older than this is abandoned. */
const MAX_AGE_MS = 15 * 60 * 1000

/** The settings page's staged edits. `null` means "unchanged", as on the page. */
export type StagedSettings = {
  name: string | null
  /** Unlike the page's `undefined`, JSON keeps `null`; `unchanged` says whether it was staged. */
  glyphId: { unchanged: true } | { unchanged: false; value: string | null }
  handle: string | null
  isPublic: boolean | null
}

export type PendingReauth =
  | { purpose: "delete"; userId: string; savedAt: number }
  | { purpose: "save"; userId: string; savedAt: number; form: StagedSettings }

const isStringOrNull = (v: unknown): v is string | null => v === null || typeof v === "string"

function parseForm(raw: unknown): StagedSettings | null {
  if (raw === null || typeof raw !== "object") return null
  const f = raw as Record<string, unknown>
  const g = f.glyphId as Record<string, unknown> | null | undefined
  let glyphId: StagedSettings["glyphId"]
  if (g && g.unchanged === true) glyphId = { unchanged: true }
  else if (g && g.unchanged === false && isStringOrNull(g.value)) glyphId = { unchanged: false, value: g.value }
  else return null
  if (!isStringOrNull(f.name) || !isStringOrNull(f.handle)) return null
  if (!(f.isPublic === null || typeof f.isPublic === "boolean")) return null
  return { name: f.name, glyphId, handle: f.handle, isPublic: f.isPublic }
}

/**
 * Reads a stash. Anything malformed, expired or from the future is null, so a
 * corrupted or abandoned entry can never reopen a dialog.
 */
export function parsePendingReauth(raw: string | null, nowMs: number = Date.now()): PendingReauth | null {
  if (!raw) return null
  let value: unknown
  try {
    value = JSON.parse(raw)
  } catch {
    return null
  }
  if (value === null || typeof value !== "object") return null
  const v = value as Record<string, unknown>
  if (typeof v.userId !== "string" || v.userId === "") return null
  if (typeof v.savedAt !== "number" || v.savedAt > nowMs || nowMs - v.savedAt > MAX_AGE_MS) return null
  if (v.purpose === "delete") return { purpose: "delete", userId: v.userId, savedAt: v.savedAt }
  if (v.purpose === "save") {
    const form = parseForm(v.form)
    return form ? { purpose: "save", userId: v.userId, savedAt: v.savedAt, form } : null
  }
  return null
}

export function serializePendingReauth(pending: PendingReauth): string {
  return JSON.stringify(pending)
}

/**
 * sessionStorage access. Every call is guarded: storage can be unavailable or
 * throw (private mode, blocked site data). A missing stash only costs the
 * restored edits; the person is still confirmed and can redo the action.
 */
export function stashPendingReauth(pending: PendingReauth): void {
  try {
    window.sessionStorage.setItem(PENDING_REAUTH_KEY, serializePendingReauth(pending))
  } catch (err) {
    console.warn("[settings] could not stash the pending re-auth:", err)
  }
}

/** Reads and removes the stash in one go, so it can resume at most once. */
export function takePendingReauth(): PendingReauth | null {
  try {
    const raw = window.sessionStorage.getItem(PENDING_REAUTH_KEY)
    window.sessionStorage.removeItem(PENDING_REAUTH_KEY)
    return parsePendingReauth(raw)
  } catch {
    return null
  }
}
