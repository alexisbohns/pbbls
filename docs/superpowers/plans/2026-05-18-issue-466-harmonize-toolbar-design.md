# Issue #466 — Harmonize top toolbar design (iOS) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply one consistent toolbar treatment across every iOS sheet/screen — native (Liquid Glass) buttons with `system.secondary` labels and no custom background, and a centered title in the `meta` typo token / `system.secondary`.

**Architecture:** Add two thin Theme helpers — `PebbleToolbarButton` (a `Button` wrapper that pins `.tint(Color.system.secondary)` to override the ambient accent tint set by `pebblesScreen()`) and `.pebblesToolbarTitle(_:)` (a view modifier that injects a `.principal` `ToolbarItem` with a `pebblesFont(.meta)` text). Migrate every existing toolbar to use them, and strip the chip background from `PebblePrivacyBadge.chip`.

**Tech Stack:** SwiftUI (iOS 17+), `@Observable`, `pebblesFont(.meta)`, `Color.system.secondary`. No new dependencies. No tests — pure styling change; verified by a manual simulator sweep (English + French, light + dark, default + Large dynamic type).

**Spec:** `docs/superpowers/specs/2026-05-18-issue-466-harmonize-toolbar-design.md`.

**Branch:** Work happens on `quality/466-harmonize-toolbar-design` (already created and the spec is committed there).

---

## File map

**Created:**
- `apps/ios/Pebbles/Theme/PebbleToolbarButton.swift` — `Button` wrapper, pins tint to `system.secondary`.
- `apps/ios/Pebbles/Theme/PebblesToolbarTitle.swift` — `.pebblesToolbarTitle(_:)` view modifier with `LocalizedStringKey` and `String` overloads.

**Modified (button + title migration):**
- `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift`
- `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`
- `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`
- `apps/ios/Pebbles/Features/Path/EmotionPickerSheet.swift`
- `apps/ios/Pebbles/Features/Path/SoulPickerSheet.swift`
- `apps/ios/Pebbles/Features/Path/ValencePickerSheet.swift`
- `apps/ios/Pebbles/Features/Profile/ProfileView.swift`
- `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift`
- `apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift`
- `apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift`
- `apps/ios/Pebbles/Features/Profile/Sheets/CreateCollectionSheet.swift`
- `apps/ios/Pebbles/Features/Profile/Sheets/EditCollectionSheet.swift`
- `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift`
- `apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift`
- `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift`
- `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift`
- `apps/ios/Pebbles/Features/Glyph/Views/GlyphCarveSheet.swift`
- `apps/ios/Pebbles/Features/Glyph/Views/GlyphPickerSheet.swift`
- `apps/ios/Pebbles/Features/Glyph/Views/GlyphsListView.swift`
- `apps/ios/Pebbles/Features/Lab/LabView.swift`
- `apps/ios/Pebbles/Features/Lab/Views/LogListView.swift`
- `apps/ios/Pebbles/Features/Onboarding/OnboardingView.swift`

**Modified (chip-bg strip):**
- `apps/ios/Pebbles/Features/Path/Read/PebblePrivacyBadge.swift`

**Regenerated:**
- `apps/ios/Pebbles.xcodeproj` (git-ignored; regenerated via `xcodegen` after the two new files are added).

---

## Task 1: Add `PebbleToolbarButton` helper

**Files:**
- Create: `apps/ios/Pebbles/Theme/PebbleToolbarButton.swift`

- [ ] **Step 1: Create the helper file**

Write `apps/ios/Pebbles/Theme/PebbleToolbarButton.swift`:

```swift
import SwiftUI

/// Toolbar button wrapper that renders as a native iOS button (Liquid Glass
/// capsule on iOS 26) with its label color pinned to `system.secondary`.
///
/// Exists so that toolbar buttons render in the branded "secondary" color
/// rather than the ambient accent set by `pebblesScreen()`, and so the rule
/// has one grep target. Adds no custom shape, padding, or `.buttonStyle` —
/// the system renders the capsule.
struct PebbleToolbarButton<Label: View>: View {
    let role: ButtonRole?
    let action: () -> Void
    @ViewBuilder let label: () -> Label

    init(role: ButtonRole? = nil,
         action: @escaping () -> Void,
         @ViewBuilder label: @escaping () -> Label) {
        self.role = role
        self.action = action
        self.label = label
    }

    var body: some View {
        Button(role: role, action: action) { label() }
            .tint(Color.system.secondary)
    }
}

extension PebbleToolbarButton where Label == Text {
    /// Convenience for the common `PebbleToolbarButton("Cancel") { … }` case.
    init(_ titleKey: LocalizedStringKey,
         role: ButtonRole? = nil,
         action: @escaping () -> Void) {
        self.init(role: role, action: action) { Text(titleKey) }
    }
}

#Preview {
    NavigationStack {
        Color.system.background
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    PebbleToolbarButton("Cancel") {}
                }
                ToolbarItem(placement: .confirmationAction) {
                    PebbleToolbarButton("Save") {}
                }
            }
    }
}
```

- [ ] **Step 2: Regenerate the Xcode project**

Run: `npm run generate --workspace=@pbbls/ios`
Expected: `xcodegen` succeeds; `apps/ios/Pebbles.xcodeproj` is updated to include the new file.

- [ ] **Step 3: Build to verify it compiles**

Run: `npm run build --workspace=@pbbls/ios`
Expected: build succeeds; the `PebbleToolbarButton` preview compiles.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Theme/PebbleToolbarButton.swift
git commit -m "feat(ios): add PebbleToolbarButton helper"
```

---

## Task 2: Add `.pebblesToolbarTitle(_:)` modifier

**Files:**
- Create: `apps/ios/Pebbles/Theme/PebblesToolbarTitle.swift`

- [ ] **Step 1: Create the modifier file**

Write `apps/ios/Pebbles/Theme/PebblesToolbarTitle.swift`:

```swift
import SwiftUI

/// Adds a centered toolbar title rendered in the Pebbles `meta` typography
/// token (uppercase, SF Compact Rounded, 12pt) and `system.secondary` color.
///
/// Coexists with `.navigationTitle(...)` so VoiceOver and the back stack still
/// see the page name; the system inline title slot is taken by this
/// `.principal` `ToolbarItem`.
///
/// Apply inside a `NavigationStack` alongside the screen's own `.toolbar`
/// modifier (SwiftUI merges them).
extension View {
    /// `LocalizedStringKey` overload — for hard-coded titles like "Settings".
    func pebblesToolbarTitle(_ title: LocalizedStringKey) -> some View {
        self
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text(title)
                        .pebblesFont(.meta)
                        .foregroundStyle(Color.system.secondary)
                }
            }
    }

    /// `String` overload — for titles sourced from user data (soul/collection
    /// names, dynamic log titles) that arrive as plain `String`.
    func pebblesToolbarTitle(_ title: String) -> some View {
        self
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text(title)
                        .pebblesFont(.meta)
                        .foregroundStyle(Color.system.secondary)
                }
            }
    }
}

#Preview {
    NavigationStack {
        Color.system.background
            .pebblesToolbarTitle("Preview title")
    }
}
```

- [ ] **Step 2: Regenerate the Xcode project**

Run: `npm run generate --workspace=@pbbls/ios`
Expected: `xcodegen` succeeds.

- [ ] **Step 3: Build to verify it compiles**

Run: `npm run build --workspace=@pbbls/ios`
Expected: build succeeds.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Theme/PebblesToolbarTitle.swift
git commit -m "feat(ios): add pebblesToolbarTitle view modifier"
```

---

## Task 3: Strip the chip background from `PebblePrivacyBadge`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Read/PebblePrivacyBadge.swift:51-60`

- [ ] **Step 1: Update `chipBody`**

Replace the existing `chipBody` (lines 51–60) with:

```swift
    private var chipBody: some View {
        Image(systemName: "lock.fill")
            .font(.system(size: 14, weight: .medium))
            .foregroundStyle(Color.system.secondary)
            .frame(width: 36, height: 36)
            .accessibilityLabel(accessibilityLabelText)
    }
```

Remove the `.background(Circle().fill(Color.system.background.opacity(0.85)))` line. Recolor `lock.fill` from `Color.system.foreground` to `Color.system.secondary`. The `.capsule` style is unchanged.

- [ ] **Step 2: Build to verify it compiles**

Run: `npm run build --workspace=@pbbls/ios`
Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Read/PebblePrivacyBadge.swift
git commit -m "quality(ios): drop chip background on PebblePrivacyBadge"
```

---

## Task 4: Migrate `PebbleDetailSheet`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift:26-56`

This sheet has the off-spec custom "Edit" capsule and uses no centered title today. Add a `.pebblesToolbarTitle(detail?.name ?? "")` so the pebble's name appears centered once `detail` resolves.

- [ ] **Step 1: Replace the body's toolbar and title chain**

Replace lines 26–56 with:

```swift
    var body: some View {
        NavigationStack {
            content
                .pebblesToolbarTitle(detail?.name ?? "")
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        if let detail {
                            PebblePrivacyBadge(visibility: detail.visibility, style: .chip)
                        }
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        PebbleToolbarButton("Edit") {
                            isPresentingEdit = true
                        }
                        .disabled(detail == nil)
                    }
                }
                .pebblesScreen()
                .toolbarBackground(.hidden, for: .navigationBar)
        }
        .task { await load() }
        .sheet(isPresented: $isPresentingEdit) {
            EditPebbleSheet(pebbleId: pebbleId, onSaved: {
                Task { await load() }
                onPebbleUpdated?()
            })
        }
    }
```

What changed:
- Removed `.navigationBarTitleDisplayMode(.inline)` (the modifier sets it).
- Added `.pebblesToolbarTitle(detail?.name ?? "")`.
- Replaced the entire custom-styled `Button { Text("Edit").font(...).background(Capsule()...) }` with `PebbleToolbarButton("Edit") { isPresentingEdit = true }`.
- Removed the now-unused `.buttonStyle(.plain)`.

- [ ] **Step 2: Build to verify it compiles**

Run: `npm run build --workspace=@pbbls/ios`
Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift
git commit -m "quality(ios): adopt harmonized toolbar in PebbleDetailSheet"
```

---

## Task 5: Migrate Path sheets (Create, Edit, Emotion, Soul, Valence)

Each of these uses the standard `Button("Cancel") { … }` pattern with `.navigationTitle(...)` + `.navigationBarTitleDisplayMode(.inline)`. The migration is identical per file: title chain → modifier; each `Button("X") { … }` → `PebbleToolbarButton("X") { … }`.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift:30-52`
- Modify: `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift:48-70`
- Modify: `apps/ios/Pebbles/Features/Path/EmotionPickerSheet.swift:45-60`
- Modify: `apps/ios/Pebbles/Features/Path/SoulPickerSheet.swift:32-50`
- Modify: `apps/ios/Pebbles/Features/Path/ValencePickerSheet.swift:24-40`

- [ ] **Step 1: `CreatePebbleSheet` — update the body**

Replace lines 30–52 with:

```swift
    var body: some View {
        NavigationStack {
            content
                .pebblesToolbarTitle("New pebble")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        PebbleToolbarButton("Cancel") {
                            Task { await cancelAndCleanup() }
                        }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        if isSaving {
                            ProgressView()
                        } else {
                            PebbleToolbarButton("Save") {
                                Task { await save() }
                            }
                            .disabled(!draft.isValid)
                        }
                    }
                }
                .pebblesScreen()
        }
```

- [ ] **Step 2: `EditPebbleSheet` — update the body**

Replace lines 48–70 with:

```swift
    var body: some View {
        NavigationStack {
            content
                .pebblesToolbarTitle("Edit pebble")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        PebbleToolbarButton("Cancel") {
                            Task { await cancelAndCleanup() }
                        }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        if isSaving {
                            ProgressView()
                        } else {
                            PebbleToolbarButton("Save") {
                                Task { await save() }
                            }
                            .disabled(!draft.isValid || isLoading)
                        }
                    }
                }
                .pebblesScreen()
        }
```

- [ ] **Step 3: `EmotionPickerSheet` — update title + buttons**

Open `EmotionPickerSheet.swift`. Find the `.navigationTitle("Emotions")` + `.navigationBarTitleDisplayMode(.inline)` lines around line 48 and:

- Replace those two lines with `.pebblesToolbarTitle("Emotions")`.
- In the toolbar block, replace `Button("Cancel") { … }` with `PebbleToolbarButton("Cancel") { … }` and `Button("Done") { … }` (or whichever is in the confirmation slot — keep its existing closure body and `.disabled(...)` modifiers) with `PebbleToolbarButton("Done") { … }`.

- [ ] **Step 4: `SoulPickerSheet` — update title + buttons**

Same shape: replace `.navigationTitle("Choose souls")` + `.navigationBarTitleDisplayMode(.inline)` with `.pebblesToolbarTitle("Choose souls")`. Swap each `Button` in the toolbar for `PebbleToolbarButton`, preserving roles, closures, and `.disabled(...)` chains.

- [ ] **Step 5: `ValencePickerSheet` — update title + buttons**

Same shape: replace `.navigationTitle("Choose a valence")` + `.navigationBarTitleDisplayMode(.inline)` with `.pebblesToolbarTitle("Choose a valence")`. Swap the toolbar `Button` for `PebbleToolbarButton`.

- [ ] **Step 6: Build to verify all five compile**

Run: `npm run build --workspace=@pbbls/ios`
Expected: build succeeds.

- [ ] **Step 7: Commit**

```bash
git add apps/ios/Pebbles/Features/Path
git commit -m "quality(ios): adopt harmonized toolbar in Path sheets"
```

---

## Task 6: Migrate Profile sheets (Settings, CreateSoul, EditSoul, CreateCollection, EditCollection)

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/CreateCollectionSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/EditCollectionSheet.swift`

For each file, perform the same two changes:

1. Replace the consecutive lines:
   ```swift
   .navigationTitle("<title>")
   .navigationBarTitleDisplayMode(.inline)
   ```
   with:
   ```swift
   .pebblesToolbarTitle("<title>")
   ```
2. In every `ToolbarItem(placement: .cancellationAction) { Button("Cancel") { … } }` / `.confirmationAction { Button("Save") { … } .disabled(...) }` block, swap `Button` for `PebbleToolbarButton`. Preserve the action closure and any `.disabled(...)`. Do **not** touch `ToolbarItemGroup(placement: .keyboard)` — those input accessories stay native.

Title strings to preserve per file:
- `SettingsSheet`: `"Settings"`
- `CreateSoulSheet`: `"New soul"`
- `EditSoulSheet`: `"Edit soul"`
- `CreateCollectionSheet`: `"New collection"`
- `EditCollectionSheet`: `"Edit collection"`

- [ ] **Step 1: `SettingsSheet` — migrate**

Apply the two-step rewrite above. Title is `"Settings"`. Leave the keyboard `ToolbarItemGroup` alone.

- [ ] **Step 2: `CreateSoulSheet` — migrate**

Apply the two-step rewrite. Title `"New soul"`.

- [ ] **Step 3: `EditSoulSheet` — migrate**

Apply the two-step rewrite. Title `"Edit soul"`.

- [ ] **Step 4: `CreateCollectionSheet` — migrate**

Apply the two-step rewrite. Title `"New collection"`.

- [ ] **Step 5: `EditCollectionSheet` — migrate**

Apply the two-step rewrite. Title `"Edit collection"`.

- [ ] **Step 6: Build to verify all five compile**

Run: `npm run build --workspace=@pbbls/ios`
Expected: build succeeds.

- [ ] **Step 7: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Sheets
git commit -m "quality(ios): adopt harmonized toolbar in Profile sheets"
```

---

## Task 7: Migrate Glyph sheets and list

**Files:**
- Modify: `apps/ios/Pebbles/Features/Glyph/Views/GlyphCarveSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Glyph/Views/GlyphPickerSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Glyph/Views/GlyphsListView.swift`

- [ ] **Step 1: `GlyphCarveSheet` — migrate**

Apply the same two-step rewrite (title chain → modifier, `Button` → `PebbleToolbarButton`). Title `"New glyph"`. Leave the keyboard `ToolbarItemGroup` alone.

- [ ] **Step 2: `GlyphPickerSheet` — migrate**

Apply the two-step rewrite. Title `"Choose a glyph"`.

- [ ] **Step 3: `GlyphsListView` — migrate**

Apply the two-step rewrite. Title `"Glyphs"`. The `ToolbarItem(placement: .primaryAction)` add button — typically `Button { … } label: { Image(systemName: "plus") }` — becomes:

```swift
PebbleToolbarButton(action: { … }) {
    Image(systemName: "plus")
}
```

(Use the closure-label initializer when the label is an `Image`, not text.)

- [ ] **Step 4: Build to verify all three compile**

Run: `npm run build --workspace=@pbbls/ios`
Expected: build succeeds.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Glyph
git commit -m "quality(ios): adopt harmonized toolbar in Glyph screens"
```

---

## Task 8: Migrate Profile lists and detail views

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/ProfileView.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift`

- [ ] **Step 1: `ProfileView` — migrate**

Apply the two-step rewrite. Title `"Profile"`. The trailing settings button — typically `Button { … } label: { Image(systemName: "gearshape") }` (or similar) — becomes `PebbleToolbarButton(action: { … }) { Image(systemName: "gearshape") }`.

- [ ] **Step 2: `SoulsListView` — migrate**

Apply the two-step rewrite. Title `"Souls"`. Convert the `.primaryAction` add button to `PebbleToolbarButton` with the image label form.

- [ ] **Step 3: `CollectionsListView` — migrate**

Same as `SoulsListView`. Title `"Collections"`.

- [ ] **Step 4: `SoulDetailView` — migrate (dynamic title)**

Replace:
```swift
.navigationTitle(soulWithGlyph.name)
.navigationBarTitleDisplayMode(.inline)
```
with:
```swift
.pebblesToolbarTitle(soulWithGlyph.name)
```
(Resolves to the `String` overload because `name` is `String`.) Convert the `.primaryAction` `Button` to `PebbleToolbarButton`, preserving its label and closure.

- [ ] **Step 5: `CollectionDetailView` — migrate (dynamic title)**

Replace:
```swift
.navigationTitle(collection.name)
.navigationBarTitleDisplayMode(.inline)
```
with:
```swift
.pebblesToolbarTitle(collection.name)
```
Convert the `.primaryAction` `Button` to `PebbleToolbarButton`.

- [ ] **Step 6: Build to verify all five compile**

Run: `npm run build --workspace=@pbbls/ios`
Expected: build succeeds.

- [ ] **Step 7: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile
git commit -m "quality(ios): adopt harmonized toolbar in Profile screens"
```

---

## Task 9: Migrate Lab screens

**Files:**
- Modify: `apps/ios/Pebbles/Features/Lab/LabView.swift`
- Modify: `apps/ios/Pebbles/Features/Lab/Views/LogListView.swift`

- [ ] **Step 1: `LabView` — migrate title**

Replace the consecutive `.navigationTitle("Lab")` + (any) `.navigationBarTitleDisplayMode(...)` with `.pebblesToolbarTitle("Lab")`. There are no toolbar buttons in `LabView` to swap; if there are any added later, leave them out of scope for this commit.

- [ ] **Step 2: `LogListView` — migrate dynamic title**

Replace:
```swift
.navigationTitle(title)
```
(and the adjacent `.navigationBarTitleDisplayMode(.inline)` if present) with:
```swift
.pebblesToolbarTitle(title)
```
`title` is a `String` (computed property), so this resolves to the `String` overload.

- [ ] **Step 3: Build to verify**

Run: `npm run build --workspace=@pbbls/ios`
Expected: build succeeds.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Lab
git commit -m "quality(ios): adopt harmonized toolbar in Lab screens"
```

---

## Task 10: Migrate Onboarding toolbar buttons

**Files:**
- Modify: `apps/ios/Pebbles/Features/Onboarding/OnboardingView.swift:38-50`

Onboarding has leading + trailing toolbar buttons but intentionally **no title** — do not add `.pebblesToolbarTitle(...)`. Only swap the two `Button` calls for `PebbleToolbarButton`.

- [ ] **Step 1: Swap the toolbar buttons**

In the existing toolbar block, replace:
```swift
ToolbarItem(placement: .topBarLeading) {
    Button { … } label: { … }
}
ToolbarItem(placement: .topBarTrailing) {
    Button { … } label: { … }
}
```
with the equivalent `PebbleToolbarButton(action: { … }) { … }` calls, preserving each existing action closure and label content (text or image) verbatim. Do not adjust any other modifiers on the view.

- [ ] **Step 2: Build to verify**

Run: `npm run build --workspace=@pbbls/ios`
Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Onboarding/OnboardingView.swift
git commit -m "quality(ios): adopt harmonized toolbar buttons in Onboarding"
```

---

## Task 11: Final lint + grep for stragglers

- [ ] **Step 1: Run iOS lint**

Run: `npm run lint --workspace=@pbbls/ios`
Expected: no errors. (If SwiftLint flags any of the migrated files, fix in place.)

- [ ] **Step 2: Grep for remaining off-spec patterns**

Run:
```bash
grep -rn 'Button("[A-Z]' apps/ios/Pebbles/Features --include='*.swift'
grep -rn 'navigationTitle' apps/ios/Pebbles/Features --include='*.swift'
grep -rn 'navigationBarTitleDisplayMode' apps/ios/Pebbles/Features --include='*.swift'
```
Expected:
- No `Button("…")` inside `.toolbar { ToolbarItem(...) { … } }` blocks anywhere in `Features/` (verify by eye — keyboard `ToolbarItemGroup`s may still contain `Button` and that is fine).
- No `.navigationTitle` outside `Theme/PebblesToolbarTitle.swift`.
- No `.navigationBarTitleDisplayMode` outside `Theme/PebblesToolbarTitle.swift`, except possibly `Features/Lab/Views/AnnouncementDetailView.swift` which has no title (acceptable — that view is intentionally chrome-less and out of scope for this issue).

If anything else surfaces, swap it inline and amend the previous task's commit (or add a small follow-up commit) before proceeding.

- [ ] **Step 3: Build the full app one more time**

Run: `npm run build --workspace=@pbbls/ios`
Expected: build succeeds.

- [ ] **Step 4 (no commit if nothing changed)**

If steps 2/3 produced any code changes, commit them:

```bash
git add apps/ios
git commit -m "quality(ios): mop up stray toolbar styles"
```

---

## Task 12: Manual visual sweep

This is the testing step. Per the spec there are no unit tests — visual correctness is verified in the simulator.

- [ ] **Step 1: Boot the app in the simulator**

Open the project in Xcode (`open apps/ios/Pebbles.xcodeproj`) and run on iPhone 17 (or whatever the current default simulator is). Sign in if needed.

- [ ] **Step 2: Toolbar button check across screens**

Visit, in this order, and confirm: every toolbar button label is in `system.secondary`, has no custom background, and renders as the native iOS button (Liquid Glass capsule on iOS 26):

1. Path tab → tap the floating + → `CreatePebbleSheet` (Cancel / Save).
2. From a list, tap an existing pebble → `PebbleDetailSheet` (Edit button — confirm the old white capsule is gone; the privacy lock badge on the left has no background).
3. From `PebbleDetailSheet`, tap Edit → `EditPebbleSheet` (Cancel / Save).
4. From `CreatePebbleSheet` or `EditPebbleSheet`, open `EmotionPickerSheet`, `SoulPickerSheet`, and `ValencePickerSheet` in turn (Cancel / Done).
5. Profile tab → settings (Cancel / Done), then `SoulsListView` (+ button), `CollectionsListView` (+ button), `GlyphsListView` (+ button).
6. From souls/collections lists, tap a row → `SoulDetailView` / `CollectionDetailView` (primary action button).
7. From `SoulsListView` + button → `CreateSoulSheet` (Cancel / Save), and from a row → `EditSoulSheet` (Cancel / Save).
8. From `CollectionsListView` + button → `CreateCollectionSheet` (Cancel / Save), and from a row → `EditCollectionSheet` (Cancel / Save).
9. From `GlyphsListView` + button → `GlyphCarveSheet` (Cancel / Save), and from any soul edit → `GlyphPickerSheet` (Cancel).
10. Lab tab → `LabView`, drill into a log → `LogListView`.
11. Sign out and revisit `OnboardingView`.

- [ ] **Step 3: Title check**

On the same tour, verify every centered title renders in the `meta` typo token: uppercase, SF Compact Rounded, 12pt, tracked, in `system.secondary`. The exceptions (no title shown) are `OnboardingView` and `AnnouncementDetailView`.

- [ ] **Step 4: Dark mode + Large dynamic type**

Toggle the simulator to dark mode and re-visit `CreatePebbleSheet`, `PebbleDetailSheet`, `SoulsListView`, `SoulDetailView`. Then set Dynamic Type to "Larger" (Settings → Display → Text Size) and re-visit the same four. Confirm nothing truncates in the principal slot and labels remain legible against the background.

- [ ] **Step 5: French locale**

Set the simulator to French (Settings → General → Language & Region → iPhone Language → Français). Re-launch the app. Re-visit at minimum `CreatePebbleSheet`, `EditPebbleSheet`, `SettingsSheet`, `SoulsListView`. Confirm titles render localized and do not truncate.

- [ ] **Step 6: VoiceOver smoke check**

Enable VoiceOver (Settings → Accessibility → VoiceOver). On `EditPebbleSheet` and `SoulsListView`, confirm VoiceOver announces the page name when the screen appears (sourced from the shadow `.navigationTitle(...)` call inside the modifier).

- [ ] **Step 7: Note any visual regressions**

If anything looks off (text too small for the slot, button label illegible against a particular tab background, etc.), file the issue inline on the PR rather than mutating the helper — the helper is the rule.

---

## Task 13: Open the PR

- [ ] **Step 1: Push the branch**

```bash
git push -u origin quality/466-harmonize-toolbar-design
```

- [ ] **Step 2: Open the PR with inherited labels and milestone from #466**

Issue #466 has labels `ios`, `quality`, `ui` and milestone `M32 · iOS Quality`. The PR inherits these (`quality` matches the PR species rule directly — no `bug` → `fix` swap needed).

```bash
gh pr create \
  --title "quality(ios): harmonize top toolbar design" \
  --label ios --label quality --label ui \
  --milestone "M32 · iOS Quality" \
  --body "$(cat <<'EOF'
Resolves #466.

## Summary

- Adds two Theme helpers — `PebbleToolbarButton` and `.pebblesToolbarTitle(_:)` — that encode the harmonized rule in one place: native iOS button with `system.secondary` label and no custom background; centered title in `pebblesFont(.meta)` / `system.secondary`.
- Migrates every existing toolbar in `Features/` to use them.
- Strips the chip-circle background from `PebblePrivacyBadge.chip` (icon recolored to `system.secondary`).

Spec: `docs/superpowers/specs/2026-05-18-issue-466-harmonize-toolbar-design.md`.

## Key files

- `apps/ios/Pebbles/Theme/PebbleToolbarButton.swift` (new)
- `apps/ios/Pebbles/Theme/PebblesToolbarTitle.swift` (new)
- `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift` (strip custom Edit capsule)
- `apps/ios/Pebbles/Features/Path/Read/PebblePrivacyBadge.swift` (strip chip bg)
- 18 other `Features/**` files migrated to the new helpers

## Test plan

- [ ] `npm run build --workspace=@pbbls/ios` passes
- [ ] `npm run lint --workspace=@pbbls/ios` passes
- [ ] Visual sweep across every migrated sheet in the simulator: light, dark, Large dynamic type, English, French
- [ ] VoiceOver still announces each screen's page name
EOF
)"
```

- [ ] **Step 3: Verify the PR is up**

Run: `gh pr view --web`
Confirm labels (`ios`, `quality`, `ui`) and milestone (`M32 · iOS Quality`) are applied.
