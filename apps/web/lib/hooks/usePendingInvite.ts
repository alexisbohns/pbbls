"use client"

import { useCallback, useSyncExternalStore } from "react"

const STORAGE_KEY = "pbbls-pending-invite"
const CHANGE_EVENT = "pbbls-pending-invite-change"

// The token is 43-char base64url today, but the client must not hard-code the
// server's token length — validate loosely: non-empty, bounded, URL-safe
// charset. Anything else (older build, hand-edited storage) is discarded
// rather than trusted.
const MAX_TOKEN_LENGTH = 200
const TOKEN_PATTERN = /^[A-Za-z0-9_-]+$/

function isPlausibleToken(value: string | null): value is string {
  return (
    value !== null &&
    value.length > 0 &&
    value.length < MAX_TOKEN_LENGTH &&
    TOKEN_PATTERN.test(value)
  )
}

function subscribe(callback: () => void) {
  window.addEventListener("storage", callback)
  window.addEventListener(CHANGE_EVENT, callback)
  return () => {
    window.removeEventListener("storage", callback)
    window.removeEventListener(CHANGE_EVENT, callback)
  }
}

function getSnapshot(): string | null {
  const stored = localStorage.getItem(STORAGE_KEY)
  return isPlausibleToken(stored) ? stored : null
}

function getServerSnapshot(): string | null {
  return null
}

/**
 * The pending connection invite (M49, design D12): when a signed-out visitor
 * opens `/invite/<token>`, the token is parked here so it survives the OAuth
 * tab round-trip (and a future email-confirmation round-trip); once auth AND
 * onboarding complete, `PendingInviteRedirect` routes the user back to
 * `/invite/<token>` for an explicit accept tap — accept is never fired
 * implicitly on sign-up.
 *
 * `localStorage` behind the `useSyncExternalStore` custom-event pattern
 * (`useLocale` precedent) — components never touch `localStorage` directly
 * (docs/agents/data-and-async.md).
 */
export function usePendingInvite() {
  const token = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot)

  const setToken = useCallback((value: string) => {
    if (!isPlausibleToken(value)) {
      console.warn("[pending-invite] refusing to store malformed token")
      return
    }
    localStorage.setItem(STORAGE_KEY, value)
    window.dispatchEvent(new Event(CHANGE_EVENT))
  }, [])

  const clear = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY)
    window.dispatchEvent(new Event(CHANGE_EVENT))
  }, [])

  return { token, setToken, clear }
}
