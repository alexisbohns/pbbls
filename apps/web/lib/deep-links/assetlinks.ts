// Digital Asset Links payload for Android App Links (M49, design D11).
//
// The signing fingerprint is NOT hardcoded: it lives in the
// ANDROID_ASSETLINKS_SHA256 env var. #662 shipped this file as a static
// public/ asset carrying a REPLACE_WITH_PLAY_APP_SIGNING_SHA256 placeholder
// that was never substituted, so `autoVerify` could never succeed and the
// Android app lost its only invite-accept surface (#697). A committed constant
// can go stale the same way and needs a deploy to rotate; an env var is set
// once in Vercel and can be corrected without touching code.
//
// A cert fingerprint is public by design (assetlinks.json is world-readable),
// so this is configuration, not a secret.

/** Package name declared by apps/android/app/build.gradle.kts. */
export const ANDROID_PACKAGE_NAME = "app.pbbls.android"

/** 32 hex byte pairs, colon-separated — the form Google's verifier expects. */
const FINGERPRINT_PATTERN = /^[0-9A-F]{2}(:[0-9A-F]{2}){31}$/

export type AssetLinksStatement = {
  relation: string[]
  target: {
    namespace: "android_app"
    package_name: string
    sha256_cert_fingerprints: string[]
  }
}

/**
 * Normalises one raw fingerprint to `AA:BB:…` uppercase, or returns null if it
 * isn't 32 hex bytes. The Play Console copies as colon-separated uppercase, but
 * `keytool` output and hand-typed values vary in case and separators, so accept
 * both shapes rather than silently publishing an unverifiable file.
 */
export function normalizeFingerprint(raw: string): string | null {
  const hex = raw.trim().replace(/:/g, "").toUpperCase()
  if (!/^[0-9A-F]{64}$/.test(hex)) return null
  const normalized = (hex.match(/.{2}/g) ?? []).join(":")
  return FINGERPRINT_PATTERN.test(normalized) ? normalized : null
}

/**
 * Parses the env var into fingerprints. Comma/whitespace separated so more than
 * one key can be declared at a time — Play App Signing and the upload key
 * differ, and a debug key is useful while testing on a device. Invalid entries
 * are dropped rather than failing the whole list: one malformed extra key must
 * not take down verification for a good one.
 */
export function parseFingerprints(raw: string | undefined): string[] {
  if (!raw) return []
  const seen = new Set<string>()
  for (const part of raw.split(/[\s,]+/)) {
    if (!part) continue
    const fingerprint = normalizeFingerprint(part)
    if (fingerprint) seen.add(fingerprint)
  }
  return [...seen]
}

/** Builds the statement list, or null when no usable fingerprint is configured. */
export function buildAssetLinks(
  raw: string | undefined,
  packageName: string = ANDROID_PACKAGE_NAME,
): AssetLinksStatement[] | null {
  const fingerprints = parseFingerprints(raw)
  if (fingerprints.length === 0) return null
  return [
    {
      relation: ["delegate_permission/common.handle_all_urls"],
      target: {
        namespace: "android_app",
        package_name: packageName,
        sha256_cert_fingerprints: fingerprints,
      },
    },
  ]
}
