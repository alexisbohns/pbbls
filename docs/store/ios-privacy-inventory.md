# iOS privacy inventory

The bridge between the things that must agree:

1. **`PrivacyInfo.xcprivacy`** in each shippable bundle — Xcode aggregates these
   into the archive's Privacy Report. It is the source for 2.
2. **The App Store Connect privacy labels** (App Privacy) — copied from 1 by hand;
   Apple has no import.
3. **The Play Data safety form** (`docs/compliance/play-data-safety.csv`) — the
   Android side of the same answers.
4. **The published privacy policy** (`apps/web/docs/privacy/{en,fr}.md`) and the
   plain-words page `/docs/data-safety`.

`Scripts/verify-privacy-manifests.mjs` (CI, `ios.yml`) checks the required-reason
half of 1 against the Swift sources. `apps/web/lib/docs/data-safety.test.ts` checks
the collected-data half of 1 against 3: the same types and the same purposes,
linked and never tracking. Nothing checks 2 (copied by hand) or the policy's
wording, which is what this file and CLAUDE.md's "Store privacy declarations" are
for.

Verified at `main` on 2026-11-07. The App Store labels were published from the
manifest the same day.

## 1. Declared collected data types

Every row is `Linked = true` (tied to the account) and `Tracking = false`
(nothing is shared with data brokers or used for cross-app advertising). The app
has no advertising SDK, no IDFA, and no AppTrackingTransparency prompt.

Analytics means the statistics counted from our own database for the admin
(daily users, retention, pebble volume, emotion and domain shares, onboarding
answers), seen only as totals, with no analytics SDK (policy §3.3, §11.3). Counts
over moods and emotions rest on the Article 9 consent (§4.2).

| `NSPrivacyCollectedDataType…` | What produces it | ASC form answer | Purposes | Policy section |
|---|---|---|---|---|
| `…EmailAddress` | Account creation and sign-in (Supabase Auth, `auth.users`), including Google and Apple sign-in | Contact Info → Email Address | App Functionality | §2.1 Account Data, §2.4 |
| `…Name` | Profile full name and avatar; soul names the user types | Contact Info → Name | App Functionality | §2.1 Account Data, §2.1 Souls |
| `…UserID` | `profiles.id` and the Supabase session subject | Identifiers → User ID | App Functionality, Analytics | §2.3 Session Tokens, §3.3 |
| `…PhotosorVideos` | Snaps attached to a pebble (`ImagePipeline` → Supabase Storage); profile avatar | User Content → Photos or Videos | App Functionality | §2.1 Account Data, §2.1 Photos |
| `…OtherUserContent` | What the user writes and makes: moments and their names, souls, collections, glyphs, Lab reactions | User Content → Other User Content | App Functionality, Analytics | §2.1 Events |
| `…Health` | Moods: emotions, intensity, positiveness and life domains | Health & Fitness → Health | App Functionality, Analytics | §2.1 Moods, §3.2, §4 |
| `…ProductInteraction` | Bounce karma, achievements and progress, when the app is used | Usage Data → Product Interaction | App Functionality, Analytics | §2.2 |
| `…OtherDataTypes` | Onboarding answers: why the person came and how often they would like to record (`complete_onboarding`); they choose the first prompt | Other Data → Other Data Types | App Functionality, Analytics, Product Personalization | §2.1 Onboarding Answers |

### Why `Health`

The published policy qualifies moods and emotion labels as **GDPR Art. 9 health
data** (`privacy/en.md` §4.1: "data concerning mental health and psychological
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

If a HealthKit `StateOfMind` write ships later, this declaration already covers
it: only the entitlement and the two `NSHealth*UsageDescription` keys would be
added (and Play's Health apps declaration would change).

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
| `…UserDefaults` | `@AppStorage`, `UserDefaults` | **Declared, `CA92.1`.** Every use reads and writes the app's own standard defaults, with no app group and nothing sent off device: the `hasSeenOnboarding` flag through `UserDefaults` in `OnboardingGate.swift`, the consent gate's cached pass in `ConsentPreferences.swift`, and small on-device preferences, most of them through `@AppStorage`. |
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
personal data, a new purpose, or a new required-reason API call updates **this
file and the manifests in the same change**, together with the Play CSV, the
`/docs/data-safety` page and the privacy policy (CLAUDE.md, "Store privacy
declarations"). The script catches the required-reason half and
`data-safety.test.ts` catches a manifest that drifts from the Play form; the App
Store Connect labels have no automatic gate, so they are updated by hand from the
manifest before the next App Store submission.

## 7. What none of this proves

The script checks the source tree, not the built product. The authoritative
checks are the archive's **Privacy Report** (Xcode → Organizer → Generate Privacy
Report) and the App Store Connect upload response. Run both before the first
submission — an ITMS-91053 rejection is deterministic and cheap to trigger, but
only on a real upload.
