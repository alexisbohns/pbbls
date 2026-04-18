# Onboarding Screens Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a 4-step Apple-Tips-style onboarding flow to the iOS app, presented after signup and replayable from a PathView info icon, with screen content driven by a static config that's fully dissociated from the rendering views.

**Architecture:** New `Pebbles/Features/Onboarding/` folder containing five files: a pure data model (`OnboardingImage`, `OnboardingStep`), a static content config (`OnboardingSteps`), a single-page render (`OnboardingPageView`), and a container that handles paging + dismissal (`OnboardingView`). Persistence uses `@AppStorage("hasSeenOnboarding")`. Both `RootView` (initial signin trigger) and `PathView` (replay button) present the same `OnboardingView` via `.fullScreenCover`, differing only in their `onFinish` closure.

**Tech Stack:** Swift 5.9, SwiftUI (iOS 17+), Swift Testing (`@Suite`/`@Test`/`#expect`), XcodeGen, SwiftLint.

**Spec:** [`docs/superpowers/specs/2026-04-18-onboarding-screens-design.md`](../specs/2026-04-18-onboarding-screens-design.md)

**Issue:** [#280](https://github.com/Bohns/pbbls/issues/280)

**Branch (already created):** `feat/280-onboarding-screens`

---

## File Map

| File | Created or Modified | Responsibility |
|---|---|---|
| `apps/ios/Pebbles/Features/Onboarding/OnboardingImage.swift` | Create | Enum: `.asset(String) \| .remote(URL)` |
| `apps/ios/Pebbles/Features/Onboarding/OnboardingStep.swift` | Create | Struct: `id`, `image`, `title`, `description` |
| `apps/ios/Pebbles/Features/Onboarding/OnboardingSteps.swift` | Create | Static `[OnboardingStep]` — the 4 screens' content |
| `apps/ios/Pebbles/Features/Onboarding/OnboardingPageView.swift` | Create | Renders one step (image, title, description) |
| `apps/ios/Pebbles/Features/Onboarding/OnboardingView.swift` | Create | TabView container, toolbar, last-page CTA |
| `apps/ios/Pebbles/Resources/Assets.xcassets/OnboardingIntro.imageset/` | Create | Placeholder image set |
| `apps/ios/Pebbles/Resources/Assets.xcassets/OnboardingConcept.imageset/` | Create | Placeholder image set |
| `apps/ios/Pebbles/Resources/Assets.xcassets/OnboardingQualify.imageset/` | Create | Placeholder image set |
| `apps/ios/Pebbles/Resources/Assets.xcassets/OnboardingCarving.imageset/` | Create | Placeholder image set |
| `apps/ios/Pebbles/RootView.swift` | Modify | Present onboarding `.fullScreenCover` after signin when flag is false |
| `apps/ios/Pebbles/Features/Path/PathView.swift` | Modify | Add `info.circle` toolbar button + replay `.fullScreenCover` |
| `apps/ios/PebblesTests/Features/Onboarding/OnboardingStepsTests.swift` | Create | Validate the static config (count, unique IDs, non-empty fields) |
| `docs/arkaik/bundle.json` | Modify | Replace 3 existing onboarding view nodes with 4 matching the iOS spec |

---

## Task 1 — Data model: `OnboardingImage` and `OnboardingStep`

**Files:**
- Create: `apps/ios/Pebbles/Features/Onboarding/OnboardingImage.swift`
- Create: `apps/ios/Pebbles/Features/Onboarding/OnboardingStep.swift`

These are pure value types. No tests of their own — they're tested indirectly via `OnboardingStepsTests` in Task 2.

- [ ] **Step 1: Create `OnboardingImage.swift`**

```swift
import Foundation

/// Source of a single onboarding screen's illustration.
///
/// `.asset` references an image set bundled in `Assets.xcassets`.
/// `.remote` references a CDN/Supabase Storage URL fetched at view time.
/// The split keeps `OnboardingPageView` agnostic to where artwork lives —
/// migrating from local placeholders to remote artwork is a config change.
enum OnboardingImage {
    case asset(String)
    case remote(URL)
}
```

- [ ] **Step 2: Create `OnboardingStep.swift`**

```swift
import Foundation

/// Single onboarding screen's content. The view layer reads these fields
/// and renders them — it never branches on the step's `id`.
struct OnboardingStep: Identifiable {
    let id: String
    let image: OnboardingImage
    let title: String
    let description: String
}
```

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Onboarding/OnboardingImage.swift \
        apps/ios/Pebbles/Features/Onboarding/OnboardingStep.swift
git commit -m "$(cat <<'EOF'
feat(ios): add onboarding data model

Introduces OnboardingImage (asset/remote enum) and OnboardingStep
(id, image, title, description). Pure value types — content config
and rendering land in subsequent commits.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2 — Content config: `OnboardingSteps` (TDD)

**Files:**
- Create: `apps/ios/Pebbles/Features/Onboarding/OnboardingSteps.swift`
- Test: `apps/ios/PebblesTests/Features/Onboarding/OnboardingStepsTests.swift`

This is the dissociated content layer the spec is designed around. Tests guard the four invariants: count, unique IDs, non-empty title, non-empty description.

- [ ] **Step 1: Write the failing tests**

Create `apps/ios/PebblesTests/Features/Onboarding/OnboardingStepsTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("OnboardingSteps")
struct OnboardingStepsTests {

    @Test("contains exactly 4 steps")
    func stepCount() {
        #expect(OnboardingSteps.all.count == 4)
    }

    @Test("step IDs are unique")
    func uniqueIds() {
        let ids = OnboardingSteps.all.map(\.id)
        #expect(Set(ids).count == ids.count)
    }

    @Test("every step has a non-empty title")
    func titlesNonEmpty() {
        for step in OnboardingSteps.all {
            #expect(!step.title.isEmpty)
        }
    }

    @Test("every step has a non-empty description")
    func descriptionsNonEmpty() {
        for step in OnboardingSteps.all {
            #expect(!step.description.isEmpty)
        }
    }

    @Test("step IDs match the spec order")
    func idsMatchSpecOrder() {
        let ids = OnboardingSteps.all.map(\.id)
        #expect(ids == ["intro", "concept", "qualify", "carving"])
    }
}
```

- [ ] **Step 2: Regenerate the Xcode project so the new test file is included**

```bash
npm run generate --workspace=@pbbls/ios
```

Expected: `Generated project successfully` (no errors).

- [ ] **Step 3: Run the tests to verify they fail**

```bash
npm run test --workspace=@pbbls/ios
```

Expected: build failure with `cannot find 'OnboardingSteps' in scope` (the type doesn't exist yet).

- [ ] **Step 4: Create `OnboardingSteps.swift`**

```swift
import Foundation

/// The four onboarding steps shown to new users on signup, and on demand
/// from the Path screen's info button. Editing copy or reordering steps
/// is a single-file change here — `OnboardingView` reads `.all` opaquely.
enum OnboardingSteps {
    static let all: [OnboardingStep] = [
        .init(
            id: "intro",
            image: .asset("OnboardingIntro"),
            title: "Your life is a path.",
            description: """
            Every moment matters — the big ones and the quiet ones. \
            But most of them slip away before you even notice. \
            Pebbles helps you collect them, one by one.
            """
        ),
        .init(
            id: "concept",
            image: .asset("OnboardingConcept"),
            title: "Drop a pebble, keep the moment.",
            description: """
            A coffee with a friend. A concert that gave you chills. \
            A tough conversation. Record it in seconds — \
            no blank page, no pressure, no audience.
            """
        ),
        .init(
            id: "qualify",
            image: .asset("OnboardingQualify"),
            title: "Qualify and relate.",
            description: """
            Associate your moment with an emotion and a domain, \
            relate with people and add your pebble to a stack or a collection.
            """
        ),
        .init(
            id: "carving",
            image: .asset("OnboardingCarving"),
            title: "Get your pebble.",
            description: """
            Choose or carve a glyph to mark the moment. \
            Then reveal a unique pebble illustration to remember your moment.
            """
        )
    ]
}
```

- [ ] **Step 5: Run the tests to verify they pass**

```bash
npm run test --workspace=@pbbls/ios
```

Expected: all 5 `OnboardingSteps` tests pass.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Onboarding/OnboardingSteps.swift \
        apps/ios/PebblesTests/Features/Onboarding/OnboardingStepsTests.swift
git commit -m "$(cat <<'EOF'
feat(ios): add onboarding content config

Static four-step config dissociated from render. Editing copy or
reordering screens is a single-file change. Covered by tests for
count, unique IDs, non-empty fields, and spec-ordered IDs.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3 — Placeholder image sets in `Assets.xcassets`

**Files:**
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/OnboardingIntro.imageset/Contents.json`
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/OnboardingIntro.imageset/placeholder.png`
- Repeat for `OnboardingConcept`, `OnboardingQualify`, `OnboardingCarving`.

A 1×1 transparent PNG keeps the build green so the layout can be wired up before final artwork lands.

- [ ] **Step 1: Generate one 1×1 transparent PNG to share across all four image sets**

```bash
cd /tmp && python3 -c "
import struct, zlib
sig = b'\x89PNG\r\n\x1a\n'
def chunk(t, d):
    return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
ihdr = struct.pack('>IIBBBBB', 1, 1, 8, 6, 0, 0, 0)  # 1x1 RGBA
idat_raw = b'\x00' + b'\x00\x00\x00\x00'             # 1 row, transparent pixel
idat = zlib.compress(idat_raw)
with open('/tmp/placeholder.png', 'wb') as f:
    f.write(sig + chunk(b'IHDR', ihdr) + chunk(b'IDAT', idat) + chunk(b'IEND', b''))
print('ok')
"
```

Expected: prints `ok`. The file `/tmp/placeholder.png` is a valid 70-byte transparent PNG.

- [ ] **Step 2: Create the four image sets**

```bash
cd /Users/alexis/code/pbbls/apps/ios/Pebbles/Resources/Assets.xcassets && \
for name in OnboardingIntro OnboardingConcept OnboardingQualify OnboardingCarving; do
  mkdir -p "${name}.imageset"
  cp /tmp/placeholder.png "${name}.imageset/placeholder.png"
  cat > "${name}.imageset/Contents.json" <<'EOF'
{
  "images" : [
    {
      "filename" : "placeholder.png",
      "idiom" : "universal",
      "scale" : "1x"
    },
    {
      "idiom" : "universal",
      "scale" : "2x"
    },
    {
      "idiom" : "universal",
      "scale" : "3x"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
EOF
done
ls /Users/alexis/code/pbbls/apps/ios/Pebbles/Resources/Assets.xcassets/ | grep Onboarding
```

Expected: lists `OnboardingCarving.imageset`, `OnboardingConcept.imageset`, `OnboardingIntro.imageset`, `OnboardingQualify.imageset`.

- [ ] **Step 3: Verify the build still passes (the asset catalog now references real files)**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: build succeeds with no asset-catalog warnings about `OnboardingIntro`/`Concept`/`Qualify`/`Carving`. (Pre-existing warnings about empty `AppIcon` are expected and noted in `apps/ios/CLAUDE.md`.)

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Resources/Assets.xcassets/Onboarding*.imageset
git commit -m "$(cat <<'EOF'
feat(ios): add onboarding image-set placeholders

1x1 transparent PNGs for OnboardingIntro/Concept/Qualify/Carving.
Final artwork swaps in by replacing placeholder.png — config in
OnboardingSteps does not change.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4 — `OnboardingPageView`: render one step

**Files:**
- Create: `apps/ios/Pebbles/Features/Onboarding/OnboardingPageView.swift`

Pure presentation. Knows nothing about pagination, dismissal, or persistence. No tests — SwiftUI rendering is verified via Xcode preview and the build per project convention (`apps/ios/CLAUDE.md`: "No UI tests for now.").

- [ ] **Step 1: Create `OnboardingPageView.swift`**

```swift
import SwiftUI

/// Renders a single `OnboardingStep`. Layout: large illustration card
/// at the top, bold title, body description. Switches on the image
/// enum — local asset vs remote URL — without leaking that distinction
/// to the parent.
struct OnboardingPageView: View {
    let step: OnboardingStep

    var body: some View {
        VStack(spacing: 32) {
            illustration
                .frame(maxWidth: .infinity)
                .frame(height: 360)
                .background(Color.pebblesSurfaceAlt)
                .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))

            VStack(alignment: .leading, spacing: 12) {
                Text(step.title)
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(Color.pebblesForeground)

                Text(step.description)
                    .font(.body)
                    .foregroundStyle(Color.pebblesMutedForeground)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 24)
        .padding(.top, 24)
    }

    @ViewBuilder
    private var illustration: some View {
        switch step.image {
        case .asset(let name):
            Image(name)
                .resizable()
                .scaledToFit()
                .padding(24)

        case .remote(let url):
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().scaledToFit().padding(24)
                default:
                    Color.pebblesSurfaceAlt
                }
            }
        }
    }
}

#Preview("Asset") {
    OnboardingPageView(step: OnboardingSteps.all[0])
}

#Preview("Long copy") {
    OnboardingPageView(step: OnboardingSteps.all[2])
}
```

- [ ] **Step 2: Regenerate the Xcode project so the new file is compiled**

```bash
npm run generate --workspace=@pbbls/ios
```

Expected: `Generated project successfully`.

- [ ] **Step 3: Build**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: BUILD SUCCEEDED.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Onboarding/OnboardingPageView.swift
git commit -m "$(cat <<'EOF'
feat(ios): add OnboardingPageView render

Renders a single OnboardingStep — illustration card, title, body.
Switches on OnboardingImage so callers don't care whether artwork
is bundled or fetched.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5 — `OnboardingView`: paging container

**Files:**
- Create: `apps/ios/Pebbles/Features/Onboarding/OnboardingView.swift`

Container that holds a paged `TabView`, the close/skip toolbar, and the last-page "Start your path" CTA. Receives `steps` and an `onFinish` closure — the closure is the seam that lets the same view serve both the initial gate (`hasSeenOnboarding = true`) and the replay (just dismiss).

- [ ] **Step 1: Create `OnboardingView.swift`**

```swift
import SwiftUI

/// Paged onboarding flow. Renders one `OnboardingPageView` per step inside
/// a `TabView` with the iOS page-style indicator. Toolbar exposes a close
/// (`xmark`) and a `Skip` button — both invoke `onFinish`. The last page
/// also shows a full-width prominent "Start your path" button.
///
/// Persistence (`@AppStorage`) lives at the call site so the view stays
/// previewable and the same view serves both initial-gate and replay.
struct OnboardingView: View {
    let steps: [OnboardingStep]
    let onFinish: () -> Void

    @State private var currentIndex: Int = 0

    var body: some View {
        NavigationStack {
            TabView(selection: $currentIndex) {
                ForEach(Array(steps.enumerated()), id: \.element.id) { index, step in
                    OnboardingPageView(step: step)
                        .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .always))
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        onFinish()
                    } label: {
                        Image(systemName: "xmark")
                    }
                    .accessibilityLabel("Close onboarding")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Skip") {
                        onFinish()
                    }
                }
            }
            .safeAreaInset(edge: .bottom) {
                if currentIndex == steps.count - 1 {
                    Button {
                        onFinish()
                    } label: {
                        Text("Start your path")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                    }
                    .buttonStyle(.borderedProminent)
                    .padding(.horizontal, 24)
                    .padding(.bottom, 24)
                }
            }
            .pebblesScreen()
        }
    }
}

#Preview {
    OnboardingView(steps: OnboardingSteps.all) {
        // no-op preview close
    }
}
```

- [ ] **Step 2: Regenerate the Xcode project**

```bash
npm run generate --workspace=@pbbls/ios
```

Expected: `Generated project successfully`.

- [ ] **Step 3: Build**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: BUILD SUCCEEDED.

- [ ] **Step 4: Manual preview check**

Open `apps/ios/Pebbles/Features/Onboarding/OnboardingView.swift` in Xcode and run the canvas preview. Verify:
- 4 pages, dot indicator at the bottom showing position.
- Close (`X`) in top-left, "Skip" in top-right.
- "Start your path" button only appears on the 4th page.
- Swiping advances the page; the CTA appears/disappears as you reach/leave the last page.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Onboarding/OnboardingView.swift
git commit -m "$(cat <<'EOF'
feat(ios): add OnboardingView container

Paged TabView with iOS page-style indicator, close + Skip toolbar
buttons, and a last-page "Start your path" CTA. Persistence is
delegated to the caller via onFinish so the same view serves both
the initial signup gate and the Path replay button.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6 — Wire the initial trigger in `RootView`

**Files:**
- Modify: `apps/ios/Pebbles/RootView.swift`

`RootView` gains an `@AppStorage` flag and an `.onChange` handler that flips a presentation flag exactly when the session transitions from nil to a real user, but only if onboarding hasn't already been seen.

- [ ] **Step 1: Read the current `RootView.swift`**

```bash
cat /Users/alexis/code/pbbls/apps/ios/Pebbles/RootView.swift
```

Confirm it matches the structure from the spec (ZStack of three branches: `Color.clear`, `AuthView`, `MainTabView`, plus a `.task { await supabase.start() }`).

- [ ] **Step 2: Replace the file with the wired version**

```swift
import SwiftUI

/// Top-level auth gate. Reads `SupabaseService` from the environment and
/// decides whether to show the auth screen or the main tab bar.
///
/// During `isInitializing`, renders `Color.clear` so the user never sees a
/// flash of the wrong screen while the keychain session is being read.
/// `.task { await supabase.start() }` subscribes to auth state changes for
/// the lifetime of this view (= the lifetime of the app).
///
/// On the first transition from no-session to signed-in (per device),
/// presents `OnboardingView` as a `.fullScreenCover` over `MainTabView`.
struct RootView: View {
    @Environment(SupabaseService.self) private var supabase
    @AppStorage("hasSeenOnboarding") private var hasSeenOnboarding = false
    @State private var isPresentingOnboarding = false

    var body: some View {
        ZStack {
            if supabase.isInitializing {
                Color.clear
            } else if supabase.session == nil {
                AuthView()
            } else {
                MainTabView()
                    .fullScreenCover(isPresented: $isPresentingOnboarding) {
                        OnboardingView(steps: OnboardingSteps.all) {
                            hasSeenOnboarding = true
                            isPresentingOnboarding = false
                        }
                    }
            }
        }
        .task {
            await supabase.start()
        }
        .onChange(of: supabase.session?.user.id) { _, newUserId in
            if newUserId != nil && !hasSeenOnboarding {
                isPresentingOnboarding = true
            }
        }
    }
}

#Preview {
    RootView()
        .environment(SupabaseService())
}
```

- [ ] **Step 3: Build**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: BUILD SUCCEEDED.

- [ ] **Step 4: Manual verification — fresh signup**

In a simulator (or device) where the app has never been installed (or with `hasSeenOnboarding` reset — `xcrun simctl spawn booted defaults delete app.pbbls.ios hasSeenOnboarding` works for the simulator), sign up with a fresh email. Verify:
- After consent + Continue, the onboarding fullscreen cover appears over the tab bar.
- Tapping "X", "Skip", or "Start your path" on page 4 dismisses the cover.
- After dismissal, signing out and signing back in does NOT re-present onboarding.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/RootView.swift
git commit -m "$(cat <<'EOF'
feat(ios): present onboarding on first signin

RootView gains @AppStorage("hasSeenOnboarding") and presents
OnboardingView as a fullScreenCover over MainTabView the first time
session transitions from nil to a real user. onChange on the user
id ensures the cover fires once per signin transition rather than
on every render.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7 — Wire the replay trigger in `PathView`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PathView.swift`

A trailing toolbar `info.circle` button presents `OnboardingView` as a `.fullScreenCover`. The replay path's `onFinish` does NOT touch `@AppStorage` — replay is idempotent.

- [ ] **Step 1: Add `@State` for the replay presentation flag**

In `apps/ios/Pebbles/Features/Path/PathView.swift`, find the existing state block (around line 6–11):

```swift
    @State private var pebbles: [Pebble] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var isPresentingCreate = false
    @State private var selectedPebbleId: UUID?
    @State private var presentedDetailPebbleId: UUID?
```

Append one new line so it becomes:

```swift
    @State private var pebbles: [Pebble] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var isPresentingCreate = false
    @State private var selectedPebbleId: UUID?
    @State private var presentedDetailPebbleId: UUID?
    @State private var isPresentingOnboarding = false
```

- [ ] **Step 2: Add the toolbar button and the `.fullScreenCover` modifier**

In the same file, find the `body` block. The current structure is:

```swift
    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Path")
                .pebblesScreen()
        }
        .task { await load() }
        .sheet(isPresented: $isPresentingCreate) { ... }
        .sheet(item: $selectedPebbleId) { id in ... }
        .sheet(item: $presentedDetailPebbleId) { id in ... }
    }
```

Replace it with:

```swift
    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Path")
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            isPresentingOnboarding = true
                        } label: {
                            Image(systemName: "info.circle")
                        }
                        .accessibilityLabel("How Pebbles works")
                    }
                }
                .pebblesScreen()
        }
        .task { await load() }
        .sheet(isPresented: $isPresentingCreate) {
            CreatePebbleSheet(onCreated: { newPebbleId in
                presentedDetailPebbleId = newPebbleId
                Task { await load() }
            })
        }
        .sheet(item: $selectedPebbleId) { id in
            EditPebbleSheet(pebbleId: id, onSaved: {
                Task { await load() }
            })
        }
        .sheet(item: $presentedDetailPebbleId) { id in
            PebbleDetailSheet(pebbleId: id)
        }
        .fullScreenCover(isPresented: $isPresentingOnboarding) {
            OnboardingView(steps: OnboardingSteps.all) {
                isPresentingOnboarding = false
            }
        }
    }
```

(The `.toolbar` modifier must be inside the `NavigationStack` so the bar finds it. The `.fullScreenCover` is outside, alongside the existing sheets.)

- [ ] **Step 3: Build**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: BUILD SUCCEEDED.

- [ ] **Step 4: Manual verification — replay**

In a simulator already past the initial onboarding (or with `hasSeenOnboarding` set to true), open the app and verify:
- An `info.circle` icon appears in the top-right of the Path screen's nav bar.
- Tapping it presents the onboarding fullScreenCover.
- Dismissing it (X / Skip / Start your path) returns to Path with no other side effects.
- Signing out and back in does NOT re-present onboarding — the replay path did not affect `hasSeenOnboarding`.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PathView.swift
git commit -m "$(cat <<'EOF'
feat(ios): add Path info button to replay onboarding

Adds an info.circle toolbar button on PathView that presents
OnboardingView as a fullScreenCover. Replay is idempotent — the
onFinish closure here does not touch @AppStorage("hasSeenOnboarding").

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8 — Lint + final test sweep

**Files:** none

- [ ] **Step 1: Run SwiftLint**

```bash
npm run lint --workspace=@pbbls/ios
```

Expected: 0 violations (or only pre-existing violations unrelated to the new files). If new violations exist, fix them in the offending file before continuing.

- [ ] **Step 2: Run the full test suite**

```bash
npm run test --workspace=@pbbls/ios
```

Expected: all tests pass — including the new `OnboardingSteps` suite (5 tests) and all pre-existing suites.

- [ ] **Step 3: If any fixes were needed, commit them**

```bash
git status
# If anything changed:
git add -p   # review hunks; only stage what relates to this branch
git commit -m "$(cat <<'EOF'
quality(ios): fix lint warnings on onboarding files

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9 — Update the Arkaik product map

**Files:**
- Modify: `docs/arkaik/bundle.json`

The bundle currently has 3 onboarding view nodes (`V-onboarding-welcome`, `V-onboarding-collect`, `V-onboarding-activate`) modeled on an older 3-screen web flow. The iOS spec defines a 4-screen flow with different IDs and copy. Replace the old nodes with new ones matching the spec.

- [ ] **Step 1: Invoke the Arkaik skill**

Run the `arkaik` skill (per `apps/ios/CLAUDE.md` and the project root `CLAUDE.md`, this is mandatory whenever product architecture changes). The skill at `.claude/skills/arkaik/skill.md` documents the surgical update patterns and the validation script.

- [ ] **Step 2: Apply the following changes via the Arkaik skill's surgical patterns**

In `docs/arkaik/bundle.json`:

a) **Remove** the 3 existing onboarding view nodes:
- `V-onboarding-welcome`
- `V-onboarding-collect`
- `V-onboarding-activate`

b) **Add** 4 new view nodes matching the iOS spec — IDs `V-onboarding-intro`, `V-onboarding-concept`, `V-onboarding-qualify`, `V-onboarding-carving`. Titles and descriptions taken from `OnboardingSteps.all`. Mark them as iOS-platform views following the schema convention used by other iOS-only views (consult `references/` in the arkaik skill folder if the platform field convention isn't obvious from existing entries).

c) **Update** the `F-onboarding` flow node's `composes` edges to reference the 4 new view IDs instead of the old 3.

d) **Update** any edge whose `source_id` or `target_id` references one of the 3 removed view IDs to point at the appropriate new view (e.g. `e-V-onboarding-activate-V-home` becomes `e-V-onboarding-carving-V-home` since `carving` is now the final screen before main).

- [ ] **Step 3: Run the Arkaik validation script**

The exact command lives in `.claude/skills/arkaik/scripts/`. Run it; expected: validation passes with no schema or referential-integrity errors.

- [ ] **Step 4: Commit**

```bash
git add docs/arkaik/bundle.json
git commit -m "$(cat <<'EOF'
docs(arkaik): align onboarding nodes with iOS 4-screen flow

Replaces the 3 legacy onboarding view nodes
(welcome/collect/activate) with 4 nodes matching the iOS spec
(intro/concept/qualify/carving). F-onboarding flow and downstream
edges updated accordingly.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10 — Open the pull request

**Files:** none

- [ ] **Step 1: Push the branch**

```bash
git push -u origin feat/280-onboarding-screens
```

- [ ] **Step 2: Confirm labels and milestone with the user**

Per project memory: "If PR resolves an issue, propose inheriting its labels and milestone — ask user to confirm." Issue #280 has labels `feat`, `ios`, `ui` and milestone `M23 · TestFlight V1`. Ask the user:

> "Issue #280 has labels `feat`, `ios`, `ui` and milestone `M23 · TestFlight V1`. Inherit those for the PR?"

Wait for confirmation. Do not proceed with `gh pr create` until labels and milestone are confirmed.

- [ ] **Step 3: Open the PR**

```bash
gh pr create \
  --title "feat(ios): onboarding screens (#280)" \
  --label feat --label ios --label ui \
  --milestone "M23 · TestFlight V1" \
  --body "$(cat <<'EOF'
Resolves #280.

## Summary
- Adds a 4-step Apple-Tips-style onboarding flow under `apps/ios/Pebbles/Features/Onboarding/`.
- Screen content lives in a static `OnboardingSteps.all` config — fully dissociated from the views (`OnboardingPageView`, `OnboardingView`).
- `OnboardingImage` enum supports `.asset(_)` and `.remote(_)` so artwork can migrate from bundled placeholders to remote URLs without view changes.
- `RootView` presents the flow as a `.fullScreenCover` once per device after first signin, gated by `@AppStorage("hasSeenOnboarding")`.
- `PathView` toolbar `info.circle` button replays the flow on demand (without affecting the persistence flag).

## Files changed
- `Pebbles/Features/Onboarding/{OnboardingImage,OnboardingStep,OnboardingSteps,OnboardingPageView,OnboardingView}.swift`
- `Pebbles/Resources/Assets.xcassets/Onboarding{Intro,Concept,Qualify,Carving}.imageset/`
- `Pebbles/RootView.swift` — `@AppStorage` + `.fullScreenCover` + `.onChange` on session user id
- `Pebbles/Features/Path/PathView.swift` — toolbar info button + `.fullScreenCover`
- `PebblesTests/Features/Onboarding/OnboardingStepsTests.swift` — config invariants (5 tests)
- `docs/arkaik/bundle.json` — onboarding nodes realigned to the iOS 4-screen flow
- `docs/superpowers/specs/2026-04-18-onboarding-screens-design.md` — design spec

## Implementation notes
- Image sets ship with 1×1 transparent PNG placeholders. Final artwork lands by replacing `placeholder.png` in each image set — no Swift changes required.
- `OnboardingView` is preview-friendly: takes `steps` + `onFinish`, knows nothing about `@AppStorage`.
- Replay path (`PathView`) deliberately does not touch the persistence flag.

## Test plan
- [ ] On a simulator with `hasSeenOnboarding` unset, sign up → onboarding fullScreenCover appears over the tab bar.
- [ ] Page dots reflect position; swipe between all 4 pages.
- [ ] X / Skip / "Start your path" all dismiss; flag is set.
- [ ] Sign out → sign back in → onboarding does NOT re-show.
- [ ] Tap `info.circle` in Path nav bar → onboarding re-presents.
- [ ] Dismissing the replay does NOT change behavior on subsequent signins.
- [ ] `npm run build --workspace=@pbbls/ios` passes.
- [ ] `npm run lint --workspace=@pbbls/ios` passes.
- [ ] `npm run test --workspace=@pbbls/ios` passes (incl. `OnboardingSteps` suite, 5 tests).

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Expected: prints the PR URL.

- [ ] **Step 4: Report the PR URL back to the user.**

---

## Self-review notes (for the engineer reading this)

- The spec and plan share one source of truth for screen copy: `OnboardingSteps.all`. If you spot a mismatch between the issue's copy and the config in Task 2, the **issue text wins** — update Task 2's source code, not the spec.
- The `.onChange(of: supabase.session?.user.id)` modifier in Task 6 fires on the first delivery from `authStateChanges` (nil → user id), which covers both fresh signup and "already-signed-in user opens the app for the first time after installing this version." If you observe the cover not firing for an existing-session user, double-check the modifier is on a parent of `MainTabView` (not on `MainTabView` itself), so it stays mounted across the auth-gate branch switch.
- Tasks 4, 5, 6, 7 each add a Swift file; XcodeGen needs to regenerate after Tasks 4 and 5 specifically because they introduce new files in folders that don't yet exist when the project file was last generated. Tasks 6 and 7 only modify existing files, so no regeneration is needed.
- If `npm run test` fails at Task 2 step 3 with anything other than "cannot find 'OnboardingSteps' in scope," stop and investigate — it likely means the test file isn't being picked up by XcodeGen. Re-running `npm run generate --workspace=@pbbls/ios` and confirming `PebblesTests/Features/Onboarding/OnboardingStepsTests.swift` exists are the first checks.
