# Issue #465 — Profile page token refacto (iOS) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Re-tokenize every Profile-screen component on iOS against the latest design (typography, color, spacing, corner radii), add the small set of new design tokens the spec introduces, and wire collection-tile-wide tap targets.

**Architecture:** Pure SwiftUI view refacto on `apps/ios/Pebbles/Features/Profile/`. New tokens land in `Theme/`. One shared chrome modifier (`.profileCard()`) is added. No data model changes; one query gains an existing aggregate. No new RPCs. No tests added (project policy: "no UI tests for now"); each task verifies via `xcodebuild` build and SwiftUI `#Preview` updates. Smoke test in simulator at the end.

**Tech Stack:** SwiftUI (iOS 17+), Supabase Swift client, xcodegen-managed Xcode project. Localization via `Resources/Localizable.xcstrings` (string catalog with native plural variations).

**Spec:** `docs/superpowers/specs/2026-05-17-issue-465-profile-tokens-refacto-design.md`

**Working directory:** `/Users/alexis/code/pbbls` — all paths below are relative to this.

**Branch:** `quality/465-profile-tokens-refacto` (already created).

**Build command used throughout:** `xcodebuild build -scheme Pebbles -destination "platform=iOS Simulator,name=iPhone 16 Pro" -quiet` (run from `apps/ios/`). If the simulator name differs locally, swap it. The `-quiet` flag suppresses noise; failures still print.

---

## File Structure

**New files:**
- `apps/ios/Pebbles/Theme/Icon+Pebbles.swift` — `PebblesIcon` enum + `pebblesIcon(_:)` view modifier.
- `apps/ios/Pebbles/Theme/ProfileCard.swift` — `.profileCard()` view modifier (clear bg + system.muted border + lg radius + lg padding).
- `apps/ios/Pebbles/Features/Profile/Components/DataTile.swift` — small reusable value/icon/label tile used by `ProfileCountersRow`.
- `apps/ios/Pebbles/Features/Profile/Components/ProfileLogoutButton.swift` — renamed from `ProfileLogoutPill.swift`.

**Modified files:**
- `apps/ios/Pebbles/Theme/Font+Pebbles.swift` — `+counterLg`, `+captionEmphasized` cases.
- `apps/ios/Pebbles/Resources/Localizable.xcstrings` — `+` plural entry for "%lld pebble(s)".
- `apps/ios/Pebbles/Features/Profile/ProfileView.swift` — outer spacing token; logout call-site rename.
- `apps/ios/Pebbles/Features/Profile/Components/ProfileBanner.swift` — token swap.
- `apps/ios/Pebbles/Features/Profile/Components/ProfileShortcutTile.swift` — token swap.
- `apps/ios/Pebbles/Features/Profile/Components/ProfileShortcutsRow.swift` — spacing token.
- `apps/ios/Pebbles/Features/Profile/Components/ProfileStatsCard.swift` — chrome modifier + tokens.
- `apps/ios/Pebbles/Features/Profile/Components/RipplesRow.swift` — token swap.
- `apps/ios/Pebbles/Features/Shared/Ripples/RippleBadge.swift` — digit typography + color simplification.
- `apps/ios/Pebbles/Features/Profile/Components/AssiduityGrid.swift` — cell size.
- `apps/ios/Pebbles/Features/Profile/Components/ProfileCountersRow.swift` — replaced internals with `DataTile`.
- `apps/ios/Pebbles/Features/Profile/Components/ProfileCollectionsCard.swift` — chrome + query + per-tile NavigationLink.
- `apps/ios/Pebbles/Features/Profile/Components/ProfileCollectionCard.swift` — full rewrite per spec.
- `apps/ios/Pebbles/Features/Profile/Components/ProfileLabCard.swift` — chrome + tokens.

**Deleted files:**
- `apps/ios/Pebbles/Features/Profile/Components/ProfileLogoutPill.swift` (renamed to `ProfileLogoutButton.swift`).

---

## Note on testing posture

Per `apps/ios/CLAUDE.md`: "No UI tests for now." This plan does **not** introduce new Swift test files. Each task verifies via:
1. The Swift build (compiler catches type/signature mistakes).
2. Updated `#Preview` blocks (visual sanity in Xcode previews).
3. A final simulator smoke pass (Task 16) for tap targets and dark-mode appearance.

If TDD ceremony feels missing — it is, and that's intentional for a view-only refacto on a project with no UI test layer. Re-introduce real tests when the project introduces a snapshot-testing target.

---

## Task 1: Add `counterLg` and `captionEmphasized` typography tokens

**Files:**
- Modify: `apps/ios/Pebbles/Theme/Font+Pebbles.swift`

- [ ] **Step 1: Add the two new cases to `PebblesFont`**

Open `apps/ios/Pebbles/Theme/Font+Pebbles.swift`. In the `enum PebblesFont` block, add:

```swift
case counterLg
case captionEmphasized
```

Place `counterLg` immediately after `cardHeadingEmphasized` and `captionEmphasized` immediately before `title`. (Group by family — title-like at the bottom, body/data tokens above it.)

- [ ] **Step 2: Add font mappings**

In the `private extension PebblesFont` block, inside the `var font: Font` switch, add:

```swift
case .counterLg:             return .sfProRounded(17, .semibold)
case .captionEmphasized:     return .sfProRounded(12, .semibold)
```

- [ ] **Step 3: Add tracking mappings**

In the same extension, inside the `var tracking: CGFloat` switch, extend the existing `case .body, .bodyEmphasized, .headline, .headlineEmphasized, .buttonLabel:` line to also include `.counterLg`:

```swift
case .body, .bodyEmphasized, .headline, .headlineEmphasized,
     .buttonLabel, .counterLg:                            return 0.34
```

Add a new case for the caption:

```swift
case .captionEmphasized:                                  return 0.24   // 2% of 12
```

`.isUppercase` does NOT need updating — both new tokens default to `false` via the catch-all.

- [ ] **Step 4: Build**

Run from `apps/ios/`:
```bash
xcodebuild build -scheme Pebbles -destination "platform=iOS Simulator,name=iPhone 16 Pro" -quiet
```
Expected: BUILD SUCCEEDED.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Theme/Font+Pebbles.swift
git commit -m "feat(ios): add counter.lg and caption.emphasized typo tokens"
```

---

## Task 2: Create `PebblesIcon` token enum and `pebblesIcon(_:)` modifier

**Files:**
- Create: `apps/ios/Pebbles/Theme/Icon+Pebbles.swift`

- [ ] **Step 1: Write the new file**

Create `apps/ios/Pebbles/Theme/Icon+Pebbles.swift`:

```swift
import SwiftUI

/// Icon-sizing tokens for SF Symbols. Lives in a sibling enum to `PebblesFont`
/// so call sites read intent-first (`.pebblesIcon(.md)` rather than calling an
/// icon a "font"). Under the hood this still applies a `Font` — SF Symbols are
/// font glyphs and `.font(.system(size:weight:design:))` is the native API for
/// pixel-precise sizing.
enum PebblesIcon {
    case sm     // 13pt semibold
    case md     // 15pt medium
    case large  // 17pt semibold
}

extension View {
    /// Apply a Pebbles icon-size token to an `Image(systemName:)`.
    func pebblesIcon(_ token: PebblesIcon) -> some View {
        font(.system(size: token.size, weight: token.weight, design: .rounded))
    }
}

private extension PebblesIcon {
    var size: CGFloat {
        switch self {
        case .sm:    return 13
        case .md:    return 15
        case .large: return 17
        }
    }

    var weight: Font.Weight {
        switch self {
        case .sm, .large: return .semibold
        case .md:         return .medium
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
xcodebuild build -scheme Pebbles -destination "platform=iOS Simulator,name=iPhone 16 Pro" -quiet
```
Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Theme/Icon+Pebbles.swift
git commit -m "feat(ios): add PebblesIcon token enum and pebblesIcon modifier"
```

---

## Task 3: Create `.profileCard()` chrome modifier

**Files:**
- Create: `apps/ios/Pebbles/Theme/ProfileCard.swift`

- [ ] **Step 1: Write the new file**

Create `apps/ios/Pebbles/Theme/ProfileCard.swift`:

```swift
import SwiftUI

/// Shared chrome modifier for Profile-screen cards (Stats, Collections, Lab):
/// clear background, 1pt `system.muted` border, `Spacing.lg` corner radius,
/// `Spacing.lg` padding. Lives as a `ViewModifier` (call site reads as
/// `.profileCard()`) rather than a wrapper view so the SwiftUI hierarchy stays
/// flat.
extension View {
    func profileCard() -> some View {
        modifier(ProfileCardModifier())
    }
}

private struct ProfileCardModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(Spacing.lg)
            .clipShape(RoundedRectangle(cornerRadius: Spacing.lg))
            .overlay {
                RoundedRectangle(cornerRadius: Spacing.lg)
                    .strokeBorder(Color.system.muted, lineWidth: 1)
            }
    }
}
```

- [ ] **Step 2: Build**

```bash
xcodebuild build -scheme Pebbles -destination "platform=iOS Simulator,name=iPhone 16 Pro" -quiet
```
Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Theme/ProfileCard.swift
git commit -m "feat(ios): add profileCard chrome modifier"
```

---

## Task 4: Tokenize `ProfileBanner`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileBanner.swift`

- [ ] **Step 1: Replace the body**

Overwrite the body and previews:

```swift
import SwiftUI

struct ProfileBanner: View {
    let displayName: String?
    let memberSince: Date?
    let glyphStrokes: [GlyphStroke]?

    var body: some View {
        VStack(spacing: Spacing.xxl) {
            glyph

            VStack(spacing: Spacing.xs) {
                Text(displayName ?? "")
                    .pebblesFont(.title)
                    .foregroundStyle(Color.system.foreground)
                if let memberSince {
                    Text("Member since \(memberSince.formatted(.dateTime.month(.wide).year()))")
                        .pebblesFont(.meta)
                        .foregroundStyle(Color.system.secondary)
                }
            }
        }
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private var glyph: some View {
        if let strokes = glyphStrokes, !strokes.isEmpty {
            GlyphView(case: .profile, strokes: strokes, side: 96)
        } else {
            GlyphView(case: .carve, side: 96)
        }
    }
}

#Preview("With glyph (placeholder strokes)") {
    ProfileBanner(displayName: "Alexis", memberSince: Date(), glyphStrokes: nil)
        .padding()
}

#Preview("With glyph") {
    ProfileBanner(
        displayName: "Alexis",
        memberSince: Date(),
        glyphStrokes: [GlyphStroke(d: "M40,40 L160,160", width: 6)]
    )
    .padding()
}
```

Removed: manual `.textCase(.uppercase)` (the `.meta` token already applies it), literal sizes, `.title3.weight(.semibold)`.

- [ ] **Step 2: Build**

```bash
xcodebuild build -scheme Pebbles -destination "platform=iOS Simulator,name=iPhone 16 Pro" -quiet
```
Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Components/ProfileBanner.swift
git commit -m "quality(ios): tokenize profile banner (#465)"
```

---

## Task 5: Tokenize `ProfileShortcutTile` and `ProfileShortcutsRow`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileShortcutTile.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileShortcutsRow.swift`

- [ ] **Step 1: Update `ProfileShortcutTile`**

Replace the file with:

```swift
import SwiftUI

struct ProfileShortcutTile<Destination: View>: View {
    let title: LocalizedStringResource
    let systemImage: String
    @ViewBuilder let destination: () -> Destination

    var body: some View {
        NavigationLink {
            destination()
        } label: {
            VStack(spacing: Spacing.sm) {
                Image(systemName: systemImage)
                    .pebblesIcon(.large)
                    .foregroundStyle(Color.accent.primary)
                Text(title)
                    .pebblesFont(.callout)
                    .foregroundStyle(Color.system.secondary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.lg)
            .background(Color.accent.surface)
            .clipShape(RoundedRectangle(cornerRadius: Spacing.lg))
        }
        .buttonStyle(.plain)
    }
}
```

- [ ] **Step 2: Update `ProfileShortcutsRow` spacing**

Open `apps/ios/Pebbles/Features/Profile/Components/ProfileShortcutsRow.swift`. Change `HStack(spacing: 12)` to `HStack(spacing: Spacing.sm)`:

```swift
import SwiftUI

struct ProfileShortcutsRow: View {
    var body: some View {
        HStack(spacing: Spacing.sm) {
            ProfileShortcutTile(title: "Collections", systemImage: "square.stack.3d.up") {
                CollectionsListView()
            }
            ProfileShortcutTile(title: "Souls", systemImage: "person.2") {
                SoulsListView()
            }
            ProfileShortcutTile(title: "Glyphs", systemImage: "scribble") {
                GlyphsListView()
            }
        }
    }
}

#Preview {
    NavigationStack {
        ProfileShortcutsRow().padding()
    }
}
```

- [ ] **Step 3: Build**

```bash
xcodebuild build -scheme Pebbles -destination "platform=iOS Simulator,name=iPhone 16 Pro" -quiet
```
Expected: BUILD SUCCEEDED.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Components/ProfileShortcutTile.swift \
        apps/ios/Pebbles/Features/Profile/Components/ProfileShortcutsRow.swift
git commit -m "quality(ios): tokenize profile shortcuts row (#465)"
```

---

## Task 6: Shrink `AssiduityGrid` cell to size 7

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Components/AssiduityGrid.swift`

- [ ] **Step 1: Change the default `cellSize`**

In `apps/ios/Pebbles/Features/Profile/Components/AssiduityGrid.swift`, change:

```swift
var cellSize: CGFloat = 14
```

to:

```swift
var cellSize: CGFloat = 7
```

Leave everything else (cellSpacing, columns, body) untouched.

- [ ] **Step 2: Build**

```bash
xcodebuild build -scheme Pebbles -destination "platform=iOS Simulator,name=iPhone 16 Pro" -quiet
```
Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Components/AssiduityGrid.swift
git commit -m "quality(ios): shrink assiduity grid cell to size 7 (#465)"
```

---

## Task 7: Update `RippleBadge` digit typography and color

**Files:**
- Modify: `apps/ios/Pebbles/Features/Shared/Ripples/RippleBadge.swift`

- [ ] **Step 1: Remove `digitColor` and `colorScheme` env, swap digit token**

Replace the body of the struct so that:
- The `@Environment(\.colorScheme) private var colorScheme` line is deleted.
- The `digitColor` computed property is deleted.
- The `Text(verbatim: ...)` digit uses `.pebblesFont(.captionEmphasized)` and `Color.system.foreground`.

The final file should be:

```swift
import SwiftUI

/// 44×44 ring-and-digit badge representing the user's Ripples level.
/// See `docs/superpowers/specs/2026-05-15-ripples-design.md` and
/// issue #442 for full color/state semantics.
struct RippleBadge: View {
    let level: Int
    let activeToday: Bool

    private var clampedLevel: Int { min(max(level, 0), 6) }

    private func tone(forStroke id: Int) -> RippleStrokeTone {
        rippleStrokeTone(strokeId: id, level: clampedLevel, activeToday: activeToday)
    }

    var body: some View {
        ZStack {
            // Draw outermost first so inner rings paint on top.
            RippleStroke6()
                .stroke(tone(forStroke: 6).color, style: stroke)
                .opacity(0.33)
            RippleStroke5()
                .stroke(tone(forStroke: 5).color, style: stroke)
                .opacity(0.33)
            RippleStroke4()
                .stroke(tone(forStroke: 4).color, style: stroke)
                .opacity(0.33)
            RippleStroke3()
                .stroke(tone(forStroke: 3).color, style: stroke)
                .opacity(0.66)
            RippleStroke2()
                .stroke(tone(forStroke: 2).color, style: stroke)
                .opacity(0.66)
            RippleStroke1()
                .stroke(tone(forStroke: 1).color, style: stroke)

            Text(verbatim: "\(clampedLevel)")
                .pebblesFont(.captionEmphasized)
                .foregroundStyle(Color.system.foreground)
        }
        .frame(width: 44, height: 44)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityLabel)
    }

    private var stroke: StrokeStyle {
        StrokeStyle(lineWidth: 2, lineCap: .round)
    }

    private var accessibilityLabel: LocalizedStringResource {
        activeToday
            ? LocalizedStringResource("Ripple level \(clampedLevel), active today")
            : LocalizedStringResource("Ripple level \(clampedLevel), inactive today")
    }
}

#Preview("All states — light") {
    RipplePreviewGrid()
        .preferredColorScheme(.light)
}

#Preview("All states — dark") {
    RipplePreviewGrid()
        .preferredColorScheme(.dark)
}

private struct RipplePreviewGrid: View {
    var body: some View {
        VStack(spacing: 12) {
            ForEach([true, false], id: \.self) { active in
                HStack(spacing: 8) {
                    Text(verbatim: active ? "active" : "inactive")
                        .font(.caption)
                        .frame(width: 60, alignment: .leading)
                    ForEach(0...6, id: \.self) { level in
                        RippleBadge(level: level, activeToday: active)
                    }
                }
            }
        }
        .padding()
        .background(Color.system.background)
    }
}
```

- [ ] **Step 2: Build**

```bash
xcodebuild build -scheme Pebbles -destination "platform=iOS Simulator,name=iPhone 16 Pro" -quiet
```
Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Shared/Ripples/RippleBadge.swift
git commit -m "quality(ios): tokenize ripple badge digit (#465)"
```

---

## Task 8: Tokenize `RipplesRow`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Components/RipplesRow.swift`

- [ ] **Step 1: Replace the file**

```swift
import SwiftUI

struct RipplesRow: View {
    let ripple: RippleSummary?
    let assiduity: [Bool]?

    var body: some View {
        HStack(alignment: .center, spacing: Spacing.lg) {
            RippleBadge(
                level: ripple?.rippleLevel ?? 0,
                activeToday: ripple?.activeToday ?? false
            )

            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text("Ripples Level \(ripple?.rippleLevel ?? 0)")
                    .pebblesFont(.headline)
                    .foregroundStyle(Color.system.foreground)
                Text(progressCopy)
                    .pebblesFont(.subhead)
                    .foregroundStyle(Color.system.secondary)
            }

            Spacer(minLength: 8)

            AssiduityGrid(data: assiduity ?? Array(repeating: false, count: 28))
        }
    }

    private var progressCopy: LocalizedStringResource {
        guard let ripple else { return "Loading…" }
        if let remaining = ripple.pebblesToNextLevel, let next = ripple.nextLevel {
            return "\(remaining) more pebbles to level \(next)"
        } else {
            return "Max level reached"
        }
    }
}

#Preview("Engaged") {
    RipplesRow(
        ripple: RippleSummary(rippleLevel: 3, pebbles28d: 11, activeToday: true),
        assiduity: (0..<28).map { $0 % 2 == 0 }
    )
    .padding()
}

#Preview("Empty") {
    RipplesRow(ripple: nil, assiduity: nil)
        .padding()
}
```

- [ ] **Step 2: Build**

```bash
xcodebuild build -scheme Pebbles -destination "platform=iOS Simulator,name=iPhone 16 Pro" -quiet
```
Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Components/RipplesRow.swift
git commit -m "quality(ios): tokenize ripples row (#465)"
```

---

## Task 9: Create `DataTile` and rewrite `ProfileCountersRow`

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Components/DataTile.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileCountersRow.swift`

- [ ] **Step 1: Create `DataTile`**

Create `apps/ios/Pebbles/Features/Profile/Components/DataTile.swift`:

```swift
import SwiftUI

/// Single value/icon/label tile used inside the Profile Stats card.
/// vstack(xs) of:
///   - large counter number (counter.lg)
///   - hstack(xs) of icon (icon.sm, accent.primary) and label (subhead, system.secondary)
struct DataTile: View {
    let value: Int?
    let icon: String
    let label: LocalizedStringResource

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(value.map { "\($0)" } ?? "—")
                .pebblesFont(.counterLg)
                .foregroundStyle(Color.system.foreground)
                .monospacedDigit()
            HStack(spacing: Spacing.xs) {
                Image(systemName: icon)
                    .pebblesIcon(.sm)
                    .foregroundStyle(Color.accent.primary)
                Text(label)
                    .pebblesFont(.subhead)
                    .foregroundStyle(Color.system.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview {
    HStack {
        DataTile(value: 42, icon: "calendar", label: "Days")
        DataTile(value: 137, icon: "fossil.shell", label: "Pebbles")
        DataTile(value: 1200, icon: "sparkles", label: "Karma")
    }
    .padding()
}
```

- [ ] **Step 2: Rewrite `ProfileCountersRow` to use `DataTile`**

Replace `apps/ios/Pebbles/Features/Profile/Components/ProfileCountersRow.swift` with:

```swift
import SwiftUI

struct ProfileCountersRow: View {
    let daysPracticed: Int?
    let pebbles: Int?
    let karma: Int?

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            DataTile(value: daysPracticed, icon: "calendar",     label: "Days")
            DataTile(value: pebbles,       icon: "fossil.shell", label: "Pebbles")
            DataTile(value: karma,         icon: "sparkles",     label: "Karma")
        }
    }
}

#Preview {
    ProfileCountersRow(daysPracticed: 42, pebbles: 137, karma: 1200)
        .padding()
}
```

- [ ] **Step 3: Build**

```bash
xcodebuild build -scheme Pebbles -destination "platform=iOS Simulator,name=iPhone 16 Pro" -quiet
```
Expected: BUILD SUCCEEDED.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Components/DataTile.swift \
        apps/ios/Pebbles/Features/Profile/Components/ProfileCountersRow.swift
git commit -m "quality(ios): introduce DataTile, tokenize counters row (#465)"
```

---

## Task 10: Apply `.profileCard()` chrome and tokens to `ProfileStatsCard`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileStatsCard.swift`

- [ ] **Step 1: Replace the file**

```swift
import SwiftUI

struct ProfileStatsCard: View {
    let ripple: RippleSummary?
    let assiduity: [Bool]?
    let daysPracticed: Int?
    let pebbles: Int?
    let karma: Int?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            Text("Stats")
                .pebblesFont(.cardHeading)
                .foregroundStyle(Color.system.secondary)

            RipplesRow(ripple: ripple, assiduity: assiduity)

            Divider().overlay(Color.system.muted)

            ProfileCountersRow(daysPracticed: daysPracticed, pebbles: pebbles, karma: karma)
        }
        .profileCard()
    }
}

#Preview {
    ProfileStatsCard(
        ripple: RippleSummary(rippleLevel: 3, pebbles28d: 11, activeToday: true),
        assiduity: (0..<28).map { $0 % 2 == 0 },
        daysPracticed: 42,
        pebbles: 137,
        karma: 1200
    )
    .padding()
}
```

Note: heading text is `"Stats"` (not `"STATS"`) — the `.cardHeading` token applies `.textCase(.uppercase)` automatically. No chevron on this card (spec confirms Stats has no detail destination in iOS yet).

- [ ] **Step 2: Build**

```bash
xcodebuild build -scheme Pebbles -destination "platform=iOS Simulator,name=iPhone 16 Pro" -quiet
```
Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Components/ProfileStatsCard.swift
git commit -m "quality(ios): tokenize stats card and apply card chrome (#465)"
```

---

## Task 11: Add `"%lld pebble(s)"` plural to the string catalog

**Files:**
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`

- [ ] **Step 1: Add the entry**

Open `apps/ios/Pebbles/Resources/Localizable.xcstrings`. The file is a JSON catalog. Find the alphabetical position for `"%lld pebbles"` (after `"%lld more pebbles to level %lld"` and before `"%lld strokes drawn"`). Insert this entry **before** `"%lld strokes drawn"`:

```json
    "%lld pebbles" : {
      "extractionState" : "manual",
      "localizations" : {
        "en" : {
          "variations" : {
            "plural" : {
              "one" : {
                "stringUnit" : {
                  "state" : "translated",
                  "value" : "%lld pebble"
                }
              },
              "other" : {
                "stringUnit" : {
                  "state" : "translated",
                  "value" : "%lld pebbles"
                }
              }
            }
          }
        },
        "fr" : {
          "variations" : {
            "plural" : {
              "one" : {
                "stringUnit" : {
                  "state" : "translated",
                  "value" : "%lld pebble"
                }
              },
              "other" : {
                "stringUnit" : {
                  "state" : "translated",
                  "value" : "%lld pebbles"
                }
              }
            }
          }
        }
      }
    },
```

(French uses the same "pebble"/"pebbles" copy — this is the brand-name English-everywhere convention already established for "Pebbles" elsewhere in the catalog. If FR adopts a translated noun later, it changes here.)

Mind JSON: the `,` after the closing `}` is required because the catalog has more entries after this one.

- [ ] **Step 2: Validate JSON shape**

```bash
python3 -c "import json; json.load(open('apps/ios/Pebbles/Resources/Localizable.xcstrings'))"
```
Expected: no output (silent success). If you get a `JSONDecodeError`, fix the surrounding comma(s).

- [ ] **Step 3: Build**

```bash
xcodebuild build -scheme Pebbles -destination "platform=iOS Simulator,name=iPhone 16 Pro" -quiet
```
Expected: BUILD SUCCEEDED.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "chore(i18n): add %lld pebble(s) plural variation"
```

---

## Task 12: Rewrite `ProfileCollectionCard`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileCollectionCard.swift`

- [ ] **Step 1: Replace the file**

```swift
import SwiftUI

/// Tile in the horizontal Collections scroller on the Profile screen.
/// Two visual variants:
///   - `.filled(collection:)` — a real collection: solid border, icon glyph,
///     name + pebble count.
///   - `.empty` — dashed-border placeholder prompting the user to create
///     their first collection.
///
/// Both variants render only their visual content; the parent decides whether
/// to wrap the tile in a `NavigationLink` (filled → detail view) or a
/// `Button` (empty → create sheet). The whole tile is hit-tested via
/// `.contentShape(...)` so taps land regardless of fill.
struct ProfileCollectionCard: View {
    enum Variant {
        case filled(collection: Collection)
        case empty
    }

    let variant: Variant

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            iconBox
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(variant.title)
                    .pebblesFont(.headline)
                    .foregroundStyle(Color.system.foreground)
                if let subtitleKey = variant.subtitleKey {
                    Text(subtitleKey)
                        .pebblesFont(.subhead)
                        .foregroundStyle(Color.system.secondary)
                }
            }
        }
        .padding(Spacing.lg)
        .frame(width: 140, alignment: .leading)
        .overlay { variant.borderOverlay }
        .contentShape(RoundedRectangle(cornerRadius: Spacing.lg))
    }

    private var iconBox: some View {
        ZStack {
            RoundedRectangle(cornerRadius: Spacing.sm)
                .fill(Color.accent.surface)
            Image(systemName: variant.iconName)
                .pebblesIcon(.sm)
                .foregroundStyle(Color.accent.primary)
        }
        .frame(width: Spacing.xxl, height: Spacing.xxl)
    }
}

private extension ProfileCollectionCard.Variant {
    var iconName: String {
        switch self {
        case .filled: return "square.stack.3d.up"
        case .empty:  return "plus"
        }
    }

    var title: LocalizedStringResource {
        switch self {
        case .filled(let collection): return LocalizedStringResource(stringLiteral: collection.name)
        case .empty:                  return "New collection"
        }
    }

    /// `subhead` line under the title. `nil` for the empty tile (no count to show).
    var subtitleKey: LocalizedStringResource? {
        switch self {
        case .filled(let collection):
            // String-catalog plural entry; see Localizable.xcstrings → "%lld pebbles".
            return LocalizedStringResource("\(collection.pebbleCount) pebbles")
        case .empty:
            return nil
        }
    }

    @ViewBuilder
    var borderOverlay: some View {
        switch self {
        case .filled:
            RoundedRectangle(cornerRadius: Spacing.lg)
                .strokeBorder(Color.system.muted, lineWidth: 1)
        case .empty:
            RoundedRectangle(cornerRadius: Spacing.lg)
                .strokeBorder(
                    Color.system.muted,
                    style: StrokeStyle(lineWidth: 1, dash: [10, 10], lineCap: .round)
                )
        }
    }
}

#Preview {
    HStack(spacing: Spacing.sm) {
        ProfileCollectionCard(
            variant: .filled(collection: Collection.preview)
        )
        ProfileCollectionCard(variant: .empty)
    }
    .padding()
}

private extension Collection {
    static var preview: Collection {
        // Workaround: Collection has a custom decoder, no memberwise init.
        // Build via JSON for previews so we keep one source of truth.
        let data = """
        { "id": "11111111-1111-1111-1111-111111111111",
          "name": "Reading list",
          "mode": "pack",
          "pebble_count": [{ "count": 7 }] }
        """.data(using: .utf8)!
        return try! JSONDecoder().decode(Collection.self, from: data)
    }
}
```

- [ ] **Step 2: Build**

```bash
xcodebuild build -scheme Pebbles -destination "platform=iOS Simulator,name=iPhone 16 Pro" -quiet
```
Expected: BUILD SUCCEEDED. If the build fails on `ProfileCollectionsCard.swift` because its existing `variant: .filled(name: c.name)` no longer matches the new signature, that's expected — Task 13 fixes the call site. To unblock the build during this isolated task, temporarily comment out the `ForEach` body inside `ProfileCollectionsCard.swift` (`// FIXME: rewired in Task 13`). Restore in Task 13.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Components/ProfileCollectionCard.swift \
        apps/ios/Pebbles/Features/Profile/Components/ProfileCollectionsCard.swift
git commit -m "quality(ios): rewrite collection tile per design tokens (#465)"
```

---

## Task 13: Rewire `ProfileCollectionsCard` (chrome, query, NavigationLink per tile)

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileCollectionsCard.swift`

- [ ] **Step 1: Replace the file**

```swift
import SwiftUI
import os

struct ProfileCollectionsCard: View {
    @Environment(SupabaseService.self) private var supabase

    @State private var collections: [Collection] = []
    @State private var hasLoaded = false
    @State private var isPresentingCreateSheet = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile-collections")

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            header

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.sm) {
                    if collections.isEmpty && hasLoaded {
                        Button {
                            isPresentingCreateSheet = true
                        } label: {
                            ProfileCollectionCard(variant: .empty)
                        }
                        .buttonStyle(.plain)
                    } else {
                        ForEach(collections) { collection in
                            NavigationLink {
                                CollectionDetailView(collection: collection, onChanged: {
                                    Task {
                                        hasLoaded = false
                                        await load()
                                    }
                                })
                            } label: {
                                ProfileCollectionCard(variant: .filled(collection: collection))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(.horizontal, Spacing.lg)
            }
            .padding(.horizontal, -Spacing.lg)
        }
        .profileCard()
        .task { await load() }
        .sheet(isPresented: $isPresentingCreateSheet) {
            CreateCollectionSheet(onCreated: {
                Task {
                    hasLoaded = false
                    await load()
                }
            })
        }
    }

    private var header: some View {
        NavigationLink {
            CollectionsListView()
        } label: {
            HStack {
                Text("Collections")
                    .pebblesFont(.cardHeading)
                    .foregroundStyle(Color.system.secondary)
                Spacer()
                Image(systemName: "chevron.right")
                    .pebblesIcon(.md)
                    .foregroundStyle(Color.system.muted)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func load() async {
        guard !hasLoaded else { return }
        do {
            let rows: [Collection] = try await supabase.client
                .from("collections")
                .select("id, name, mode, pebble_count:collection_pebbles(count)")
                .order("created_at", ascending: false)
                .execute().value
            self.collections = rows
            self.hasLoaded = true
        } catch {
            logger.error("collections fetch failed: \(error.localizedDescription, privacy: .private)")
            self.hasLoaded = true
        }
    }
}
```

Removed: the private `ProfileCollectionRow` struct (now uses the existing `Collection` model). The "Collections" heading is lowercase here — `.cardHeading` uppercases automatically.

Side note: the inset/outset trick (`.padding(.horizontal, Spacing.lg)` + `.padding(.horizontal, -Spacing.lg)`) lets tiles scroll edge-to-edge inside the padded card. Same shape as before — only the literal `16` is replaced with `Spacing.lg`.

- [ ] **Step 2: Build**

```bash
xcodebuild build -scheme Pebbles -destination "platform=iOS Simulator,name=iPhone 16 Pro" -quiet
```
Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Components/ProfileCollectionsCard.swift
git commit -m "quality(ios): tokenize collections card and wire per-tile navigation (#465)"
```

---

## Task 14: Apply `.profileCard()` chrome and tokens to `ProfileLabCard`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileLabCard.swift`

- [ ] **Step 1: Replace the file**

```swift
import SwiftUI

struct ProfileLabCard: View {
    var body: some View {
        NavigationLink {
            LabView()
        } label: {
            HStack(spacing: Spacing.xs) {
                Image(systemName: "lightbulb.max")
                    .pebblesIcon(.large)
                    .foregroundStyle(Color.accent.primary)
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text("Lab")
                        .pebblesFont(.headline)
                        .foregroundStyle(Color.system.foreground)
                    Text("News & community")
                        .pebblesFont(.subhead)
                        .foregroundStyle(Color.system.secondary)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .pebblesIcon(.md)
                    .foregroundStyle(Color.system.muted)
            }
            .contentShape(Rectangle())
            .profileCard()
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    NavigationStack {
        ProfileLabCard().padding()
    }
}
```

- [ ] **Step 2: Build**

```bash
xcodebuild build -scheme Pebbles -destination "platform=iOS Simulator,name=iPhone 16 Pro" -quiet
```
Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Components/ProfileLabCard.swift
git commit -m "quality(ios): tokenize lab card (#465)"
```

---

## Task 15: Rename `ProfileLogoutPill` → `ProfileLogoutButton` and re-style

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Components/ProfileLogoutButton.swift`
- Delete: `apps/ios/Pebbles/Features/Profile/Components/ProfileLogoutPill.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/ProfileView.swift` (call site)

- [ ] **Step 1: Create the renamed file with new styling**

Create `apps/ios/Pebbles/Features/Profile/Components/ProfileLogoutButton.swift`:

```swift
import SwiftUI

struct ProfileLogoutButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text("Log out")
                .pebblesFont(.buttonLabel)
                .foregroundStyle(Color.accent.primary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.md)
                .background(
                    RoundedRectangle(cornerRadius: Spacing.lg)
                        .fill(Color.accent.surface)
                )
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    ProfileLogoutButton(action: {}).padding()
}
```

- [ ] **Step 2: Delete the old file**

```bash
git rm apps/ios/Pebbles/Features/Profile/Components/ProfileLogoutPill.swift
```

- [ ] **Step 3: Update the call site in `ProfileView`**

Open `apps/ios/Pebbles/Features/Profile/ProfileView.swift`. Find the existing block:

```swift
                ProfileLogoutPill {
                    Task { await supabase.signOut() }
                }
                .padding(.top, 8)
```

Replace with:

```swift
                ProfileLogoutButton {
                    Task { await supabase.signOut() }
                }
                .padding(.top, 8)
```

(The `.padding(.top, 8)` is removed wholesale in Task 16 — leave it here for now.)

- [ ] **Step 4: Regenerate the xcodeproj (xcodegen globs sources but a clean regen avoids stale references)**

```bash
cd apps/ios && npm run generate && cd -
```

Expected: prints `Generated project successfully`. If the command isn't defined in `apps/ios/package.json`, use `xcodegen generate --spec apps/ios/project.yml` from the repo root instead.

- [ ] **Step 5: Build**

```bash
xcodebuild build -scheme Pebbles -destination "platform=iOS Simulator,name=iPhone 16 Pro" -quiet
```
Expected: BUILD SUCCEEDED.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Components/ProfileLogoutButton.swift \
        apps/ios/Pebbles/Features/Profile/ProfileView.swift
git commit -m "quality(ios): rename ProfileLogoutPill to ProfileLogoutButton and restyle (#465)"
```

(The deletion is already staged from Step 2.)

---

## Task 16: Tokenize outer `ProfileView` spacing and remove banner top padding

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/ProfileView.swift`

- [ ] **Step 1: Update outer VStack and drop banner top padding**

In `apps/ios/Pebbles/Features/Profile/ProfileView.swift`, change:

```swift
            VStack(spacing: 16) {
                ProfileBanner(
                    displayName: profile?.displayName,
                    memberSince: profile?.createdAt,
                    glyphStrokes: glyphStrokes
                )
                .padding(.top, 8)
```

to:

```swift
            VStack(spacing: Spacing.xl) {
                ProfileBanner(
                    displayName: profile?.displayName,
                    memberSince: profile?.createdAt,
                    glyphStrokes: glyphStrokes
                )
```

Also remove the `.padding(.top, 8)` modifier on `ProfileLogoutButton`:

```swift
                ProfileLogoutButton {
                    Task { await supabase.signOut() }
                }
```

(The `Spacing.xl` (22) outer gap supersedes both manual top paddings.)

The outer `.padding(.horizontal, 16)` and `.padding(.bottom, 32)` can be left as-is — they're not in scope for this issue (and 16/32 aren't on the token scale; revisiting them is a separate quality pass).

- [ ] **Step 2: Build**

```bash
xcodebuild build -scheme Pebbles -destination "platform=iOS Simulator,name=iPhone 16 Pro" -quiet
```
Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/ProfileView.swift
git commit -m "quality(ios): tokenize profile view outer spacing (#465)"
```

---

## Task 17: Simulator smoke test

**Files:** none modified.

This task is manual visual + interaction verification. No commits.

- [ ] **Step 1: Boot the simulator and run the app**

```bash
xcodebuild -scheme Pebbles -destination "platform=iOS Simulator,name=iPhone 16 Pro" build
xcrun simctl boot "iPhone 16 Pro" 2>/dev/null || true
open -a Simulator
```

Then run from Xcode (`Cmd+R` on the Pebbles scheme) — `xcodebuild` alone doesn't launch the app in the simulator. Sign in (or use whatever dev fixture session is established) and navigate to the Profile tab.

- [ ] **Step 2: Visual checklist (light mode)**

Compare against issue #465 screenshots. Confirm each:
- Banner: glyph centered, name in serif title, "Member since …" subtitle uppercase and muted.
- Shortcuts row: three accent-surface tiles, accent-primary icons, secondary-colored labels.
- Stats card: clear bg with thin muted border; "STATS" heading; ripple row reads as expected; assiduity grid is small (size 7) but readable; counter tiles (Days/Pebbles/Karma) show big numbers above small icon+label.
- Collections card: same chrome; "COLLECTIONS" heading + chevron right; horizontal tiles with rounded icon-box on accent-surface.
- Empty state (if user has 0 collections): dashed-border tile with `+` icon.
- Lab card: same chrome; lightbulb icon + Lab/News & community + chevron.
- Logout button: accent-surface rectangle with accent-primary label.

- [ ] **Step 3: Visual checklist (dark mode)**

Toggle simulator dark mode (`Cmd+Shift+A` in iOS 17+ simulator, or Settings → Developer → Dark Appearance). Confirm:
- All borders still visible (no contrast loss).
- Banner title legible.
- Ripple-badge digit (now always `system.foreground`) is legible against ALL active-day fills, including levels 1–6. **If illegible at any level, file a follow-up — do not patch in this PR (acceptance was confirmed at design time).**
- Assiduity grid cells (size 7) are still distinguishable from background.

If size 7 is visually broken: revert Task 6 and open a follow-up issue tagging design.

- [ ] **Step 4: Interaction checklist**

- Tap each shortcut tile (Collections / Souls / Glyphs) → respective list pushes.
- Tap anywhere along the Collections card header (label, chevron, or empty space between) → `CollectionsListView` pushes.
- Tap anywhere on a filled collection tile (not just the label) → `CollectionDetailView` pushes for that collection. **Key acceptance.**
- If no collections exist: tap anywhere on the dashed tile (including the empty interior, not just the `+` icon) → `CreateCollectionSheet` presents. **Key acceptance.**
- Tap anywhere on the Lab card → `LabView` pushes.
- Tap "Log out" → signs out (or shows the existing confirmation, depending on `SupabaseService.signOut` behavior).
- Settings gear (toolbar) still presents the settings sheet.

- [ ] **Step 5: Update `Localizable.xcstrings` review (Xcode)**

Open `Localizable.xcstrings` in Xcode. Confirm:
- No entry is in the `New` or `Stale` state.
- The new `%lld pebbles` plural entry shows both `one` and `other` variants in `en` and `fr`.
- No other unexpected new entries appeared (i.e., we didn't inadvertently introduce un-tokenized text that auto-extracted into the catalog).

If anything is `New`, address it (translate or remove).

---

## Task 18: Final workspace lint and PR open

**Files:** none modified.

- [ ] **Step 1: Lint the iOS workspace**

```bash
npm run lint --workspace=@pbbls/ios 2>&1 | tail -40
```

Expected: exit 0, no errors. If there is no lint script defined for the workspace, skip this step (record skip reason in the PR body).

- [ ] **Step 2: Confirm clean working tree**

```bash
git status
```
Expected: nothing to commit, branch ahead of `main` by the task commits.

- [ ] **Step 3: Push and open PR**

```bash
git push -u origin quality/465-profile-tokens-refacto
```

Then open the PR. Title: `quality(ios): profile page token refacto`. Body starts with `Resolves #465`. Inherit the labels from issue #465 (`ios`, `quality`, `ui`) and the milestone `M32 · iOS Quality` — per repo policy, confirm inheritance with the user before opening (or pass through automatically per the autonomous-mode CLAUDE.md instructions). PR body should call out:
- The `RippleBadge` color simplification (always `system.foreground`) — design-confirmed at spec time.
- The `AssiduityGrid` cell size shrink (14 → 7) — smoke-test passed.
- `ProfileLogoutPill` → `ProfileLogoutButton` rename (file + struct).
- New tokens: `PebblesFont.counterLg`, `PebblesFont.captionEmphasized`, `PebblesIcon.{sm, md, large}`, `.profileCard()` modifier.
- New string catalog plural: `%lld pebbles`.

```bash
gh pr create --title "quality(ios): profile page token refacto" --body "$(cat <<'EOF'
Resolves #465

## Summary
- Re-tokenize every Profile-screen component (typography, color, spacing, corner radii) per the design in issue #465.
- Add five new design tokens: `PebblesFont.counterLg`, `PebblesFont.captionEmphasized`, and `PebblesIcon.{sm, md, large}` (with `.pebblesIcon(_:)` modifier).
- Add `.profileCard()` view modifier for the Stats / Collections / Lab card chrome.
- Wire the **whole** collection tile as a tap target (filled → push detail view; empty → present create sheet). This fixes the "only the + works" frustration.
- Rename `ProfileLogoutPill` → `ProfileLogoutButton` (file + struct) to match the new rounded-rectangle shape.
- Add a `%lld pebbles` plural entry to `Localizable.xcstrings` for the per-collection count under each tile.

## Notes for review
- `RippleBadge` digit color is now always `system.foreground` (was conditional on `colorScheme` × `activeToday`). Confirmed acceptable by design at spec time.
- `AssiduityGrid` cells shrink from 14pt to 7pt per spec — smoke-tested in light and dark mode.
- No new tests (project policy: "no UI tests for now"). Verification was via `xcodebuild` build per commit + a simulator smoke pass.

## Test plan
- [x] Build succeeds for `Pebbles` scheme on iPhone 16 Pro simulator
- [x] Visual parity with screenshots in #465 in light mode
- [x] Visual parity in dark mode (ripple-badge digit legible at all 7 levels)
- [x] Tap anywhere on a filled collection tile pushes that collection's detail view
- [x] Tap anywhere on the dashed empty tile presents the create-collection sheet
- [x] Tap anywhere on the Collections card header pushes the list view
- [x] No `New` or `Stale` entries in `Localizable.xcstrings`
EOF
)" --label ios --label quality --label ui --milestone "M32 · iOS Quality"
```

Expected: prints PR URL.

- [ ] **Step 4: Return the PR URL to the user.**

---

## Self-review notes (internal)

Coverage scan against the spec:

| Spec section | Task |
|---|---|
| New typography tokens | 1 |
| New icon tokens | 2 |
| `.profileCard()` modifier | 3 |
| `ProfileView` outer spacing | 16 |
| `ProfileBanner` | 4 |
| `ProfileShortcutTile` / `ProfileShortcutsRow` | 5 |
| `AssiduityGrid` cell size | 6 |
| `RippleBadge` digit | 7 |
| `RipplesRow` | 8 |
| `DataTile` + `ProfileCountersRow` | 9 |
| `ProfileStatsCard` | 10 |
| `Localizable.xcstrings` plural | 11 |
| `ProfileCollectionCard` rewrite | 12 |
| `ProfileCollectionsCard` (chrome, query, NavigationLink) | 13 |
| `ProfileLabCard` | 14 |
| `ProfileLogoutPill` → `Button` rename + restyle | 15 |
| Smoke test | 17 |
| Lint + PR | 18 |

All spec sections mapped. Type-consistency check: `Collection` (the existing model) is the type passed through `.filled(collection:)` in Task 12 and constructed in `ProfileCollectionsCard` in Task 13 — same type, same property names (`name`, `pebbleCount`). `pebblesIcon(_:)` is defined in Task 2 and consumed in Tasks 5, 9, 10 (via Stats heading? no — heading uses `pebblesFont`), 12, 13, 14. No drift.

Task 12 has a known sequencing wart: rewriting `ProfileCollectionCard` breaks `ProfileCollectionsCard`'s call site until Task 13 rewires it. Task 12 explicitly tells the implementer to stub the call site temporarily — alternatives (merging 12+13 into one task, or doing 13 first with a placeholder card) traded one wart for another. This is the simplest path.
