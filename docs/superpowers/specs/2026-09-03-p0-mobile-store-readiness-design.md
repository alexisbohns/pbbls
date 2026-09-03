# P0 mobile store readiness — design (M57)

Design doc for the two-part stack that closes the three P0 findings the
2026-08 Kritik audit left open on the mobile surfaces:

| Finding | Criterion | Surface | Part |
|---|---|---|---|
| `F-2026-08-GDP-ios-02` | GDP-05 | ios | 1 |
| `F-2026-08-PLT-ios-01` | PLT-02 | ios | 1 |
| `F-2026-08-A11Y-android-01` | A11Y-05 | android (+ ios mirror) | 2 |

The first two are the same defect seen through two criteria — no
`PrivacyInfo.xcprivacy` in either shippable iOS bundle — and are resolved
together by Part 1. The third is a contrast failure on the Google sign-in
label that the 1:1 mirror rule propagated to both native apps.

Milestone **M57 · Store readiness**. The roadmap
(`2026-07-28-store-launch-roadmap.md:25,148`) already lists the privacy
manifest as a v1.0 gate; the contrast bug is new information the roadmap does
not carry.

**No schema, RPC or payload changes.** Nothing crosses a surface boundary, so
`packages/supabase` and `apps/admin` are untouched. Web is untouched too: its
Google button is a shadcn `Button variant="outline"`
(`apps/web/app/login/page.tsx:195-216`), themed surface *and* themed
foreground, so it has neither defect.

---

## 1. Re-verification at HEAD (2026-09-03, `main` @ `26be933c`)

The audit ran against a snapshot. Every cited `file:line` was re-read. Verdict
per finding, then the drift.

### 1.1 `F-2026-08-GDP-ios-02` + `F-2026-08-PLT-ios-01` — **still valid, unchanged**

| Claim | Check | Result |
|---|---|---|
| No manifest anywhere under `apps/ios` | `find . -iname '*xcprivacy*'` | zero hits, repo-wide |
| Nothing references one | `grep -rIl 'xcprivacy\|NSPrivacyAccessed\|PrivacyInfo'` | one hit, and it is the roadmap prose |
| `@AppStorage` at `RootView.swift:25` | read | exact: `@AppStorage("hasSeenOnboarding") private var hasSeenOnboarding = false` |
| It is the app's only `UserDefaults` key | `grep -rn 'AppStorage\|UserDefaults'` | three hits: `RootView.swift:25` (the key), `OnboardingView.swift:9` and `ComposerSnapshotStore.swift:9` (both comments) |
| `project.yml` declares no manifest resource | read all 129 lines | confirmed |
| The widget is a second shippable bundle | `PebblesWidget` is `type: app-extension`, embedded by the app (`project.yml:68-69`) | confirmed |
| An upload pipeline exists | `ci_scripts/ci_post_clone.sh` (Xcode Cloud, ASC secrets), `schemes.Pebbles.archive.config: Release` (`project.yml:128-129`) | confirmed |

Nothing has drifted. The finding is reproducible verbatim.

### 1.2 `F-2026-08-A11Y-android-01` — **still valid; one remediation step is already done and demonstrably does not gate**

| Citation | Result |
|---|---|
| `GoogleSignInButton.kt:49` `.background(Color.White)` | exact |
| `GoogleSignInButton.kt:64` `color = system.foreground` | exact |
| `Palettes.kt:41` `SystemPaletteDark.foreground = Color(0xFFE9E2E4)` | exact |
| `WelcomeScreen.kt:185` call site | exact |
| `AuthScreen.kt:228` call site | exact |
| `apps/ios/Pebbles/Components/Buttons/GoogleSignInButton.swift`, same pattern | exact — the path is **correct** at HEAD; the audit simply gave no line numbers. They are **`:20`** (`.foregroundStyle(Color.system.foreground)`) and **`:24`** (`.background(Capsule().fill(Color.white))`) |
| `SystemForeground.colorset` dark appearance is the same value | `Assets.xcassets/SystemForeground.colorset/Contents.json` — dark = `r 0x4A→0xE9, g 0x36→0xE2, b 0x39→0xE4`. Confirmed |
| `PebblesTheme` resolves the palette from `isSystemInDarkTheme()` alone | `PebblesTheme.kt:56-57`. No night-mode pin, no forced-light wrapper between it and either call site. Confirmed |

**Contrast, recomputed independently** (WCAG 2.x relative luminance, sRGB):

| Pair | Ratio | AA text (4.5) | AA non-text (3.0) |
|---|---|---|---|
| `#E9E2E4` on `#FFFFFF` — the bug | **1.276 : 1** | fail | fail |
| `#4A3639` on `#FFFFFF` — the proposed ink | **11.183 : 1** | pass (AAA) | pass |
| `#AF979D` on `#FFFFFF` — see §1.4 | 2.714 : 1 | fail | fail |
| `#E9E2E4` on `#171012` — dark ink on its *intended* ground | 14.709 : 1 | pass | pass |

The last row is what makes this a pure surface/ink pairing bug rather than a
palette bug: `system.foreground` is correct everywhere it sits on
`system.background`. It is wrong only where the ground is pinned.

### 1.3 Drift — what the audit says that HEAD contradicts

1. **"add a dark-mode screenshot preview of the auth screen" is already done.**
   `apps/android/app/src/screenshotTest/.../FunnelScreenshots.kt` has
   `WelcomeScreenDark` (`:36-48`) and `AuthScreenSignup` (`:63-75`), both
   `@PreviewTest @Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)`,
   both wrapped in `PebblesTheme`. Under `UI_MODE_NIGHT_YES`,
   `isSystemInDarkTheme()` returns true and `PebblesTheme` provides
   `SystemPaletteDark` — so these previews have been rendering the illegible
   button in every CI run since the funnel landed.
   **Correction to the verifier's note:** the masking is *not* the
   `staticCompositionLocalOf { SystemPaletteLight }` default (these previews
   are wrapped, so the default never applies). The masking is entirely that
   `android.yml:71-73` runs `updateDebugScreenshotTest` and
   `apps/android/.gitignore:19` ignores `app/src/screenshotTest*/reference/` —
   render-to-view, no baseline, nothing to fail. This makes the case for a
   real assertion *stronger*, not weaker: the picture of the bug has been
   uploaded as a CI artifact for weeks and changed nothing.
2. **`PebblesWidget` is not "unused".** The roadmap (`:148`) proposes
   "stripping the unused `PebblesWidget` target from the submitted build". It
   is not unused: it is the `+N karma` Live Activity
   (`PebblesWidget/KarmaActivityWidget.swift`, driven by
   `Pebbles/Features/Karma/KarmaLiveActivityController.swift`, with
   `NSSupportsLiveActivities: true` in `Pebbles/Resources/Info.plist`).
   Stripping it would delete a shipped feature. That roadmap line should be
   struck, and the widget gets a manifest of its own (§3.2).
3. **Milestone erratum confirmed.** The roadmap schedules the manifest under
   **M57**, not M56 (`2026-07-28-store-launch-roadmap.md:47-48,146-149`).
4. **supabase-swift erratum confirmed and narrowed.** Session persistence on
   Apple platforms is Keychain, which is not a required-reason API. The pinned
   versions are supabase-swift `2.43.1`, SVGView `1.0.6`, rive-ios `6.19.2`
   (`Pebbles.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved`).
   None of the three is on Apple's "commonly used third-party SDK" list, so
   none is required to ship a *signed* manifest — but they are linked into the
   app binary, so any required-reason API they call is reported against
   **our** bundle. See §3.5 and Risk R1.
5. **No drift in any other citation.** `project.yml` line ranges shifted
   only in the trivial sense that the audit quoted `38-69` (GDP) and `40-95`
   (PLT) as spans; at HEAD the `Pebbles` target is `39-69` and `PebblesWidget`
   is `71-89`. Both spans still contain what the findings say they contain.

### 1.4 What the grep for other fixed-surface components turned up

The remediation asks token-or-constant, and the honest answer depends on
whether this is one site or a class. It is a class. `Color.White` /
`Color.white` used as a **fixed ground under themed ink**:

| Site | Ground | Ink | Dark-theme ratio |
|---|---|---|---|
| `components/GoogleSignInButton.kt:49,64` | `Color.White` | `system.foreground` | **1.28 : 1** |
| `Components/Buttons/GoogleSignInButton.swift:24,20` | `Color.white` | `Color.system.foreground` | **1.28 : 1** |
| `components/PebblesCheckbox.kt:81,86` | `Color.White` (unchecked box) | `system.secondary` glyph | **2.71 : 1** |
| `Components/Checkboxes/PebblesCheckbox.swift:38,44` | `Color.white` (unchecked box) | `Color.system.secondary` glyph | **2.71 : 1** |
| `Components/Inputs/PebblesTextInput.swift:42,25,32` | `Color.white` | `Color.system.secondary` text | **2.71 : 1** |

The last row is also a **1:1 mirror divergence**: Android's
`PebblesTextInput.kt:64` already uses `system.background` and carries the
comment *"never a hardcoded white, which is unreadable in dark mode"*. iOS
never got that fix.

Every other `Color.white` / `Color.White` hit is benign — white ink on the
accent fill (`PebblesPrimaryButton`, `SlideToConfirm`, `PebblesAuthSwitcher`),
a white ring/stroke on a pebble (`PathPebbleRow`), a white QR ground with
`Color.Black` modules (`InviteScreen`/`InviteSheet`), or a white drawing
canvas whose ink is the user's chosen stroke colour (`GlyphCanvasView`,
`GlyphCarveScreen`).

So: **five sites, two surfaces, one root cause.** That decides §4.1.

**Scope discipline.** The finding is the Google button. Per root `CLAUDE.md`
("never refactor existing code without explicit approval"), Part 2 fixes the
button on both surfaces and introduces the token; the checkbox and the iOS
text input are written up in §6 as a proposed follow-up issue, with the token
already in place so each becomes a one-line change.

---

## 2. Part breakdown

```
main
 └── PART 1  chore/<issue>-ios-privacy-manifest    → GDP-ios-02 + PLT-ios-01
      └── PART 2  fix/<issue>-google-btn-contrast  → A11Y-android-01 (ios + android)
```

### The dependency, stated honestly

**Part 2 imports nothing from Part 1.** Different languages, different files,
different findings. If Part 1 were reverted, Part 2 would still compile, lint
and pass its tests. This is a stack because the two parts tell one story —
*"the mobile apps are fit to be submitted"* — not because one needs the other.

There is exactly **one** file-level coupling, and it is deliberate: Part 1
creates `.github/workflows/ios.yml` (there is no iOS workflow today), and
Part 2 adds one step to it. That is an additive edit to a file that exists by
then; it is not a code dependency, and it points the same way as the ordering
argument below.

### Why Part 1 goes first

- **Part 1 is a hard submission blocker; Part 2 is not.** With an Xcode Cloud
  post-clone script wired to App Store Connect secrets and a Release archive
  scheme, ITMS-91053 is deterministic on the first archive upload — TestFlight
  included. No manifest means no build reaches a tester. Part 2 makes a label
  legible in a build that, today, cannot be uploaded.
- **The A11Y defect degrades rather than blocks.** The white capsule and the
  multi-colour G mark stay visible in dark mode, so the affordance is still
  discoverable by shape, and email/password sign-in is entirely unaffected.
  That is the impact-3 mitigation the audit already priced in.
- **Part 1 lays the CI ground Part 2 stands on.** `ios.yml` does not exist;
  Part 1 has to create it to satisfy its own remediation ("add a CI presence
  check"). Part 2 then extends it for free.

### The argument for flipping, and why it does not win

Android **auto-publishes to Play internal testing on every push to `main`**
(`android-release.yml`, `apps/android/CLAUDE.md` § Release). So the contrast
bug is live for internal testers right now, while the missing manifest blocks
a submission that has not been attempted. That is a real argument to land
Part 2 first.

It loses on time constants. A stack merges bottom-up in hours; the internal
testers wait one merge longer either way. Part 1 carries the external, dated
dependency (App Review), the larger design surface (manifest content,
inventory doc, new workflow) and the higher chance of a review round-trip, so
it should start its clock first.

**If the maintainer wants the Android fix in tonight's internal build**, the
right move is not to reorder the stack — it is to cut Part 2 as its own
single-PR stack off `main` and rebase Part 1 behind it. The two parts are
genuinely independent, so that costs nothing but a rebase. Flag this as a call
for the human (§7 Q5).

---

## 3. Part 1 design — `PrivacyInfo.xcprivacy` for both iOS targets

### D1 — Two manifests, one per shippable bundle, neither in `Shared/`

Apple's static analysis reports per-bundle. The app and the widget are two
bundles (`app.pbbls.ios`, `app.pbbls.ios.widget`), so each needs its own file.

`Shared/` is listed in the `sources` of **both** targets
(`project.yml:44-45, 76-77`). A manifest placed there would be copied into
both bundles and would declare the app's `UserDefaults` usage against the
widget too — a false declaration, and exactly the kind of thing that ages into
a wrong nutrition label. The two files are deliberately unshared and
deliberately different.

| Bundle | Path |
|---|---|
| `Pebbles` (app) | `apps/ios/Pebbles/Resources/PrivacyInfo.xcprivacy` |
| `PebblesWidget` (app-extension) | `apps/ios/PebblesWidget/PrivacyInfo.xcprivacy` |

### D2 — What the app actually collects, derived from the code

The nutrition-label answers are derived from the data layer and the storage
buckets, then checked against the published policy
(`apps/web/docs/privacy/en.md`, v1.0.0, 2026-04-09). Facts established by
reading the code:

- **Auth is email + password, Sign in with Apple, and Google OAuth.**
  `Pebbles.entitlements` declares `com.apple.developer.applesignin`; the
  Google path is `GoogleSignInButton` → `signInWithOAuth`.
- **Photos are stripped before upload.**
  `Features/PebbleMedia/ImagePipeline.swift:95-97` re-encodes with only
  `kCGImageDestinationLossyCompressionQuality`, and the comment states the
  intent: *"Passing only the lossy-compression key ensures NO EXIF / GPS /
  TIFF dictionaries are written into the output."* So **no precise or coarse
  location is collected**, and the label must not claim otherwise.
- **EXIF is read, not uploaded.** `ExifCaptureDate.swift` reads
  `kCGImagePropertyExifDateTimeOriginal` from the picked bytes purely to seed
  the pebble's `happened_at`. The stored artefact is a timestamp the user can
  edit, i.e. user content, not device metadata.
- **No analytics, no crash SDK, no ad SDK, no push.** `grep` for
  `UNUserNotification|registerForRemoteNotifications` returns nothing; there
  is no analytics dependency in `Package.resolved`. This matches policy §13.3
  ("No Analytics, No Advertising") exactly.
- **Souls are typed names, not address-book reads.** No `Contacts` framework
  import anywhere.
- **Karma / achievements / assiduity are stored server-side**, and policy §2.2
  already declares "Bounce Karma: a metric indicating your regularity of
  engagement with the app". Omitting it would be the contradiction; declaring
  it as `ProductInteraction` (linked, non-tracking, app-functionality) is the
  consistent answer.

The resulting seven types, all `Linked = true`, `Tracking = false`, purpose
`AppFunctionality` only:

| `NSPrivacyCollectedDataType` | What it is in Pebbles | Policy § |
|---|---|---|
| `…EmailAddress` | Supabase Auth account identifier | 2.1 |
| `…Name` | `profiles.display_name`, and **soul names** (§7 Q2) | 2.1, 2.5 |
| `…UserID` | `auth.users.id`, `profiles.handle` | 2.1, 2.3 |
| `…PhotosorVideos` | snaps in the `pebbles-media` bucket | 2.1 |
| `…OtherUserContent` | pebble names, collections, carved glyph strokes | 2.1 |
| `…Health` | emotion, intensity, positiveness, valence (§7 Q1) | 3.2, 4.1 |
| `…ProductInteraction` | karma events, achievements, assiduity | 2.2 |

Deliberately **absent**, each for a reason worth writing down: no
`PreciseLocation`/`CoarseLocation` (EXIF is stripped at
`ImagePipeline.swift:97`), no `Contacts` (§7 Q2), no `PaymentInfo` /
`PurchaseHistory` (karma is a closed earned-only economy, no IAP — the same
assertion the roadmap wants in the App Review notes), no
`CrashData`/`PerformanceData`/`OtherDiagnosticData` (§7 Q3), no
`AdvertisingData`, no `DeviceID`, no `SensitiveInfo` (Apple's definition
enumerates race, orientation, religion, politics, genetics, biometrics —
emotional state is not among them; `Health` is the better fit).

### D3 — App manifest, proposed content

`apps/ios/Pebbles/Resources/PrivacyInfo.xcprivacy`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>NSPrivacyTracking</key>
    <false/>
    <key>NSPrivacyTrackingDomains</key>
    <array/>

    <key>NSPrivacyAccessedAPITypes</key>
    <array>
        <dict>
            <!-- @AppStorage("hasSeenOnboarding") — RootView.swift:25.
                 CA92.1 = access info from the same app, no data sent off device. -->
            <key>NSPrivacyAccessedAPIType</key>
            <string>NSPrivacyAccessedAPICategoryUserDefaults</string>
            <key>NSPrivacyAccessedAPITypeReasons</key>
            <array>
                <string>CA92.1</string>
            </array>
        </dict>
    </array>

    <key>NSPrivacyCollectedDataTypes</key>
    <array>
        <dict>
            <key>NSPrivacyCollectedDataType</key>
            <string>NSPrivacyCollectedDataTypeEmailAddress</string>
            <key>NSPrivacyCollectedDataTypeLinked</key><true/>
            <key>NSPrivacyCollectedDataTypeTracking</key><false/>
            <key>NSPrivacyCollectedDataTypePurposes</key>
            <array><string>NSPrivacyCollectedDataTypePurposeAppFunctionality</string></array>
        </dict>
        <dict>
            <key>NSPrivacyCollectedDataType</key>
            <string>NSPrivacyCollectedDataTypeName</string>
            <key>NSPrivacyCollectedDataTypeLinked</key><true/>
            <key>NSPrivacyCollectedDataTypeTracking</key><false/>
            <key>NSPrivacyCollectedDataTypePurposes</key>
            <array><string>NSPrivacyCollectedDataTypePurposeAppFunctionality</string></array>
        </dict>
        <dict>
            <key>NSPrivacyCollectedDataType</key>
            <string>NSPrivacyCollectedDataTypeUserID</string>
            <key>NSPrivacyCollectedDataTypeLinked</key><true/>
            <key>NSPrivacyCollectedDataTypeTracking</key><false/>
            <key>NSPrivacyCollectedDataTypePurposes</key>
            <array><string>NSPrivacyCollectedDataTypePurposeAppFunctionality</string></array>
        </dict>
        <dict>
            <key>NSPrivacyCollectedDataType</key>
            <string>NSPrivacyCollectedDataTypePhotosorVideos</string>
            <key>NSPrivacyCollectedDataTypeLinked</key><true/>
            <key>NSPrivacyCollectedDataTypeTracking</key><false/>
            <key>NSPrivacyCollectedDataTypePurposes</key>
            <array><string>NSPrivacyCollectedDataTypePurposeAppFunctionality</string></array>
        </dict>
        <dict>
            <key>NSPrivacyCollectedDataType</key>
            <string>NSPrivacyCollectedDataTypeOtherUserContent</string>
            <key>NSPrivacyCollectedDataTypeLinked</key><true/>
            <key>NSPrivacyCollectedDataTypeTracking</key><false/>
            <key>NSPrivacyCollectedDataTypePurposes</key>
            <array><string>NSPrivacyCollectedDataTypePurposeAppFunctionality</string></array>
        </dict>
        <dict>
            <key>NSPrivacyCollectedDataType</key>
            <string>NSPrivacyCollectedDataTypeHealth</string>
            <key>NSPrivacyCollectedDataTypeLinked</key><true/>
            <key>NSPrivacyCollectedDataTypeTracking</key><false/>
            <key>NSPrivacyCollectedDataTypePurposes</key>
            <array><string>NSPrivacyCollectedDataTypePurposeAppFunctionality</string></array>
        </dict>
        <dict>
            <key>NSPrivacyCollectedDataType</key>
            <string>NSPrivacyCollectedDataTypeProductInteraction</string>
            <key>NSPrivacyCollectedDataTypeLinked</key><true/>
            <key>NSPrivacyCollectedDataTypeTracking</key><false/>
            <key>NSPrivacyCollectedDataTypePurposes</key>
            <array><string>NSPrivacyCollectedDataTypePurposeAppFunctionality</string></array>
        </dict>
    </array>
</dict>
</plist>
```

### D4 — Widget manifest: present, and deliberately empty

`apps/ios/PebblesWidget/PrivacyInfo.xcprivacy`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <!-- The Live Activity renders KarmaActivityAttributes.ContentState handed to
         it by ActivityKit (Shared/KarmaActivityAttributes.swift — "No App Group
         is needed"). It has no network, no storage, no UserDefaults, and reads
         no required-reason API. Present-and-empty is the declaration that says
         so; an absent file says nothing at all. -->
    <key>NSPrivacyTracking</key>
    <false/>
    <key>NSPrivacyTrackingDomains</key>
    <array/>
    <key>NSPrivacyAccessedAPITypes</key>
    <array/>
    <key>NSPrivacyCollectedDataTypes</key>
    <array/>
</dict>
</plist>
```

### D5 — `project.yml`: explicit, though inference already works

Verified empirically against XcodeGen 2.46.0 (the version on the maintainer's
machine) with a throwaway probe project: a `PrivacyInfo.xcprivacy` sitting
under a directory `sources` entry is auto-classified into the target's
**Resources** build phase. So `sources: - path: Pebbles` would already pick it
up.

Also verified: adding an **explicit** entry alongside the directory entry
produces **no duplication** — one `PBXFileReference`, one `PBXBuildFile`, in
`Resources`. XcodeGen de-duplicates.

So the entry is added explicitly, because "it works by extension inference" is
not something a future reader should have to rediscover, and because the
findings ask for `project.yml` to reference it:

```yaml
  Pebbles:
    …
    sources:
      - path: Pebbles
      - path: Shared
      # Privacy manifest — declared explicitly rather than left to XcodeGen's
      # extension inference. One per shippable bundle; never in Shared/, which
      # is compiled into both targets.
      - path: Pebbles/Resources/PrivacyInfo.xcprivacy
        buildPhase: resources

  PebblesWidget:
    …
    sources:
      - path: PebblesWidget
      - path: Shared
      - path: PebblesWidget/PrivacyInfo.xcprivacy
        buildPhase: resources
```

### D6 — `docs/store/ios-privacy-inventory.md`

`docs/store/` does not exist yet; Part 1 creates it with one file. It is the
bridge between the manifest (which Xcode aggregates into the archive's Privacy
Report) and the App Store Connect form (which a human fills in by hand), and
it is where the *reasoning* lives so the next person does not re-derive it.

Contents:

1. **Data-type table** — one row per declared type: the `NSPrivacy…` constant,
   the Pebbles feature and table/bucket that produces it, linked/tracking/
   purpose, the ASC form answer, and the privacy-policy section it must stay
   consistent with.
2. **Not-declared table** — one row per type deliberately *absent*, with the
   evidence (e.g. "PreciseLocation — not collected;
   `ImagePipeline.swift:97` strips EXIF/GPS on every upload").
3. **SDK table** — `supabase-swift 2.43.1`, `SVGView 1.0.6`,
   `rive-ios 6.19.2` (plus the transitive Apple/pointfree packages from
   `Package.resolved`): does it ship its own manifest, is it on Apple's
   signature-required list, which required-reason APIs it is known to touch,
   and who declares them.
4. **Required-reason API table** — the five categories, the symbols that
   trigger each, and the current verdict (only `UserDefaults` applies).
5. **Standing update rule** — a new SPM dependency, a new storage bucket, a
   new table holding a new *class* of personal data, or a new required-reason
   API call updates this file **and** the manifests **in the same change**.

Per root `CLAUDE.md` § "Editing CLAUDE.md / AGENTS.md", that standing rule
does **not** get promoted into `apps/ios/CLAUDE.md` in this PR — learnings are
promoted during the milestone-boundary audit pass, not per-PR. It lives in the
inventory and in the decision-log entry.

### D7 — The CI presence check, and where it lives

There is **no iOS workflow today** (`.github/workflows/` holds
`android-release.yml`, `android.yml`, `arkaik.yml`, `lab-note-reminder.yml`,
`supabase.yml`). Part 1 therefore needs a **new workflow**:
`.github/workflows/ios.yml`, modelled on `android.yml`'s shape (path-filtered
on `pull_request` and `push: [main]`, `concurrency` group by ref,
`permissions: contents: read`).

It runs on **`ubuntu-latest`**, not macOS: the check is a Node script, needs
no Xcode, and finishes in seconds. Paying for a macOS runner to assert a file
exists would be the reason the check gets disabled later.

`apps/ios/Scripts/verify-privacy-manifests.mjs` (sibling of the existing
`valence-art-to-svg.mjs` / `generate-wobble-golden.mjs`), wired as
`npm run verify:privacy --workspace=@pbbls/ios` so CI and a human invoke the
identical command — the discipline `supabase.yml` established (#741).

A presence check that only checks presence rots. This one is coupled to the
source, so it fails when the code moves out from under it:

1. Both manifests exist at the exact paths of D1.
2. Each parses (minimal plist-subset reader in the script — no new npm
   dependency for a seven-key file) and is not empty XML.
3. Each carries `NSPrivacyTracking = false`, an **empty**
   `NSPrivacyTrackingDomains`, and both array keys present.
4. **Symbol → category coupling.** `grep` the target's own Swift sources for
   the trigger symbols of each of the five required-reason categories, and
   assert the declaration set matches exactly — in both directions. Adding
   `FileManager.attributesOfItem` without declaring `FileTimestamp` fails;
   deleting the last `@AppStorage` while leaving `CA92.1` declared also fails.

   | Category | Symbols grepped |
   |---|---|
   | `…UserDefaults` | `@AppStorage`, `UserDefaults` |
   | `…FileTimestamp` | `creationDate`, `modificationDate`, `attributesOfItem`, `NSURLContentModificationDateKey`, `stat(`, `fstat` |
   | `…SystemBootTime` | `systemUptime`, `mach_absolute_time` |
   | `…DiskSpace` | `volumeAvailableCapacity`, `systemFreeSize` |
   | `…ActiveKeyboards` | `activeInputModes` |

   Crude on purpose: a comment mentioning `creationDate` will trip it (there
   is one, at `ExifCaptureDate.swift:10`), and the script must therefore strip
   comments before matching, then fail loud with `file:line` so a human
   decides rather than the script guessing.
5. Neither manifest is under `Shared/` (D1's invariant, asserted).
6. `project.yml` contains the two explicit `- path: …PrivacyInfo.xcprivacy`
   entries, each under the right target.

What this check **cannot** prove is what ends up in the built bundle. That
proof is the archive's Privacy Report and the upload response — see the
manual step in §5 and Risk R1.

---

## 4. Part 2 design — the Google sign-in label

### 4.1 — Decision: a real token (`onLight`), not a constant

**Chosen: add a fifth field `onLight` to `SystemPalette` on both surfaces,
valued `#4A3639` (the light-theme foreground) in *both* the light and dark
palettes.**

Four reasons, in the order they actually decided it:

1. **It is a class, not a site.** §1.4 found five call sites across two
   surfaces where themed ink sits on a pinned light ground. A hex literal
   would be duplicated at five places today and copied to the sixth by
   whoever mirrors the next component. The named token is the thing that makes
   the *rule* ("this ground does not follow the theme, so its ink must not
   either") visible at the call site.
2. **Testability — the decisive, repo-specific reason.** Android's only
   automated gate is a JVM JUnit test: `apps/android/CLAUDE.md` mandates
   "JUnit4 + `kotlinx-coroutines-test`, JVM unit tests only. No Robolectric,
   no instrumented tests", and screenshot testing is render-to-view (§1.3).
   A hex literal inside a `@Composable` body is unreachable from a JVM test.
   A palette field is a plain value a JVM test reads directly — `PalettesTest`
   already does exactly this. **The token is what makes §4.3 possible at all.**
   The same holds on iOS: `Color.system.onLight` is assertable from a Swift
   Testing suite; a literal inside `GoogleSignInButton.body` is not.
3. **It extends the existing shape rather than inventing one.** iOS palettes
   are asset colorsets, and `AccentPrimary.colorset` is already a single-
   appearance (no dark variant) colorset — so `SystemOnLight.colorset` with
   one universal entry is a proven pattern, not a new one. Android transcribes
   the hexes and `PalettesTest` guards the transcription; adding one field
   extends that.
4. **Zero visual change in light mode.** The value is not a new colour — it is
   the existing light foreground promoted to a theme-independent role. In
   light mode every pixel is identical, which is precisely what makes the
   diff reviewable: any visual difference in the CI screenshots is the dark
   mode fix and nothing else.

**Naming: `onLight`, not `onWhite`.** The ground is "a fixed light surface"
— white today, and the same rule would hold if it became `#FAFAFA`.
`on<Surface>` is the idiom Compose readers already parse from Material's
`onPrimary`/`onSurface`, which `PebblesTheme.kt:59-96` already maps onto.

**Value: `0xFF4A3639` / sRGB `0x4A,0x36,0x39`** — 11.18:1 on white, AAA. No
new colour needs tuning, and no design-source round-trip is needed.

**What was rejected.** A bare constant in the button file: fails reason 2
outright, which alone settles it. Re-theming the capsule to
`system.background` (Android's `PebblesTextInput` answer): wrong here, because
the Google G mark is a fixed multi-colour asset that requires a light ground —
a dark capsule would need Google's dark-theme button variant, which is a brand
decision, not a bug fix.

### 4.2 — The exact change on each surface

Both surfaces hoist the pairing out of the view body, which is what §4.3
asserts against, and which is the root `CLAUDE.md` convention ("keep business
logic out of components").

**Android** — `theme/Palettes.kt`:

```kotlin
data class SystemPalette(
    val foreground: Color,
    val secondary: Color,
    val muted: Color,
    val background: Color,
    /**
     * Ink for content painted on a *pinned light* surface (the Google capsule),
     * where `foreground` would flip to #E9E2E4 and vanish at 1.28:1. Identical
     * in both palettes on purpose — it does not follow the theme, because its
     * ground does not either.
     */
    val onLight: Color,
)

internal val SystemPaletteLight = SystemPalette(…, onLight = Color(0xFF4A3639))
internal val SystemPaletteDark  = SystemPalette(…, onLight = Color(0xFF4A3639))
```

`components/GoogleSignInButton.kt`:

```kotlin
/** The capsule is a pinned light surface — the multi-colour G mark requires one. */
internal val GoogleButtonSurface = Color.White

/** Ink for [GoogleButtonSurface]. Never `system.foreground`: see SystemPalette.onLight. */
internal fun googleButtonLabelColor(system: SystemPalette): Color = system.onLight
```

…with `:49` becoming `.background(GoogleButtonSurface)` and `:64`
`color = googleButtonLabelColor(system)`.

**iOS** — new `Assets.xcassets/SystemOnLight.colorset/Contents.json`, a single
`universal` sRGB entry `red 0x4A / green 0x36 / blue 0x39 / alpha 1.000`, **no
dark appearance**; `Theme/Palettes.swift` gains `let onLight: Color` on
`SystemPalette` and `onLight: Color("SystemOnLight")` in the
`Color.system` initializer;
`Components/Buttons/GoogleSignInButton.swift` gains

```swift
extension GoogleSignInButton {
    /// The capsule is a pinned light surface — the multi-colour G mark requires one.
    static let surface = Color.white
    /// Ink for `surface`. Never `system.foreground`: see `SystemPalette.onLight`.
    static let labelColor = Color.system.onLight
}
```

…with `:20` becoming `.foregroundStyle(Self.labelColor)` and `:24`
`.background(Capsule().fill(Self.surface))`.

Both token galleries gain the swatch, so the token is visible where the others
are: `Theme/ColorTokensPreview.swift:32-36` and
`DebugTokenPreviewScreen.kt:64-67`.

No new localized strings. No Arkaik change (`AC-google-sign-in` and its three
edges already exist and are unmoved). No schema change.

### 4.3 — A test that fails today

**A dark-mode preview is not a regression test here, and the repo already
proves it** (§1.3): the dark previews exist, they render the bug, and
`android.yml` uploads the picture. The gate has to be an assertion.

**Android — `app/src/test/kotlin/app/pbbls/android/components/GoogleSignInButtonContrastTest.kt`** (new).
JVM JUnit, no Compose runtime, no Robolectric. `androidx.compose.ui.graphics.Color`
is a value class over `ULong` and works fine on the plain JVM — `PalettesTest`
already asserts on it.

```kotlin
// WCAG 2.x relative luminance, test-local: the app ships no contrast utility
// and does not need one.
private fun lum(c: Color): Double { … }
private fun ratio(a: Color, b: Color): Double { … }

@Test fun labelIsLegibleOnTheCapsuleInBothThemes() {
    for ((name, p) in listOf("light" to SystemPaletteLight, "dark" to SystemPaletteDark)) {
        val r = ratio(googleButtonLabelColor(p), GoogleButtonSurface)
        assertTrue("$name: $r:1 fails AA 4.5:1", r >= 4.5)
    }
}

@Test fun onLightDoesNotFollowTheTheme() {
    assertEquals(SystemPaletteLight.onLight, SystemPaletteDark.onLight)
}
```

- **Fails today**: `googleButtonLabelColor` does not exist; wire it to
  `system.foreground` (the current behaviour) and the dark case computes
  **1.276** and fails. Verified by hand in §1.2.
- **Genuinely gates**: `android.yml:57` runs `./gradlew ktlintCheck
  testDebugUnitTest assembleDebug` on every PR touching `apps/android/**` and
  on every push to `main`. This is a merge-blocking check today, with no new
  infrastructure.
- **Catches the realistic regression**: the second test fails the day someone
  "helpfully" gives `onLight` a dark variant to match the palette's shape.

`PalettesTest.kt` also gains `onLight` to its two hex-transcription
assertions, keeping its role as the guard for hand-transcribed iOS values.

**iOS — `PebblesTests/GoogleSignInButtonContrastTests.swift`** (new, Swift
Testing per `apps/ios/CLAUDE.md`; never XCTest).

```swift
@Suite("GoogleSignInButton contrast")
struct GoogleSignInButtonContrastTests {
    @Test("label meets AA on the capsule in both appearances")
    func labelIsLegible() throws {
        for style in [UIUserInterfaceStyle.light, .dark] {
            let traits = UITraitCollection(userInterfaceStyle: style)
            let ink    = UIColor(GoogleSignInButton.labelColor).resolvedColor(with: traits)
            let ground = UIColor(GoogleSignInButton.surface).resolvedColor(with: traits)
            #expect(contrastRatio(ink, ground) >= 4.5, "\(style) fails AA")
        }
    }

    @Test("onLight resolves identically in both appearances")
    func onLightIsAppearanceIndependent() { … }
}
```

`UIColor(_ color: Color)` preserves an asset-catalog colour's dynamic
behaviour, so `.resolvedColor(with:)` is what actually exercises the dark
appearance — the mechanic the bug lives in. **Fails today** for the same
reason as the Android test.

Honest limitation: `PebblesTests` runs only via
`npm run test --workspace=@pbbls/ios` on a Mac. **It is not a CI gate**, because
no macOS runner exists (§7 Q4).

**iOS CI half — one step added to `ios.yml`.** `apps/ios/Scripts/verify-color-contrast.mjs`
reads `SystemOnLight.colorset/Contents.json` and asserts (a) exactly one
`colors` entry, i.e. no `appearances` block, and (b) ≥ 4.5:1 against
`#FFFFFF`. Ubuntu, no Xcode, milliseconds. This is the **one place Part 2
touches a Part 1 artefact** — an added step in a workflow file, not a code
dependency. It is cuttable: without it the iOS half is gated by the Swift test
alone, run locally.

**Not changed:** the existing dark previews. They stay as the review aid they
are. No new preview is added — the audit's remediation on that point is
already satisfied (§1.3).

---

## 5. Tasks, files and independent verification

### Part 1 — `chore/<issue>-ios-privacy-manifest`

**Why it stands alone:** a reviewer reading only this PR sees two manifests,
their derivation (the inventory doc), the two-line `project.yml` wiring, and a
script that keeps them true. Nothing in it refers forward to Part 2. Saying
yes or no needs no other context than the App Store rules and this repo's
data layer.

Tasks:

1. Write `apps/ios/Pebbles/Resources/PrivacyInfo.xcprivacy` (D3).
2. Write `apps/ios/PebblesWidget/PrivacyInfo.xcprivacy` (D4).
3. Add the two explicit `sources` entries to `apps/ios/project.yml` (D5).
4. Write `docs/store/ios-privacy-inventory.md` (D6).
5. Write `apps/ios/Scripts/verify-privacy-manifests.mjs`; add
   `"verify:privacy"` to `apps/ios/package.json` (D7).
6. Add `.github/workflows/ios.yml` (D7).
7. Append one entry to `docs/decisions/log.md` (§8).
8. Strike the "unused `PebblesWidget`" clause from
   `docs/superpowers/specs/2026-07-28-store-launch-roadmap.md:148` — it is
   factually wrong (§1.3.2) and this is the PR that proves it.

Files touched:

```
apps/ios/Pebbles/Resources/PrivacyInfo.xcprivacy          (new)
apps/ios/PebblesWidget/PrivacyInfo.xcprivacy              (new)
apps/ios/project.yml                                      (+6 lines)
apps/ios/package.json                                     (+1 script)
apps/ios/Scripts/verify-privacy-manifests.mjs             (new)
.github/workflows/ios.yml                                 (new)
docs/store/ios-privacy-inventory.md                       (new)
docs/decisions/log.md                                     (append)
docs/superpowers/specs/2026-07-28-store-launch-roadmap.md (1 line)
```

Independent verification (no Part 2 needed — Part 1 changes no Swift at all,
so lint and build are trivially green and the real proof is the script + a
regenerated project):

```bash
npm run verify:privacy --workspace=@pbbls/ios          # exit 0

cd apps/ios && xcodegen generate                       # then, per project memory:
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*/Build
grep -c 'PrivacyInfo.xcprivacy in Resources' \
  apps/ios/Pebbles.xcodeproj/project.pbxproj           # expect 2

npm run lint  --workspace=@pbbls/ios                   # swiftlint
npm run build --workspace=@pbbls/ios
```

Manual, once, before merge is claimed complete (this is the only thing that
proves the manifest against Apple rather than against ourselves): archive the
Release scheme, open Xcode's **Generate Privacy Report** on the archive, and
confirm the report lists exactly the seven collected types and the one
required-reason category; then upload to TestFlight and confirm no
ITMS-91053. If the response names a category we did not declare, add it and
record the source SDK in the inventory's SDK table (Risk R1).

### Part 2 — `fix/<issue>-google-btn-contrast`

**Why it stands alone:** the PR is one token added to two palettes, two call
sites repointed at it, and two unit tests that fail without the change. A
reviewer needs the contrast number and the 1:1 mirror rule, nothing else. It
does not mention the privacy manifest anywhere.

Tasks:

1. Add `onLight` to `SystemPalette` + both palettes (Android), and to
   `SystemPalette` + a new `SystemOnLight.colorset` (iOS).
2. Repoint `GoogleSignInButton` on both surfaces via the hoisted
   surface/label pair (§4.2).
3. Add the swatch to both token galleries.
4. Add `GoogleSignInButtonContrastTest.kt` and
   `GoogleSignInButtonContrastTests.swift`; extend `PalettesTest.kt`.
5. Add `apps/ios/Scripts/verify-color-contrast.mjs` + one step in `ios.yml`.
6. Append one entry to `docs/decisions/log.md` (§8).
7. Lab Note (EN/FR) in the PR body — the button is a user-visible Arkaik node
   (`AC-google-sign-in`) and a user-facing fix, which the Lab Note contract
   classifies as `species: feature`, `platform: all`.

Files touched:

```
apps/android/app/src/main/kotlin/app/pbbls/android/theme/Palettes.kt
apps/android/app/src/main/kotlin/app/pbbls/android/components/GoogleSignInButton.kt
apps/android/app/src/main/kotlin/app/pbbls/android/DebugTokenPreviewScreen.kt
apps/android/app/src/test/kotlin/app/pbbls/android/theme/PalettesTest.kt
apps/android/app/src/test/kotlin/app/pbbls/android/components/GoogleSignInButtonContrastTest.kt  (new)
apps/ios/Pebbles/Theme/Palettes.swift
apps/ios/Pebbles/Theme/ColorTokensPreview.swift
apps/ios/Pebbles/Resources/Assets.xcassets/SystemOnLight.colorset/Contents.json                  (new)
apps/ios/Pebbles/Components/Buttons/GoogleSignInButton.swift
apps/ios/PebblesTests/GoogleSignInButtonContrastTests.swift                                      (new)
apps/ios/Scripts/verify-color-contrast.mjs                                                       (new)
.github/workflows/ios.yml                                                                        (+1 step)
docs/decisions/log.md                                                                            (append)
```

Independent verification:

```bash
# iOS — real gate, run locally (see project memory on stale DerivedData)
cd apps/ios && xcodegen generate
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*/Build
npm run test  --workspace=@pbbls/ios      # GoogleSignInButtonContrastTests green
npm run lint  --workspace=@pbbls/ios
npm run build --workspace=@pbbls/ios

node apps/ios/Scripts/verify-color-contrast.mjs

# Android — no JDK/SDK on this machine; `npm run build --workspace=@pbbls/android`
# no-ops via scripts/gradle-if-sdk.sh. The gate is android.yml:
#   ./gradlew ktlintCheck testDebugUnitTest assembleDebug
# Push and read the check. The ui-screenshots artifact's *Dark previews are the
# eyeball confirmation, not the gate.
```

**TDD note:** write both tests first, against the *current* `system.foreground`
wiring, and record the two red runs (Android `1.276 < 4.5`, iOS the same) in
the PR body. A contrast test that was never seen red is a test that might be
asserting nothing.

---

## 6. Risks / open questions

**R1 — SPM dependencies may use required-reason APIs we cannot see statically.**
supabase-swift, SVGView and rive-ios link into the app binary, so Apple's
analysis attributes their required-reason calls to *our* bundle. None is on
Apple's signature-required SDK list, and none ships a manifest of its own. The
D7 script greps only first-party sources, so it cannot cover them.
*Mitigation:* before the first archive, `grep` the resolved SPM checkouts for
the same five symbol sets and add any hit to the app manifest and the
inventory's SDK table; then treat the first upload response as authoritative.
This is why §5's manual archive step is part of "done", not a nice-to-have.

**R2 — the published privacy policy is stale in both directions.** v1.0.0
describes features that do not exist (Therapist, Decisions, Cairns, Google
Gemma — `grep -ril gemma` hits only the legal docs) and omits ones that do
(marketplace, karma, connections, public profiles, drafts). The manifest
declares what the binary does, which is the correct rule; but a reviewer who
reads both will see a policy claiming a US LLM transfer that the app never
makes. The roadmap already schedules the rewrite for **M56**, which precedes
M57 on the critical path. *Do not soften the manifest to match the stale
policy.* The inventory's policy-section column is what will make the M56
rewrite mechanical.

**R3 — no iOS CI beyond the Node checks.** Part 1's workflow proves the
manifests are present and consistent with the sources; nothing proves the app
still builds, and Part 2's Swift test does not run in CI. A macOS runner would
close both (§7 Q4).

**R4 — a same-class contrast defect is knowingly left in four places.**
`PebblesCheckbox` (both surfaces, 2.71:1 unchecked glyph, failing 1.4.11's
3:1) and iOS `PebblesTextInput` (2.71:1 body text, failing 1.4.5's 4.5:1 —
and a 1:1 mirror divergence, since Android fixed it). Out of scope per the
no-refactor rule; §7 Q6 proposes the follow-up. The token Part 2 introduces
makes each a one-line fix. Note the text input probably wants Android's answer
(theme the *ground* to `system.background`) rather than `onLight`, because
nothing pins that surface.

**R5 — the Google capsule's border is 1.28:1 in *light* mode.** `system.muted`
(`#E9E2E4`) on a white capsule over a white page: the border is the only thing
delimiting the control, at 1.28:1 against both. Arguably WCAG 1.4.11. Not part
of this finding, not part of this stack, listed so it is not rediscovered as
new.

**R6 — the widget's empty manifest could be read as an oversight.** Mitigated
by the comment inside the file (D4). If the widget ever gains an App Group or
a network read, its manifest stops being empty and the D7 script's
symbol-coupling catches the `UserDefaults` case automatically.

### Open questions for the maintainer

- **Q1 — declare `NSPrivacyCollectedDataTypeHealth`?** Emotion/intensity/
  valence is not HealthKit data and Apple's `SensitiveInfo` definition does
  not cover emotional state, but the published policy qualifies moods as GDPR
  Art. 9 health data (§3.2, §4.1) and `Health` explicitly covers "any other
  user provided health or medical data". Declaring it is the consistent
  answer; it may draw App Review questions about a HealthKit entitlement the
  app does not have (and policy §4.4 discusses HealthKit "if applicable",
  which the M56 rewrite should delete). **Recommendation: declare it.**
- **Q2 — souls: `Name` or `Contacts`?** Souls are names of third parties the
  user types; no Contacts framework is imported and no contact identifiers are
  stored. `Name` is tighter and true; `Contacts` is the literal reading of
  "information about the user's contacts" and would be the conservative
  answer. **Recommendation: `Name`, with the reasoning recorded in the
  inventory.**
- **Q3 — do Supabase auth logs need a declaration?** Login timestamps and IP
  are collected server-side for security. There is no ASC type for "auth
  audit log", and ASC's disclosure exceptions cover security/fraud-prevention
  data. **Recommendation: do not declare; record the reasoning in the
  inventory's not-declared table.**
- **Q4 — add a macOS job to `ios.yml`?** It would make Part 2's Swift test and
  the iOS build real gates, at roughly 10× the runner-minute cost of the
  ubuntu job. Not required by either finding. **Recommendation: ship the
  ubuntu job now, open a separate issue for the macOS job so the cost is its
  own decision** (the same shape as the `supabase.yml` service-role call).
- **Q5 — order, one more time.** Android auto-ships to Play internal testing
  on every push to `main`, so the contrast bug is live for internal testers
  and the manifest blocks a submission not yet attempted. Keeping Part 1 first
  costs internal testers one merge. If that is one merge too many, cut Part 2
  as its own stack off `main` (§2). **Recommendation: keep the given order.**
- **Q6 — approve the R4 follow-up?** Root `CLAUDE.md` forbids refactoring
  without explicit approval, so the checkbox and text-input sites are written
  up rather than fixed. **Recommendation: cut a follow-up issue in the same
  sitting so the token does not sit with one consumer.**
- **Q7 — one issue or two for Part 2?** `CLAUDE.md` § Issues says a feature
  landing on several surfaces is one issue per client. This is a two-line fix
  plus a shared token; splitting it produces two PRs that must land together
  to keep the 1:1 mirror, and the token would have no consumer in whichever
  merges first. §7 proposes **one** issue carrying both surface labels.

---

## 7. Proposed issues

Not created — the GitHub API is unreachable from this sandbox. Titles follow
`[Type] Description`; labels are drawn from root `CLAUDE.md` (species: `feat`
`fix` `bug` `chore` `docs` `test` `quality`; domain: `core` `ui` `db` `api`
`auth` `facility` `legal`; surface: `web` `ios` `android` `supabase`).
Milestone **M57 · Store readiness**, verified against
`docs/superpowers/specs/2026-07-28-store-launch-roadmap.md:47-48,146-149`.

### Part 1

- **Title:** `[Chore] Ship PrivacyInfo.xcprivacy in both iOS targets and pin the nutrition-label answers`
- **Labels:** `chore` · `ios` · `legal` · `facility`
  (`chore` because nothing user-visible changes; `legal` for the
  privacy-declaration domain; `facility` for the new workflow and check
  script. Drop `facility` if the maintainer prefers exactly one scope beyond
  the surface.)
- **Milestone:** M57 · Store readiness
- **Closes:** `F-2026-08-GDP-ios-02`, `F-2026-08-PLT-ios-01`
- **Lab Note:** none — compliance plumbing, no user-visible change. Delete the
  section and add `no-lab-note` if the advisory workflow comments.

### Part 2

- **Title:** `[Bug] Google sign-in label is invisible in dark mode on iOS and Android`
- **Labels:** `bug` · `ui` · `ios` · `android`
  (the PR that resolves it takes `fix` in place of `bug`, per the PR
  checklist)
- **Milestone:** M57 · Store readiness — it is a store-submission-adjacent
  accessibility defect and belongs to this stack's story. If the maintainer
  keeps M57 strictly to the roadmap's listed scope, move it to the current
  bugfix milestone; the stack does not care.
- **Closes:** `F-2026-08-A11Y-android-01`
- **Lab Note:** required. `species: feature`, `platform: all`,
  `status: in_progress`, `published: false` — a user-facing fix is a
  `feature` per the contract, and it lands on both native surfaces.

### Follow-up (R4, not part of this stack)

- **Title:** `[Bug] Themed ink on pinned light surfaces fails contrast in the checkbox and the iOS text input`
- **Labels:** `bug` · `ui` · `ios` · `android`
- **Milestone:** maintainer's call.

---

## 8. Decision-log entries

Both parts clear the significance bar in `CLAUDE.md`'s PR checklist step 6 —
a future agent would otherwise waste real time rediscovering either. Append
(supersede-don't-edit), one per part:

1. **Part 1** — *"Each shippable iOS bundle carries its own privacy manifest;
   the nutrition-label answers are derived from the code and kept in
   `docs/store/`."* Context: findings `F-2026-08-GDP-ios-02` /
   `F-2026-08-PLT-ios-01`, ITMS-91053, the Xcode Cloud pipeline. Decision: two
   manifests, never one in `Shared/`; explicit `project.yml` entries even
   though inference works; a symbol-coupled ubuntu check in a new `ios.yml`.
   Consequences: a new SPM dependency / storage bucket / personal-data table
   updates the inventory and the manifests **in the same change**; the widget
   is not "unused" and the roadmap line saying so is struck.
2. **Part 2** — *"Ink on a pinned light surface reads `system.onLight`, never
   `system.foreground`."* Context: 1.28:1 on the primary sign-in affordance,
   propagated to both surfaces by the 1:1 mirror rule, and rendered every CI
   run into a screenshot nobody diffs. Decision: a fifth `SystemPalette`
   field, identical in both palettes, plus a JVM/Swift contrast assertion —
   because render-to-view screenshots structurally cannot gate a colour bug.
   Consequences: a new component pinning its own ground takes `onLight` for
   its ink; `PalettesTest` and its iOS counterpart are the guards;
   `PebblesCheckbox` and iOS `PebblesTextInput` are known remaining instances
   (R4).

Also relevant and **not** superseded by anything here:

- **2026-09-02 (#743)** — a check added to `scripts/` gains its npm script and
  its workflow step in the same change, or it is not a gate. Both parts follow
  it (`verify:privacy`, and the contrast step).
- **2026-09-02 (#741)** — CI and a human invoke the identical command. Both
  parts follow it.
- **2026-08-24 (#729/#735)** — deliberate iOS/Android divergences are recorded
  rather than "aligned". This stack creates none: `onLight` lands identically
  on both surfaces, which is the point.
