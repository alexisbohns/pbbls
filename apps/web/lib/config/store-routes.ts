/**
 * Routes whose screens read the eagerly-loaded global store (or the provider
 * that owns it). On these, a failed store load must show a failure state rather
 * than each screen's "nothing here yet" copy.
 *
 * Deliberately NOT derived from AuthGate's PROTECTED_PREFIXES: that list answers
 * "does this route require a session", which is a different question with a
 * currently different membership (/wallet, /achievements, /drafts and /settings
 * reach the store through components rather than through app/*\/page.tsx).
 */
export const STORE_BACKED_PREFIXES = [
  "/path",
  "/record",
  "/pebble",
  "/collections",
  "/souls",
  "/glyphs",
  "/carve",
  "/profile",
  "/connections",
  "/wallet",
  "/achievements",
  "/drafts",
  "/settings",
] as const

/**
 * Exact-segment prefix match. A bare `startsWith` would match "/pathological"
 * against "/path"; this is the same shape AuthGate uses.
 */
export function isStoreBackedRoute(pathname: string): boolean {
  return STORE_BACKED_PREFIXES.some(
    (prefix) => pathname === prefix || pathname.startsWith(prefix + "/"),
  )
}
