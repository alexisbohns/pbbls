/**
 * The web half of the recent sign-in check (#976, #977).
 *
 * GoTrue stamps every real authentication into the access token's `amr` claim
 * (`[{"method":"password","timestamp":<epoch s>}]`), and a refresh keeps the
 * stamps, so a fresh stamp means the credential was proven recently. The
 * server's `recent_auth_ok` (20260927120000) applies the same rule; this copy
 * only decides whether to PROMPT, and the server stays the authority.
 *
 * The window is duplicated in that migration, in Android's `RecentAuth.WINDOW`
 * and in iOS's `RecentAuth.window`: change all four together.
 *
 * The payload is read without verifying the signature. That is fine for a UX
 * decision about our own token, and never for a security one.
 */

export const RECENT_AUTH_WINDOW_SECONDS = 10 * 60

/** The raised condition's text, shared by the SQL, the edge function and every client. */
export const REAUTH_REQUIRED = "reauth_required"

/** How the signed-in person proves it's them again. */
export type ReauthMethod = "password" | "google" | "apple"

function payloadOf(token: string): Record<string, unknown> | null {
  const part = token.split(".")[1]
  if (!part) return null
  try {
    const b64 = part.replace(/-/g, "+").replace(/_/g, "/")
    const padded = b64.padEnd(Math.ceil(b64.length / 4) * 4, "=")
    const bytes = Uint8Array.from(atob(padded), (c) => c.charCodeAt(0))
    const parsed: unknown = JSON.parse(new TextDecoder().decode(bytes))
    return parsed !== null && typeof parsed === "object" && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : null
  } catch {
    return null
  }
}

/**
 * The newest numeric `amr` timestamp (epoch seconds), or null when there is
 * none. "Any stamp within the window" is the same as "the newest one is".
 */
export function newestAmrStamp(accessToken: string | null | undefined): number | null {
  if (!accessToken) return null
  const amr = payloadOf(accessToken)?.amr
  if (!Array.isArray(amr)) return null
  let newest: number | null = null
  for (const entry of amr) {
    if (entry === null || typeof entry !== "object") continue
    const stamp = (entry as Record<string, unknown>).timestamp
    if (typeof stamp === "number" && Number.isFinite(stamp) && (newest === null || stamp > newest)) {
      newest = stamp
    }
  }
  return newest
}

/** True when the token proves a sign-in no older than the window. Unparseable → false. */
export function isRecentSignIn(accessToken: string | null | undefined, nowMs: number = Date.now()): boolean {
  const newest = newestAmrStamp(accessToken)
  return newest !== null && newest >= nowMs / 1000 - RECENT_AUTH_WINDOW_SECONDS
}

/**
 * The proof to ask for. An email identity always means a password, even when
 * a provider is linked too. An empty list is an email account whose
 * identities were not loaded, which is the same rule the Password section uses.
 */
export function reauthMethod(providers: readonly string[]): ReauthMethod {
  if (providers.length === 0 || providers.includes("email")) return "password"
  if (providers.includes("google")) return "google"
  if (providers.includes("apple")) return "apple"
  return "password"
}

/**
 * True for every shape of "sign in again": the profiles trigger's
 * `raise exception 'reauth_required'` (PostgREST message), and the
 * delete-account 428 and GoTrue's `reauthentication_needed`, both of which the
 * auth hook rethrows as `reauth_required`.
 */
export function isReauthRequired(err: unknown): boolean {
  return err instanceof Error && err.message === REAUTH_REQUIRED
}
