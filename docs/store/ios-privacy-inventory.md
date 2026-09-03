# iOS privacy inventory

The bridge between three things that must agree and have no automatic link:

1. **`PrivacyInfo.xcprivacy`** in each shippable bundle — Xcode aggregates these
   into the archive's Privacy Report.
2. **The App Store Connect privacy questionnaire** — filled in by hand, per release.
3. **The published privacy policy** (`apps/web/docs/privacy/{en,fr}.md`) — the
   promise users actually read.

`Scripts/verify-privacy-manifests.mjs` (CI, `ios.yml`) enforces 1 mechanically.
Nothing enforces 2 or 3, which is what this file is for.

Verified at `main` on 2026-09-04.

## 1. Declared collected data types

Every row is `Linked = true` (tied to the account) and `Tracking = false`
(nothing is shared with data brokers or used for cross-app advertising), with
purpose `AppFunctionality`. The app has no advertising SDK, no IDFA, and no
AppTrackingTransparency prompt.

| `NSPrivacyCollectedDataType…` | What produces it | ASC form answer | Policy section |
|---|---|---|---|
| `…EmailAddress` | Account creation and sign-in (Supabase Auth, `auth.users`) | Contact Info → Email Address | §2.1 Account Data |
| `…Name` | Profile full name and avatar; soul names the user types | Contact Info → Name | §2.1 Account Data, §2.1 Souls |
| `…UserID` | `profiles.id` and the Supabase session subject | Identifiers → User ID | §2.3 Session Tokens |
| `…PhotosorVideos` | Snaps attached to a pebble (`ImagePipeline` → Supabase Storage); profile avatar | User Content → Photos or Videos | §2.1 Account Data |
| `…OtherUserContent` | Free-text CBT responses (situation, thoughts, reactions, learnings), whispers | User Content → Other User Content | §2.1 Events |
| `…Health` | Moods: 7-point rating, up to 38 emotion labels, up to 18 life-domain associations, intensity and impact | Health & Fitness → Health | §2.1 Moods and Decisions, §3.2, §4 |
| `…ProductInteraction` | Bounce karma, achievements and progress, cairns | Usage Data → Product Interaction | §2.2 |

### Why `Health`

The published policy qualifies moods and emotion labels as **GDPR Art. 9 health
data** (`privacy/en.md:94`, §4.1: "data concerning mental health and psychological
well-being"). Apple's `Health` type covers "any other user provided health or
medical data" — it is **not** gated on using HealthKit, and the app currently
uses none (no `com.apple.developer.healthkit` entitlement; no HealthKit symbol
in any source). Declaring anything narrower would contradict a document the
project publishes.

Consequences, accepted deliberately:

- **App Store Review Guideline 5.1.3** applies: health data must not be used for
  advertising or data mining, must not be shared with third parties without
  consent, and must not be stored in iCloud. Pebbles stores it in Supabase, so
  nothing in the current architecture conflicts.
- The nutrition label will show **Health & Fitness → Health**, linked to identity.

The domain taxonomy in `domains.ts` mirrors `HKStateOfMind.Association` and
`credits/en.md:57` already claims `HKStateOfMind` compatibility, so if the
`StateOfMind` write ships later, this declaration already covers it — only the
entitlement and the two `NSHealth*UsageDescription` keys would be added.

## 2. Deliberately not declared

| Type | Evidence it is not collected |
|---|---|
| `…PreciseLocation` / `…CoarseLocation` | No `CoreLocation` / `CLLocation` symbol anywhere. `ImagePipeline.encodeJPEG` (`ImagePipeline.swift:96-100`) passes only the lossy-compression key, so no EXIF/GPS/TIFF dictionary is written into an upload. |
| `…Contacts` | No `CNContact` / Contacts framework use. Souls are names the user types; the address book is never read. |
| `…PhysicalAddress`, `…PhoneNumber` | No field collects either. |
| `…PaymentInfo`, `…CreditInfo` | No in-app purchase or payment SDK. |
| `…AdvertisingData`, `…DeviceID` | No `AdSupport`, no IDFA, no AppTrackingTransparency. `NSPrivacyTracking` is `false` with an empty `NSPrivacyTrackingDomains`. |
| `…Crash…`, `…Performance…`, `…OtherDiagnosticData` | No crash reporter or analytics SDK is linked. |
| `…SearchHistory`, `…BrowsingHistory` | Not collected. |

## 3. Required-reason APIs

Only one category applies. `verify-privacy-manifests.mjs` greps the target's own
Swift sources for each category's trigger symbols (comments and string literals
stripped) and fails if the declarations and the code disagree **in either
direction**.

| Category | Trigger symbols | Verdict |
|---|---|---|
| `…UserDefaults` | `@AppStorage`, `UserDefaults` | **Declared, `CA92.1`.** One real use: `@AppStorage("hasSeenOnboarding")` at `RootView.swift:25`. |
| `…FileTimestamp` | `creationDate`, `modificationDate`, `attributesOfItem`, `NSURLContentModificationDateKey`, `stat(`, `fstat` | Not used. The only `creationDate` hit is a doc comment at `ExifCaptureDate.swift:10`. |
| `…SystemBootTime` | `systemUptime`, `mach_absolute_time` | Not used. |
| `…DiskSpace` | `volumeAvailableCapacity`, `systemFreeSize` | Not used. |
| `…ActiveKeyboards` | `activeInputModes` | Not used. |

## 4. Third-party SDKs

Manifest counts read from the resolved SPM checkouts at the pinned versions. An
SDK's own manifest covers **its** symbols, never the app's — `@AppStorage` in
Pebbles' code is Pebbles' declaration to make.

| Package | Version | Ships `.xcprivacy` | Notes |
|---|---|---|---|
| `supabase-swift` | 2.43.1 | **No** | Sessions persist in Keychain, which is not a required-reason API. |
| `SVGView` | 1.0.6 | **No** | Pure rendering; no storage, no network. |
| `rive-ios` | 6.19.2 | Yes (1) | Animation runtime. |
| `swift-crypto` | 4.3.1 | Yes (7) | Transitive via supabase-swift. |
| `swift-asn1` | 1.6.0 | No | Transitive. |
| `swift-http-types` | 1.5.1 | No | Transitive. |
| `swift-clocks` | 1.0.6 | No | Transitive. |
| `swift-concurrency-extras` | 1.3.2 | No | Transitive. |
| `xctest-dynamic-overlay` | 1.9.0 | No | Test-support only. |

None is on Apple's signature-required SDK list at these versions. Re-check when
adding a dependency: the list is versioned and grows.

## 5. Bundles

| Bundle | Manifest | Content |
|---|---|---|
| `Pebbles` (app) | `Pebbles/Resources/PrivacyInfo.xcprivacy` | The tables above. |
| `PebblesWidget` (app extension) | `PebblesWidget/PrivacyInfo.xcprivacy` | Present and **deliberately empty**. The karma Live Activity renders state handed to it by ActivityKit; it has no network, no storage, and reads no required-reason API. An empty declaration says that; an absent file says nothing. |

Neither lives under `Shared/`, which compiles into both targets — asserted by
the verification script.

## 6. Standing update rule

A new SPM dependency, a new storage bucket, a new table holding a new *class* of
personal data, or a new required-reason API call updates **this file and the
manifests in the same change**. The script catches the required-reason half; the
collected-data half and the ASC form have no automatic gate, which is why the
rule is written down here.

## 7. What none of this proves

The script checks the source tree, not the built product. The authoritative
checks are the archive's **Privacy Report** (Xcode → Organizer → Generate Privacy
Report) and the App Store Connect upload response. Run both before the first
submission — an ITMS-91053 rejection is deterministic and cheap to trigger, but
only on a real upload.
