# Issue #466 — Harmonize top toolbar design (iOS)

**Status:** Approved
**Author:** Brainstorming session, 2026-05-18
**Scope:** `apps/ios` only

## Context

The iOS app currently has two inconsistent toolbar patterns:

- **Standard (most sheets):** `Button("Cancel") { … }` in `.cancellationAction` /
  `.confirmationAction`. Renders as the native iOS button (Liquid Glass capsule
  on iOS 26), tinted with `accent.primary` because `pebblesScreen()` sets
  `.tint(Color.accent.primary)`.
- **Off-spec (`PebbleDetailSheet`):** the "Edit" button wraps `Text("Edit")` in
  a custom `Capsule().fill(Color.system.background.opacity(0.85))`, and the
  leading `PebblePrivacyBadge(.chip)` does the same with a `Circle()`. These
  are the "weird bg" called out in the issue screenshot.

No sheet currently centers a branded title — every sheet relies on
`.navigationTitle(...)` which renders in the system style, not the `meta`
typography token.

## Goal

Every toolbar in the iOS app uses the same chrome:

- **Buttons:** native (Liquid Glass capsule on iOS 26), label in
  `system.secondary`, no custom background.
- **Title (between buttons):** `pebblesFont(.meta)` + `system.secondary`,
  centered via `.principal` placement.

Applies to **all** toolbars — modal sheets, pushed detail views, and tab roots.

## Non-goals

- No global `UINavigationBar.appearance()` override. Appearance proxies fight
  SwiftUI's per-view styling and can't render the `.meta` token (uppercase +
  tracked) on the system title cleanly.
- No change to `pebblesScreen()`'s ambient `.tint(accent.primary)`. That tint
  still drives in-content accent (links, accent icons). The toolbar override is
  local to toolbar items.
- No new typography token — reuse `.meta`.
- No refactor of `PebbleReadView` or other content. Only the toolbar surface
  changes.
- The `.capsule` style of `PebblePrivacyBadge` (used inline in
  `PebbleReadView`, not in toolbars) stays as-is.
- No iOS 17 fallback work. `.principal` items and `.meta` font render
  correctly back to iOS 17; Liquid Glass capsules render on iOS 26 and the
  prior look on 17/18 — both are acceptable since the rule is "let the system
  render the button."
- `.keyboard` `ToolbarItemGroup` in `SettingsSheet` and `GlyphCarveSheet` are
  input accessories, not the top bar — untouched.

## Design

### New helpers (Theme layer)

#### 1. `PebbleToolbarButton`

New file: `apps/ios/Pebbles/Theme/PebbleToolbarButton.swift`

A thin wrapper around `Button` that exists for two reasons:

1. Pins label color to `system.secondary` regardless of the ambient
   `.tint(accent.primary)` set by `pebblesScreen()`.
2. Gives us one grep target if the rule changes again.

No custom shape, no padding, no `.buttonStyle`. The system renders the Liquid
Glass capsule.

```swift
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

// Convenience for the common Text("Cancel") / Text("Save") case
extension PebbleToolbarButton where Label == Text {
    init(_ titleKey: LocalizedStringKey,
         role: ButtonRole? = nil,
         action: @escaping () -> Void) {
        self.init(role: role, action: action) { Text(titleKey) }
    }
}
```

> `.tint(...)` is what controls the label color of a system-rendered toolbar
> button. `.foregroundStyle` is not used because the system bar style
> overrides it on iOS 26.

#### 2. `.pebblesToolbarTitle(_:)` view modifier

New file: `apps/ios/Pebbles/Theme/PebblesToolbarTitle.swift`

Injects a `.principal` `ToolbarItem` with the branded text. Keeps
`.navigationTitle(...)` alongside it (with the same string) so VoiceOver and
the back stack still see the page name. The `.principal` item takes the visual
slot from the system inline title.

```swift
extension View {
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
}
```

A second overload accepts `LocalizedStringResource` for sites that pull the
title from a typed reference field (e.g. soul/collection names that are user
data, not catalog strings — those use plain `Text(_ string: String)` so we'd
also add a `String`-accepting overload).

```swift
extension View {
    func pebblesToolbarTitle(_ title: String) -> some View { … }
}
```

> SwiftUI allows multiple `.toolbar` modifiers in the same view tree. The
> `.principal` item from the modifier coexists with the sheet's own
> `.toolbar { ToolbarItem(.cancellationAction)…  }` block.

### Migration — per-site changes

For every site below the migration is:

1. Replace `.navigationTitle("…")` + `.navigationBarTitleDisplayMode(.inline)`
   with `.pebblesToolbarTitle("…")`.
2. Replace toolbar `Button("…") { … }` with `PebbleToolbarButton("…") { … }`.
3. Remove any custom capsule/chip background and custom font on toolbar
   buttons.

#### Sheets

- `Features/Path/PebbleDetailSheet.swift` — strip the custom Edit capsule
  (`.padding(.horizontal, 14) / .frame(height: 36) / .background(Capsule()…)`)
  and the `.buttonStyle(.plain)`. Use `PebbleToolbarButton("Edit") { … }`.
  Add a centered title via `.pebblesToolbarTitle(detail?.name ?? "")` —
  empty string while loading, populated once `detail` resolves. The existing
  `.toolbarBackground(.hidden, for: .navigationBar)` call stays; the new
  modifier only adds toolbar items and does not set a background, so the two
  do not collide.
- `Features/Path/CreatePebbleSheet.swift` — "New pebble" title; Cancel / Save.
- `Features/Path/EditPebbleSheet.swift` — "Edit pebble" title; Cancel / Save.
- `Features/Path/EmotionPickerSheet.swift` — "Emotions" title; Cancel / Done.
- `Features/Path/SoulPickerSheet.swift` — "Choose souls" title; Cancel / Done.
- `Features/Path/ValencePickerSheet.swift` — "Choose a valence" title;
  Cancel.
- `Features/Profile/Sheets/SettingsSheet.swift` — "Settings" title;
  Cancel / Done.
- `Features/Profile/Sheets/CreateSoulSheet.swift` — "New soul" title;
  Cancel / Save.
- `Features/Profile/Sheets/EditSoulSheet.swift` — "Edit soul" title;
  Cancel / Save.
- `Features/Profile/Sheets/CreateCollectionSheet.swift` — "New collection"
  title; Cancel / Save.
- `Features/Profile/Sheets/EditCollectionSheet.swift` — "Edit collection"
  title; Cancel / Save.
- `Features/Glyph/Views/GlyphCarveSheet.swift` — "New glyph" title;
  Cancel / Save.
- `Features/Glyph/Views/GlyphPickerSheet.swift` — "Choose a glyph" title;
  Cancel.

#### Pushed detail views

- `Features/Profile/Views/SoulDetailView.swift` — title is the soul's name
  (use the `String` overload of `.pebblesToolbarTitle(_:)`); primaryAction
  button → `PebbleToolbarButton`.
- `Features/Profile/Views/CollectionDetailView.swift` — same shape as above
  for collection name.

#### List / tab-root screens

- `Features/Profile/ProfileView.swift` — "Profile" title; trailing settings
  button → `PebbleToolbarButton`.
- `Features/Profile/Lists/SoulsListView.swift` — "Souls" title; primaryAction
  add button → `PebbleToolbarButton`.
- `Features/Profile/Lists/CollectionsListView.swift` — "Collections" title;
  primaryAction add button → `PebbleToolbarButton`.
- `Features/Glyph/Views/GlyphsListView.swift` — "Glyphs" title; primaryAction
  add button → `PebbleToolbarButton`.
- `Features/Lab/LabView.swift` — "Lab" title.
- `Features/Lab/Views/LogListView.swift` — dynamic `title` (String overload).
- `Features/Onboarding/OnboardingView.swift` — toolbar already has leading +
  trailing buttons; no title (welcome flow). Buttons → `PebbleToolbarButton`;
  no `.pebblesToolbarTitle(...)` since the screen intentionally has no title.

### `PebblePrivacyBadge.chip` change

File: `apps/ios/Pebbles/Features/Path/Read/PebblePrivacyBadge.swift`

Drop the chip's circle background and switch the icon color to
`system.secondary`:

```swift
private var chipBody: some View {
    Image(systemName: "lock.fill")
        .font(.system(size: 14, weight: .medium))
        .foregroundStyle(Color.system.secondary)
        .frame(width: 36, height: 36)
        .accessibilityLabel(accessibilityLabelText)
}
```

The `.capsule` style (used inline in `PebbleReadView`, not in toolbars) is
unchanged.

## Testing

No unit tests — pure styling change with no logic.

Manual sweep (simulator, iPhone 17 default size):

- Visual: open every listed sheet/screen in light + dark, default and Large
  dynamic-type, and confirm:
  - All toolbar buttons render as bare native Liquid Glass with `system.secondary` label.
  - All centered titles render with `.meta` (uppercase, tracked, 12pt) in
    `system.secondary`.
- Localization: switch the device to French and confirm titles render and do
  not truncate in the principal slot.
- Accessibility: VoiceOver reads each screen's page name (sourced from the
  shadow `.navigationTitle(...)` call).

## Acceptance criteria

- Every toolbar button label is `system.secondary` and has no custom background.
- Every centered title uses the `.meta` typo token in `system.secondary`.
- The off-spec Edit capsule on `PebbleDetailSheet` is gone.
- The chip bg on `PebblePrivacyBadge.chip` is gone (icon recolored to
  `system.secondary`).
- All sites listed in the Migration section build cleanly and lint passes
  (`npm run lint --workspace=@pbbls/ios`).
