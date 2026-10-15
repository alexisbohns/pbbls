// ---------------------------------------------------------------------------
// The slugs `save_moment` / `complete_pebble` raise (migration
// 20261012090100), mapped to the i18n keys under `moment.errors` (R10). They
// arrive in the PostgREST error `message`; matching is `includes`, as on iOS.
// A `P0002` (no_data_found) with no known slug is a moment that is gone
// (Android's rule). `invalid_payload`, `invalid_snap` and anything unknown are
// client bugs or outages: the generic "Couldn't save". Delete failures never
// use this table (R15 has its own copy).
// ---------------------------------------------------------------------------

export const MOMENT_WRITE_SLUGS = {
  moment_empty: "momentEmpty",
  media_quota_exceeded: "mediaQuotaExceeded",
  pebble_needs_emotion: "pebbleNeedsEmotion",
  pebble_needs_valence: "pebbleNeedsValence",
  pebble_needs_domain: "pebbleNeedsDomain",
  soul_not_owned: "soulNotOwned",
  collection_not_owned: "collectionNotOwned",
  moment_not_found: "momentNotFound",
  not_authenticated: "notAuthenticated",
} as const

export type MomentErrorKey = (typeof MOMENT_WRITE_SLUGS)[keyof typeof MOMENT_WRITE_SLUGS] | "generic"

function keyFor(message: string, code: string | null): MomentErrorKey {
  for (const [slug, key] of Object.entries(MOMENT_WRITE_SLUGS)) {
    if (message.includes(slug)) return key
  }
  return code === "P0002" ? "momentNotFound" : "generic"
}

/** A refused moment write, carrying the copy key the screen shows. */
export class MomentWriteError extends Error {
  readonly key: MomentErrorKey
  readonly code: string | null

  constructor(fn: "save_moment" | "complete_pebble", error: { message?: string; code?: string } | null) {
    const message = error?.message ?? "unknown"
    super(`${fn} failed: ${message}`)
    this.name = "MomentWriteError"
    this.code = error?.code ?? null
    this.key = keyFor(message, this.code)
  }
}

/** The i18n key under `moment.errors` for any thrown value; anything but a MomentWriteError is "generic". */
export function momentErrorKey(err: unknown): MomentErrorKey {
  return err instanceof MomentWriteError ? err.key : "generic"
}
