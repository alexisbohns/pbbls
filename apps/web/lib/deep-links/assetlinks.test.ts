import { describe, expect, it } from "vitest"
import {
  ANDROID_PACKAGE_NAME,
  buildAssetLinks,
  normalizeFingerprint,
  parseFingerprints,
} from "./assetlinks"

// A real Play App Signing fingerprint, shape-wise: 32 hex bytes, colon-separated.
const FINGERPRINT =
  "14:6D:E9:83:C5:73:06:50:D8:EE:B9:95:2F:34:FC:64:16:A0:83:42:E6:1D:BE:A8:8A:04:96:B2:3F:CF:44:E5"
const OTHER =
  "A1:B2:C3:D4:E5:F6:07:18:29:3A:4B:5C:6D:7E:8F:90:01:12:23:34:45:56:67:78:89:9A:AB:BC:CD:DE:EF:F0"

describe("normalizeFingerprint", () => {
  it("passes through the Play Console form unchanged", () => {
    expect(normalizeFingerprint(FINGERPRINT)).toBe(FINGERPRINT)
  })

  it("uppercases and re-inserts colons for keytool-style input", () => {
    expect(normalizeFingerprint(FINGERPRINT.toLowerCase())).toBe(FINGERPRINT)
    expect(normalizeFingerprint(FINGERPRINT.replace(/:/g, ""))).toBe(FINGERPRINT)
    expect(
      normalizeFingerprint(`  ${FINGERPRINT.replace(/:/g, "").toLowerCase()}  `),
    ).toBe(FINGERPRINT)
  })

  it("rejects anything that is not 32 hex bytes", () => {
    // The regression this whole module exists for: #662 shipped this literal.
    expect(normalizeFingerprint("REPLACE_WITH_PLAY_APP_SIGNING_SHA256")).toBeNull()
    expect(normalizeFingerprint("")).toBeNull()
    expect(normalizeFingerprint(FINGERPRINT.slice(0, -3))).toBeNull() // too short
    expect(normalizeFingerprint(`${FINGERPRINT}:00`)).toBeNull() // too long
    expect(normalizeFingerprint(FINGERPRINT.replace("14", "1G"))).toBeNull() // non-hex
  })
})

describe("parseFingerprints", () => {
  it("returns nothing for an unset or blank env var", () => {
    expect(parseFingerprints(undefined)).toEqual([])
    expect(parseFingerprints("")).toEqual([])
    expect(parseFingerprints("   ")).toEqual([])
  })

  it("splits on commas and whitespace", () => {
    expect(parseFingerprints(`${FINGERPRINT},${OTHER}`)).toEqual([FINGERPRINT, OTHER])
    expect(parseFingerprints(`${FINGERPRINT} ${OTHER}`)).toEqual([FINGERPRINT, OTHER])
    expect(parseFingerprints(`${FINGERPRINT},\n  ${OTHER}\n`)).toEqual([
      FINGERPRINT,
      OTHER,
    ])
  })

  it("deduplicates across formatting differences", () => {
    expect(
      parseFingerprints(`${FINGERPRINT}, ${FINGERPRINT.toLowerCase()}`),
    ).toEqual([FINGERPRINT])
  })

  it("drops invalid entries but keeps the valid ones", () => {
    expect(parseFingerprints(`${FINGERPRINT}, not-a-fingerprint`)).toEqual([
      FINGERPRINT,
    ])
    expect(parseFingerprints("REPLACE_WITH_PLAY_APP_SIGNING_SHA256")).toEqual([])
  })
})

describe("buildAssetLinks", () => {
  it("emits the statement list Google's verifier expects", () => {
    expect(buildAssetLinks(FINGERPRINT)).toEqual([
      {
        relation: ["delegate_permission/common.handle_all_urls"],
        target: {
          namespace: "android_app",
          package_name: ANDROID_PACKAGE_NAME,
          sha256_cert_fingerprints: [FINGERPRINT],
        },
      },
    ])
  })

  it("declares every configured key in one statement", () => {
    const statements = buildAssetLinks(`${FINGERPRINT},${OTHER}`)
    expect(statements).toHaveLength(1)
    expect(statements?.[0].target.sha256_cert_fingerprints).toEqual([
      FINGERPRINT,
      OTHER,
    ])
  })

  it("matches the package name in the Android manifest's App Links filter", () => {
    expect(ANDROID_PACKAGE_NAME).toBe("app.pbbls.android")
  })

  it("returns null instead of a never-verifiable file when unconfigured", () => {
    expect(buildAssetLinks(undefined)).toBeNull()
    expect(buildAssetLinks("")).toBeNull()
    expect(buildAssetLinks("REPLACE_WITH_PLAY_APP_SIGNING_SHA256")).toBeNull()
  })
})
