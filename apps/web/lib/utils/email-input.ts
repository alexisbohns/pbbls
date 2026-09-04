/**
 * Email input normalization for the auth surfaces.
 *
 * Product policy bans `+` in email input on every client (anti Gmail-alias
 * abuse), recorded in `docs/decisions/log.md` (2026-05-26) and scoped there to
 * `ios, webapp`. iOS (`AuthView.normalizeEmailInput`) and Android
 * (`AuthLogic.normalizeEmailInput`) already enforce it; web did not, which made
 * the policy bypassable on the surface most exposed to alias abuse and left the
 * three clients disagreeing about what a valid address even is.
 */

/** The one character the policy bans. */
export const DISALLOWED_EMAIL_CHAR = "+"

/**
 * Lowercases and strips `+` from raw email input. Real-time scrubbing keeps the
 * visible field, the submitted value, and the server's view of the address in
 * sync — the same contract as the iOS and Android helpers this mirrors.
 */
export function normalizeEmailInput(raw: string): string {
  return raw.toLowerCase().replaceAll(DISALLOWED_EMAIL_CHAR, "")
}

/**
 * Whether raw input carries the banned character. Callers must check this
 * BEFORE normalizing: after the strip there is nothing left to detect, and the
 * inline explanation is the only way a user learns why their keystroke
 * vanished. Lowercasing is silent on purpose; this is not.
 */
export function hasDisallowedEmailChar(raw: string): boolean {
  return raw.includes(DISALLOWED_EMAIL_CHAR)
}
