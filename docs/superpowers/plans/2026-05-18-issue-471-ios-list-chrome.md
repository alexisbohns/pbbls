# iOS list chrome harmonization (issue #471) — Implementation Plan

> **⚠ Historical document.** Tasks 1–10 below were executed roughly as written, but the rendering approach diverged during simulator verification. The shipped code uses `.listRowBackground` with stroked closed shapes (not `.overlay` with `.strokeBorder`), and six `Form`-based screens were converted to `List` to bypass an iOS clip mask. **For the actual shipped code patterns, read [the spec](../specs/2026-05-18-issue-471-ios-list-chrome-design.md), not this plan.** The plan is preserved as an execution record; the post-merge commits on `quality/471-ios-list-chrome` document each pivot.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace every iOS `List`/`Form`'s native background with a `Color.system.muted` border at `Spacing.lg` corner radius, per-Section, matching the existing `profileCard()` chrome — across 9 files.

**Architecture (as planned):** One new Theme file (`PebblesList.swift`) introducing `.pebblesList()`, `.pebblesListRow(position:)`, and `Text.pebblesSectionHeader()`. Each list/form in scope applies the modifier and decorates rows with a position-aware enum (`.only`/`.top`/`.middle`/`.bottom`). The border is drawn on a per-row overlay using `UnevenRoundedRectangle` because SwiftUI doesn't expose Section bounds.

**Architecture (as shipped — diverged from plan):** Same theme file, same enum, same call-site decoration pattern. But the border is drawn via `.listRowBackground` (not `.overlay`) with `.stroke` on closed shapes (RoundedRectangle for `.only`, UnevenRoundedRectangle for `.top`/`.bottom`, Rectangle for `.middle`) — `.overlay` wraps row content and produces isolated pill-shapes per row instead of a continuous card. System separators are hidden via `.listRowSeparator(.hidden)` so adjacent rows' overlapping boundary strokes alone provide the interior dividers. All six `Form`-using screens were converted to `List` because `Form` ignores `.listStyle(.plain)` and applies a `UITableViewCell` clip mask that cropped our 17pt corners. `pebblesList()` adds `.listStyle(.plain)` and `.padding(.horizontal, Spacing.lg)`. `PebbleFormView`'s two implicit `Picker`s got explicit `.pickerStyle(.menu)` to preserve menu-style behavior post-conversion.

**Tech Stack:** SwiftUI (iOS 17+), Swift Testing for any unit work (none required here — this is a visual refactor; verification is `xcodebuild build` + simulator). Build via `npm run build --workspace=@pbbls/ios`. Lint via `npm run lint --workspace=@pbbls/ios`. The branch `quality/471-ios-list-chrome` is already created and holds the spec commit.

**TDD note:** This is a pure visual change. SwiftUI view modifiers have no meaningful behavior to assert in unit tests beyond compilation. Each task's verification is therefore (1) `xcodebuild build` succeeds and (2) the relevant Xcode Preview / simulator screen renders correctly. No PebblesTests changes.

**Spec:** `docs/superpowers/specs/2026-05-18-issue-471-ios-list-chrome-design.md`

---

## File Structure

**New:**
- `apps/ios/Pebbles/Theme/PebblesList.swift` — exports `PebblesListRowPosition` enum, `View.pebblesList()`, `View.pebblesListRow(position:)`, `Text.pebblesSectionHeader()`, and free function `pebblesRowPosition(index:count:)`.

**Modified (9 files):**
- `apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift`
- `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift`
- `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift`
- `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift`
- `apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift`
- `apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift`
- `apps/ios/Pebbles/Features/Profile/Sheets/CreateCollectionSheet.swift`
- `apps/ios/Pebbles/Features/Profile/Sheets/EditCollectionSheet.swift`
- `apps/ios/Pebbles/Features/Path/PebbleFormView.swift`

No `project.yml` change — `xcodegen`'s `sources: - path: Pebbles` pulls in any new `.swift` automatically. Run `npm run generate --workspace=@pbbls/ios` once after creating `PebblesList.swift` so the local `.xcodeproj` picks it up.

---

## Task 1: Add the `PebblesList` theme primitive

**Files:**
- Create: `apps/ios/Pebbles/Theme/PebblesList.swift`

- [ ] **Step 1: Create `PebblesList.swift` with the full API**

```swift
import SwiftUI

// MARK: - Row position

/// Where a row sits inside its Section, used to mask the border overlay's
/// corner radii. `.only` is the default for single-row sections.
enum PebblesListRowPosition {
    case only
    case top
    case middle
    case bottom
}

/// Map a `ForEach` index/count pair to a row position.
func pebblesRowPosition(index: Int, count: Int) -> PebblesListRowPosition {
    if count <= 1 { return .only }
    if index == 0 { return .top }
    if index == count - 1 { return .bottom }
    return .middle
}

// MARK: - List/Form chrome

/// Applied to `List` or `Form`: hides the native grouped background,
/// recolors row separators to `system.muted`, and sets `Spacing.lg`
/// between sections so the bordered groups breathe consistently.
extension View {
    func pebblesList() -> some View {
        modifier(PebblesListModifier())
    }
}

private struct PebblesListModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .scrollContentBackground(.hidden)
            .listRowSeparatorTint(Color.system.muted)
            .listSectionSpacing(Spacing.lg)
    }
}

// MARK: - Row chrome

/// Applied to each row inside a Section: clears the native row background
/// and draws the section border's contribution for this row (top/bottom
/// corners rounded according to `position`). Adjacent rows' borders
/// overlap on the shared horizontal edge — visually a single rectangle.
extension View {
    func pebblesListRow(position: PebblesListRowPosition = .only) -> some View {
        modifier(PebblesListRowModifier(position: position))
    }
}

private struct PebblesListRowModifier: ViewModifier {
    let position: PebblesListRowPosition

    func body(content: Content) -> some View {
        content
            .listRowBackground(Color.clear)
            .listRowSeparatorTint(Color.system.muted)
            .overlay(borderOverlay)
    }

    private var borderOverlay: some View {
        let r = Spacing.lg
        let radii: RectangleCornerRadii = {
            switch position {
            case .only:
                return RectangleCornerRadii(topLeading: r, bottomLeading: r, bottomTrailing: r, topTrailing: r)
            case .top:
                return RectangleCornerRadii(topLeading: r, bottomLeading: 0, bottomTrailing: 0, topTrailing: r)
            case .middle:
                return RectangleCornerRadii(topLeading: 0, bottomLeading: 0, bottomTrailing: 0, topTrailing: 0)
            case .bottom:
                return RectangleCornerRadii(topLeading: 0, bottomLeading: r, bottomTrailing: r, topTrailing: 0)
            }
        }()
        return UnevenRoundedRectangle(cornerRadii: radii)
            .strokeBorder(Color.system.muted, lineWidth: 1)
            .allowsHitTesting(false)
    }
}

// MARK: - Section header

/// Section header typography matching profile cards (Stats, Collections):
/// `.pebblesFont(.cardHeading)` (SF Compact Rounded 15 semibold, uppercase,
/// 10% tracking) in `system.secondary`.
extension Text {
    func pebblesSectionHeader() -> some View {
        self
            .pebblesFont(.cardHeading)
            .foregroundStyle(Color.system.secondary)
    }
}

// MARK: - Preview

#Preview("PebblesList chrome") {
    Form {
        Section {
            Text("Single row")
                .pebblesListRow(position: .only)
        } header: {
            Text("Single").pebblesSectionHeader()
        }

        Section {
            Text("Top row").pebblesListRow(position: .top)
            Text("Middle row").pebblesListRow(position: .middle)
            Text("Bottom row").pebblesListRow(position: .bottom)
        } header: {
            Text("Multi-row").pebblesSectionHeader()
        }
    }
    .pebblesList()
}
```

- [ ] **Step 2: Regenerate the Xcode project**

Run: `npm run generate --workspace=@pbbls/ios`
Expected: silent success; `.xcodeproj` updated to include the new file.

- [ ] **Step 3: Build to verify the new file compiles**

Run: `npm run build --workspace=@pbbls/ios`
Expected: `** BUILD SUCCEEDED **`. No errors. Warnings about `AppIcon` are pre-existing and expected.

- [ ] **Step 4: Lint**

Run: `npm run lint --workspace=@pbbls/ios`
Expected: zero violations on the new file.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Theme/PebblesList.swift apps/ios/project.yml apps/ios/Pebbles.xcodeproj 2>/dev/null || git add apps/ios/Pebbles/Theme/PebblesList.swift
git commit -m "quality(ios): add pebblesList theme primitive for issue #471"
```

Note: `.xcodeproj` is gitignored per `apps/ios/CLAUDE.md` — only `PebblesList.swift` and possibly `project.yml` (no change expected) should appear in `git status`.

---

## Task 2: Migrate `CollectionsListView`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift:88-111`

- [ ] **Step 1: Replace the `else { List { … } }` branch**

Current code (lines 87–111):

```swift
} else {
    List {
        ForEach(items) { collection in
            NavigationLink {
                CollectionDetailView(collection: collection, onChanged: {
                    Task {
                        await load()
                        await refs.refreshCollections()
                    }
                })
            } label: {
                CollectionRow(collection: collection)
            }
            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                Button(role: .destructive) {
                    pendingDeletion = collection
                } label: {
                    Label("Delete", systemImage: "trash")
                }
            }
            .listRowBackground(Color.clear)
        }
    }
    .listRowSeparatorTint(Color.system.muted)
}
```

Replace with:

```swift
} else {
    List {
        ForEach(Array(items.enumerated()), id: \.element.id) { index, collection in
            NavigationLink {
                CollectionDetailView(collection: collection, onChanged: {
                    Task {
                        await load()
                        await refs.refreshCollections()
                    }
                })
            } label: {
                CollectionRow(collection: collection)
            }
            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                Button(role: .destructive) {
                    pendingDeletion = collection
                } label: {
                    Label("Delete", systemImage: "trash")
                }
            }
            .pebblesListRow(position: pebblesRowPosition(index: index, count: items.count))
        }
    }
    .pebblesList()
}
```

- [ ] **Step 2: Build**

Run: `npm run build --workspace=@pbbls/ios`
Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Open `CollectionsListView` in simulator and verify**

Launch the app, sign in, open Profile → Collections list. Confirm: clear background, single bordered group containing all collection rows, `system.muted` dividers between rows, swipe-to-delete still works.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift
git commit -m "quality(ios): apply pebblesList chrome to CollectionsListView"
```

---

## Task 3: Migrate `CollectionDetailView`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift:115-139`

- [ ] **Step 1: Replace the `else { List { … } }` branch**

Current code (lines 114–139):

```swift
} else {
    List {
        Section {
            HStack {
                CollectionModeBadge(mode: collection.mode)
                Spacer()
                Text(pebbleCountLabel)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }

        ForEach(groupedPebbles, id: \.key) { group in
            Section(header: Text(Self.monthFormatter.string(from: group.key))) {
                ForEach(group.value) { pebble in
                    PebbleRow(
                        pebble: pebble,
                        onTap: { selectedPebbleId = pebble.id },
                        onDelete: { pendingDeletion = pebble }
                    )
                }
            }
        }
    }
    .listStyle(.insetGrouped)
}
```

Replace with:

```swift
} else {
    List {
        Section {
            HStack {
                CollectionModeBadge(mode: collection.mode)
                Spacer()
                Text(pebbleCountLabel)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .pebblesListRow(position: .only)
        }

        ForEach(groupedPebbles, id: \.key) { group in
            Section {
                ForEach(Array(group.value.enumerated()), id: \.element.id) { index, pebble in
                    PebbleRow(
                        pebble: pebble,
                        onTap: { selectedPebbleId = pebble.id },
                        onDelete: { pendingDeletion = pebble }
                    )
                    .pebblesListRow(position: pebblesRowPosition(index: index, count: group.value.count))
                }
            } header: {
                Text(Self.monthFormatter.string(from: group.key)).pebblesSectionHeader()
            }
        }
    }
    .pebblesList()
}
```

Note: `.listStyle(.insetGrouped)` is removed — `.pebblesList()` inherits the Form/List's natural inset-grouped layout while transparently overriding background and separator color.

- [ ] **Step 2: Build**

Run: `npm run build --workspace=@pbbls/ios`
Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Simulator check**

Open a collection that contains pebbles across multiple months. Verify: header row is its own bordered group; each month is its own bordered card with `system.muted` dividers; month headers render in `cardHeading` typography.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift
git commit -m "quality(ios): apply pebblesList chrome to CollectionDetailView"
```

---

## Task 4: Migrate `SoulDetailView`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift:127-136`

- [ ] **Step 1: Replace the `List { … }`**

Current code (lines 127–136):

```swift
List {
    ForEach(pebbles) { pebble in
        PebbleRow(
            pebble: pebble,
            onTap: { selectedPebbleId = pebble.id },
            onDelete: { pendingDeletion = pebble }
        )
    }
}
```

Replace with:

```swift
List {
    ForEach(Array(pebbles.enumerated()), id: \.element.id) { index, pebble in
        PebbleRow(
            pebble: pebble,
            onTap: { selectedPebbleId = pebble.id },
            onDelete: { pendingDeletion = pebble }
        )
        .pebblesListRow(position: pebblesRowPosition(index: index, count: pebbles.count))
    }
}
.pebblesList()
```

- [ ] **Step 2: Build**

Run: `npm run build --workspace=@pbbls/ios`
Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Simulator check**

Open a soul that has multiple pebbles. Verify: single bordered group containing the pebble list, `system.muted` dividers between rows.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift
git commit -m "quality(ios): apply pebblesList chrome to SoulDetailView"
```

---

## Task 5: Migrate `CreateSoulSheet`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift:23-42` (Form body)

- [ ] **Step 1: Replace the `Form { … }`**

Current code (lines 23–42):

```swift
Form {
    Section {
        TextField("Name", text: $draft.name)
            .textInputAutocapitalization(.words)
            .autocorrectionDisabled(false)
    }
    Section("Glyph") {
        GlyphRow(
            glyph: draft.currentGlyph,
            onTap: { isPresentingPicker = true }
        )
    }
    if let saveError {
        Section {
            Text(saveError)
                .font(.footnote)
                .foregroundStyle(.red)
        }
    }
}
```

Replace with:

```swift
Form {
    Section {
        TextField("Name", text: $draft.name)
            .textInputAutocapitalization(.words)
            .autocorrectionDisabled(false)
            .pebblesListRow(position: .only)
    }
    Section {
        GlyphRow(
            glyph: draft.currentGlyph,
            onTap: { isPresentingPicker = true }
        )
        .pebblesListRow(position: .only)
    } header: {
        Text("Glyph").pebblesSectionHeader()
    }
    if let saveError {
        Section {
            Text(saveError)
                .font(.footnote)
                .foregroundStyle(.red)
                .pebblesListRow(position: .only)
        }
    }
}
.pebblesList()
```

- [ ] **Step 2: Build**

Run: `npm run build --workspace=@pbbls/ios`
Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Simulator check**

Open Profile → Souls → New. Verify: Name field is a bordered single-row card; Glyph section is a bordered single-row card under a `Glyph` header in cardHeading typography. Trigger a save error (e.g. by signing out beforehand) and verify the error row also gets the bordered card treatment.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift
git commit -m "quality(ios): apply pebblesList chrome to CreateSoulSheet"
```

---

## Task 6: Migrate `EditSoulSheet`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift:36-55` (Form body)

- [ ] **Step 1: Replace the `Form { … }`**

Current code (lines 36–55):

```swift
Form {
    Section {
        TextField("Name", text: $draft.name)
            .textInputAutocapitalization(.words)
            .autocorrectionDisabled(false)
    }
    Section("Glyph") {
        GlyphRow(
            glyph: draft.currentGlyph,
            onTap: { isPresentingPicker = true }
        )
    }
    if let saveError {
        Section {
            Text(saveError)
                .font(.footnote)
                .foregroundStyle(.red)
        }
    }
}
```

Replace with:

```swift
Form {
    Section {
        TextField("Name", text: $draft.name)
            .textInputAutocapitalization(.words)
            .autocorrectionDisabled(false)
            .pebblesListRow(position: .only)
    }
    Section {
        GlyphRow(
            glyph: draft.currentGlyph,
            onTap: { isPresentingPicker = true }
        )
        .pebblesListRow(position: .only)
    } header: {
        Text("Glyph").pebblesSectionHeader()
    }
    if let saveError {
        Section {
            Text(saveError)
                .font(.footnote)
                .foregroundStyle(.red)
                .pebblesListRow(position: .only)
        }
    }
}
.pebblesList()
```

- [ ] **Step 2: Build**

Run: `npm run build --workspace=@pbbls/ios`
Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Simulator check**

Open Profile → Souls → tap an existing soul → Edit. Verify same layout as CreateSoulSheet.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift
git commit -m "quality(ios): apply pebblesList chrome to EditSoulSheet"
```

---

## Task 7: Migrate `CreateCollectionSheet`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/CreateCollectionSheet.swift:25-47` (Form body)

- [ ] **Step 1: Replace the `Form { … }`**

Current code (lines 25–47):

```swift
Form {
    Section("Name") {
        TextField("Name", text: $name)
            .textInputAutocapitalization(.words)
            .autocorrectionDisabled(false)
    }
    Section("Mode") {
        Picker("Mode", selection: $mode) {
            Text("None").tag(CollectionMode?.none)
            Text("Stack").tag(CollectionMode?.some(.stack))
            Text("Pack").tag(CollectionMode?.some(.pack))
            Text("Track").tag(CollectionMode?.some(.track))
        }
        .pickerStyle(.segmented)
    }
    if let saveError {
        Section {
            Text(saveError)
                .font(.footnote)
                .foregroundStyle(.red)
        }
    }
}
```

Replace with:

```swift
Form {
    Section {
        TextField("Name", text: $name)
            .textInputAutocapitalization(.words)
            .autocorrectionDisabled(false)
            .pebblesListRow(position: .only)
    } header: {
        Text("Name").pebblesSectionHeader()
    }
    Section {
        Picker("Mode", selection: $mode) {
            Text("None").tag(CollectionMode?.none)
            Text("Stack").tag(CollectionMode?.some(.stack))
            Text("Pack").tag(CollectionMode?.some(.pack))
            Text("Track").tag(CollectionMode?.some(.track))
        }
        .pickerStyle(.segmented)
        .pebblesListRow(position: .only)
    } header: {
        Text("Mode").pebblesSectionHeader()
    }
    if let saveError {
        Section {
            Text(saveError)
                .font(.footnote)
                .foregroundStyle(.red)
                .pebblesListRow(position: .only)
        }
    }
}
.pebblesList()
```

- [ ] **Step 2: Build**

Run: `npm run build --workspace=@pbbls/ios`
Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Simulator check**

Open Profile → Collections → New. Verify Name field and Mode segmented picker each in their own bordered card; headers in cardHeading typography. Confirm segmented picker still selects normally.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Sheets/CreateCollectionSheet.swift
git commit -m "quality(ios): apply pebblesList chrome to CreateCollectionSheet"
```

---

## Task 8: Migrate `EditCollectionSheet`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/EditCollectionSheet.swift:38-…` (Form body — read file first to confirm exact range)

- [ ] **Step 1: Read the file to confirm current Form body**

Run: `Read apps/ios/Pebbles/Features/Profile/Sheets/EditCollectionSheet.swift`

Expected structure mirrors `CreateCollectionSheet` (Name section, Mode section, optional error section). If the structure differs materially (e.g. additional sections), adapt the changes below to cover every section with `.pebblesListRow(position: .only)` (or the position helper for multi-row sections) and swap any `Section("Title")` for the `header:` form with `pebblesSectionHeader()`.

- [ ] **Step 2: Apply the same chrome as Task 7**

For each `Section`:
- If single-row → wrap the row's content in `.pebblesListRow(position: .only)`.
- If labeled (`Section("Title")`) → switch to `Section { … } header: { Text("Title").pebblesSectionHeader() }`.
- Error section → `.pebblesListRow(position: .only)`, no header.

Add `.pebblesList()` to the `Form`.

- [ ] **Step 3: Build**

Run: `npm run build --workspace=@pbbls/ios`
Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 4: Simulator check**

Open Profile → Collections → tap an existing collection → Edit. Verify the same bordered card layout as CreateCollectionSheet.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Sheets/EditCollectionSheet.swift
git commit -m "quality(ios): apply pebblesList chrome to EditCollectionSheet"
```

---

## Task 9: Migrate `SettingsSheet`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift:65-81` (Form body) and section helpers below

- [ ] **Step 1: Add `.pebblesList()` to the Form**

Find:

```swift
Form {
    headerSection
    informationsSection
    if isSSO {
        providersSection
    } else {
        passwordSection
    }
    if let saveError {
        Section {
            Text(saveError)
                .font(.footnote)
                .foregroundStyle(.red)
        }
    }
    legalSection
}
.scrollDismissesKeyboard(.interactively)
```

Replace with (only the trailing modifier changes — append `.pebblesList()` after `Form { … }`):

```swift
Form {
    headerSection
    informationsSection
    if isSSO {
        providersSection
    } else {
        passwordSection
    }
    if let saveError {
        Section {
            Text(saveError)
                .font(.footnote)
                .foregroundStyle(.red)
                .pebblesListRow(position: .only)
        }
    }
    legalSection
}
.pebblesList()
.scrollDismissesKeyboard(.interactively)
```

- [ ] **Step 2: Update `headerSection` (single glyph button row)**

Find:

```swift
private var headerSection: some View {
    Section {
        Button {
            isPresentingGlyphPicker = true
        } label: {
            HStack {
                Spacer()
                glyphView
                Spacer()
            }
            .padding(.vertical, 8)
        }
        .buttonStyle(.plain)
        .listRowBackground(Color.clear)
    }
}
```

Replace `.listRowBackground(Color.clear)` with `.pebblesListRow(position: .only)`:

```swift
private var headerSection: some View {
    Section {
        Button {
            isPresentingGlyphPicker = true
        } label: {
            HStack {
                Spacer()
                glyphView
                Spacer()
            }
            .padding(.vertical, 8)
        }
        .buttonStyle(.plain)
        .pebblesListRow(position: .only)
    }
}
```

- [ ] **Step 3: Update `informationsSection`, `passwordSection`, `providersSection`, `legalSection`**

Read each section helper. For each one:

1. If the `Section` uses a string label (`Section("Title")`), switch to `Section { … } header: { Text("Title").pebblesSectionHeader() }`.
2. For every row inside the section, append `.pebblesListRow(position: …)` with the position computed by hand:
   - Single row → `.only`
   - First of N → `.top`, intermediate → `.middle`, last → `.bottom`
3. Remove any pre-existing `.listRowBackground(Color.clear)` calls (now covered by `.pebblesListRow`).

If a section's row count is dynamic (e.g. `providersSection` enumerates `linkedProviders`), use `ForEach(Array(items.enumerated()), id: \.element.id)` and `pebblesRowPosition(index: index, count: items.count)`.

- [ ] **Step 4: Build**

Run: `npm run build --workspace=@pbbls/ios`
Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 5: Simulator check**

Open Profile → Settings. Verify every section reads as a bordered card. Test both the SSO path (sign in via Apple/Google) and the password path. Verify the glyph header section is a single bordered card, informations/password/providers each have their own bordered group with the new header typography, and the legal section renders correctly. Force a save error (e.g. submit while offline) and confirm the error row appears as a single bordered card.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift
git commit -m "quality(ios): apply pebblesList chrome to SettingsSheet"
```

---

## Task 10: Migrate `PebbleFormView`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PebbleFormView.swift:102-308`

This is the largest migration. The render section at the top stays borderless (intentional edge-to-edge artwork).

- [ ] **Step 1: Add `.pebblesList()` and drop the file-level separator-tint**

At line 308:

```swift
}
.listRowSeparatorTint(Color.system.muted)
.sheet(isPresented: $showPicker) { … }
```

Replace with:

```swift
}
.pebblesList()
.sheet(isPresented: $showPicker) { … }
```

- [ ] **Step 2: Render section — keep borderless**

Lines 103–111 (the `if let svg = renderSvg` block). No change — `.listRowInsets(EdgeInsets())` and `.listRowBackground(Color.clear)` stay as-is. Do NOT add `.pebblesListRow` here.

- [ ] **Step 3: When / Name / Description section (3 rows)**

Find (lines 113–128):

```swift
Section {
    DatePicker(
        "When",
        selection: $draft.happenedAt,
        displayedComponents: [.date, .hourAndMinute]
    )
    .tint(Color.accent.primary)
    .listRowBackground(Color.clear)

    TextField("Name", text: $draft.name)
        .listRowBackground(Color.clear)

    TextField("Description (optional)", text: $draft.description, axis: .vertical)
        .lineLimit(1...5)
        .listRowBackground(Color.clear)
}
```

Replace with:

```swift
Section {
    DatePicker(
        "When",
        selection: $draft.happenedAt,
        displayedComponents: [.date, .hourAndMinute]
    )
    .tint(Color.accent.primary)
    .pebblesListRow(position: .top)

    TextField("Name", text: $draft.name)
        .pebblesListRow(position: .middle)

    TextField("Description (optional)", text: $draft.description, axis: .vertical)
        .lineLimit(1...5)
        .pebblesListRow(position: .bottom)
}
```

- [ ] **Step 4: Mood section (3 rows: Emotion / Domain / Valence)**

Find (lines 130–215):

```swift
Section("Mood") {
    Button { showEmotionPicker = true } label: { … }
        // … existing modifiers …
        .listRowBackground(Color.clear)

    Picker("Domain", selection: $draft.domainId) { … }
        .listRowBackground(Color.clear)

    Button { showValencePicker = true } label: { … }
        // … existing modifiers …
        .listRowBackground(Color.clear)
}
```

Replace the section wrapper and per-row modifier:

```swift
Section {
    Button { showEmotionPicker = true } label: { … }
        // … existing modifiers …
        .pebblesListRow(position: .top)

    Picker("Domain", selection: $draft.domainId) { … }
        .pebblesListRow(position: .middle)

    Button { showValencePicker = true } label: { … }
        // … existing modifiers …
        .pebblesListRow(position: .bottom)
} header: {
    Text("Mood").pebblesSectionHeader()
}
```

Keep all the existing button labels, accessibility modifiers, and inner views verbatim — only the section wrapper (`Section("Mood")` → `Section { … } header: { … }`) and the trailing `.listRowBackground(Color.clear)` → `.pebblesListRow(position: …)` swap changes.

- [ ] **Step 5: Glyph section (1 row)**

Find (lines 217–250):

```swift
Section("Glyph") {
    Button { showPicker = true } label: { … }
        // … existing modifiers …
        .listRowBackground(Color.clear)
}
```

Replace with:

```swift
Section {
    Button { showPicker = true } label: { … }
        // … existing modifiers …
        .pebblesListRow(position: .only)
} header: {
    Text("Glyph").pebblesSectionHeader()
}
```

- [ ] **Step 6: Souls section (1 row)**

Find (lines 252–259):

```swift
Section("Souls") {
    SelectedSoulsRow(
        soulIds: $draft.soulIds,
        allSouls: souls
    )
    .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
    .listRowBackground(Color.clear)
}
```

Replace with:

```swift
Section {
    SelectedSoulsRow(
        soulIds: $draft.soulIds,
        allSouls: souls
    )
    .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
    .pebblesListRow(position: .only)
} header: {
    Text("Souls").pebblesSectionHeader()
}
```

`.listRowInsets` is preserved because `SelectedSoulsRow` needs the custom insets.

- [ ] **Step 7: Optional section (1 row)**

Find (lines 261–269):

```swift
Section("Optional") {
    Picker("Collection", selection: $draft.collectionId) { … }
        .listRowBackground(Color.clear)
}
```

Replace with:

```swift
Section {
    Picker("Collection", selection: $draft.collectionId) { … }
        .pebblesListRow(position: .only)
} header: {
    Text("Optional").pebblesSectionHeader()
}
```

- [ ] **Step 8: Photo section (1 row, variant-dependent)**

Find (lines 271–297):

```swift
if showsPhotoSection {
    Section("Photo") {
        switch formSnap {
        case .none:
            Button { photoPickerPresented = true } label: { … }
                .listRowBackground(Color.clear)
        case .existing(_, let storagePath):
            ExistingSnapRow(…)
                .listRowBackground(Color.clear)
        case .pending(let snap):
            AttachedPhotoView(…)
                .listRowBackground(Color.clear)
        }
    }
}
```

Replace with:

```swift
if showsPhotoSection {
    Section {
        switch formSnap {
        case .none:
            Button { photoPickerPresented = true } label: { … }
                .pebblesListRow(position: .only)
        case .existing(_, let storagePath):
            ExistingSnapRow(…)
                .pebblesListRow(position: .only)
        case .pending(let snap):
            AttachedPhotoView(…)
                .pebblesListRow(position: .only)
        }
    } header: {
        Text("Photo").pebblesSectionHeader()
    }
}
```

- [ ] **Step 9: Error section (1 row, no header)**

Find (lines 299–306):

```swift
if let saveError {
    Section {
        Text(saveError)
            .foregroundStyle(.red)
            .font(.callout)
            .listRowBackground(Color.clear)
    }
}
```

Replace with:

```swift
if let saveError {
    Section {
        Text(saveError)
            .foregroundStyle(.red)
            .font(.callout)
            .pebblesListRow(position: .only)
    }
}
```

- [ ] **Step 10: Build**

Run: `npm run build --workspace=@pbbls/ios`
Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 11: Simulator check**

Open Path → New pebble (and also Edit an existing pebble). Verify:
- Render section at top spans edge-to-edge with no border (unchanged)
- When/Name/Description forms one bordered card with two inner dividers
- Mood forms a bordered card with two inner dividers, header reads "MOOD" in cardHeading typography
- Glyph / Souls / Optional / Photo each render as single bordered cards under cardHeading headers
- Tap through pickers (emotion, valence, domain, collection, glyph) to confirm they still function
- Trigger a validation error (submit without required fields) and confirm the error section appears as a bordered card

- [ ] **Step 12: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PebbleFormView.swift
git commit -m "quality(ios): apply pebblesList chrome to PebbleFormView"
```

---

## Task 11: Full build, lint, and Localizable check

- [ ] **Step 1: Full build from repo root**

Run: `npm run build --workspace=@pbbls/ios`
Expected: `** BUILD SUCCEEDED **`. No new warnings beyond pre-existing AppIcon ones.

- [ ] **Step 2: Lint**

Run: `npm run lint --workspace=@pbbls/ios`
Expected: zero violations.

- [ ] **Step 3: Localizable.xcstrings check**

Per `apps/ios/CLAUDE.md`: open `apps/ios/Pebbles/Resources/Localizable.xcstrings` in Xcode and confirm no entry is `New` or `Stale`. This change introduces no new user-facing strings (only typography on existing section header strings already in the catalog) — confirmation is procedural.

If any string appears as `New` or `Stale`, fill in the missing `en`/`fr` values before opening the PR.

- [ ] **Step 4: Simulator smoke pass**

Walk through every screen modified by tasks 2–10 in both light and dark mode. Confirm:
- All bordered cards render in `system.muted` border in both themes
- Section header typography matches Profile cards (Stats, Collections)
- Row separators are `system.muted`, not the default grey
- Native interactions still work: swipe-to-delete on collections list, keyboard avoidance on forms, sheets dismiss correctly, NavigationLink presses

- [ ] **Step 5: No commit (this task only verifies)**

If issues found, branch the fix into a new commit on the same branch.

---

## Task 12: Open PR

- [ ] **Step 1: Push branch**

Run:
```bash
git push -u origin quality/471-ios-list-chrome
```

- [ ] **Step 2: Open the PR**

Run:
```bash
gh pr create --title "quality(ios): harmonize list and form chrome (#471)" --body "$(cat <<'EOF'
Resolves #471

## Summary
- Introduces `apps/ios/Pebbles/Theme/PebblesList.swift` with `.pebblesList()`, `.pebblesListRow(position:)`, and `Text.pebblesSectionHeader()` — matching the existing `.profileCard()` chrome (border `system.muted`, radius `Spacing.lg`).
- Applies the new chrome to every in-scope list/form: `CollectionsListView`, `CollectionDetailView`, `SoulDetailView`, `SettingsSheet`, `CreateSoulSheet`, `EditSoulSheet`, `CreateCollectionSheet`, `EditCollectionSheet`, `PebbleFormView`.
- `PebbleFormView`'s top render section stays borderless (edge-to-edge artwork is intentional).
- Lab views are intentionally out of scope.

## Test plan
- [ ] `npm run build --workspace=@pbbls/ios` succeeds
- [ ] `npm run lint --workspace=@pbbls/ios` clean
- [ ] Simulator smoke pass in light + dark mode across each modified screen
- [ ] `Localizable.xcstrings` shows no `New`/`Stale` entries

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 3: Apply labels and milestone (inherited from issue #471)**

Issue #471 labels: `ios`, `quality`, `ui`. Milestone: `M32 · iOS Quality`. PR inherits the same.

Run:
```bash
gh pr edit --add-label "ios,quality,ui" --milestone "M32 · iOS Quality"
```

Expected: `gh` confirms labels and milestone applied.

- [ ] **Step 4: Return the PR URL to the user**

The `gh pr create` output prints the URL. Surface it in the final message.

---

## Self-review checklist (run before handoff)

**Spec coverage:**
- Border / radius / color spec → Task 1 (border overlay in `PebblesListRowModifier`)
- Per-section grouping → row position enum + helper in Task 1, applied per file in Tasks 2–10
- Section header typography → `Text.pebblesSectionHeader()` in Task 1, applied per file in Tasks 3, 5, 6, 7, 8, 9, 10
- Scope list (9 files) → one task per file (Tasks 2–10)
- `PebbleFormView` render section stays borderless → explicit instruction in Task 10 Step 2
- EditCollectionSheet included → Task 8
- Lab views excluded → not in plan

**Placeholders:** None — every step shows actual code or commands.

**Type consistency:** `PebblesListRowPosition` cases (`.only`/`.top`/`.middle`/`.bottom`) used identically across all tasks. `pebblesRowPosition(index:count:)` signature stable. `.pebblesList()` / `.pebblesListRow(position:)` / `Text.pebblesSectionHeader()` API matches Task 1.
