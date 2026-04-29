# iOS Pebble Read View Polish — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework the iOS pebble read view to match the polished mockups in issue #331 — 16:9 photo banner with floating chip toolbar, smaller pebble in an overlapping page-bg-fill box, smaller serif title, inline metadata pills, souls as inline pills below description.

**Architecture:** Greenfield component decomposition under `apps/ios/Pebbles/Features/Path/Read/`. Four new SwiftUI views (`PebbleReadBanner`, `PebbleReadTitle`, `PebblePillFlow`, `PebbleMetaPill`) replace three existing ones (`PebbleReadHeader`, `PebbleReadPicture`, `PebbleMetadataRow`). `PebbleReadView` is rewritten to compose them; `PebbleDetailSheet` keeps its toolbar but restyles items as floating chips. `PebblePrivacyBadge` gains a `.chip` style variant. `SnapImageView` is generalized to support `.fill` content mode so the banner can render a 16:9 cover image.

**Tech Stack:** SwiftUI (iOS 17+), `@Observable`, `Layout` protocol for the pill flow container, existing `SVGView`-based `PebbleRenderView`, existing `Color.pebbles*` theme tokens, `LocalizedStringResource` for all user-facing strings.

**Spec:** `docs/superpowers/specs/2026-04-29-ios-pebble-read-view-polish-design.md`. **Issue:** [#331](https://github.com/Bohns/pbbls/issues/331). **Branch:** `feat/329-pebble-read-view` (current).

**Testing policy:** No automated tests in V1 (matches `apps/ios/CLAUDE.md`). Each component ships with `#Preview` blocks. Verification = `xcodegen generate` + `xcodebuild build` between tasks.

---

## File map

```
apps/ios/Pebbles/Features/Path/Read/
  PebbleReadBanner.swift       (NEW)  – photo + overlapping pebble box, no-photo variant
  PebbleReadTitle.swift        (NEW)  – serif title + uppercase tracked date
  PebblePillFlow.swift         (NEW)  – Layout flow container with wrapping
  PebbleMetaPill.swift         (NEW)  – atomic pill (.emotion / .neutral / .unset)
  PebbleReadView.swift         (MOD)  – composes the 4 new components
  PebblePrivacyBadge.swift     (MOD)  – adds .chip style variant
  PebbleReadHeader.swift       (DEL)
  PebbleReadPicture.swift      (DEL)
  PebbleMetadataRow.swift      (DEL)
apps/ios/Pebbles/Features/Path/
  PebbleDetailSheet.swift      (MOD)  – chip-style toolbar items, transparent nav bar
apps/ios/Pebbles/Features/PebbleMedia/
  SnapImageView.swift          (MOD)  – exposes contentMode, drops internal clipShape
```

`apps/ios/project.yml` does not need changes — its `sources: [{ path: Pebbles }]` auto-includes everything under `Pebbles/`. After adding/removing files, `xcodegen generate` is still required to refresh the (gitignored) `.xcodeproj` file list.

---

## Task 1 — Generalize `SnapImageView` for cover-image use

**Why now:** The new banner needs a 16:9 photo with `.fill` content mode and externally controlled corner radius. Today `SnapImageView` hardcodes `.scaledToFit()` and an internal `.clipShape(RoundedRectangle(cornerRadius: 12))`. Generalizing it first means Task 6 (banner) can just consume it.

**Files:**
- Modify: `apps/ios/Pebbles/Features/PebbleMedia/SnapImageView.swift`

- [ ] **Step 1: Replace the file with a content-mode-aware version**

Overwrite `apps/ios/Pebbles/Features/PebbleMedia/SnapImageView.swift` with:

```swift
import SwiftUI

/// Lazy-loads signed URLs for a single snap and renders the original (1024 px)
/// JPEG. Designed for the pebble detail sheet — caller passes the
/// `storage_path` from `public.snaps`, no auth/user knowledge required.
///
/// The view does not clip its output: callers decide framing and corner
/// radius. Pass `contentMode: .fill` for cover-style banners and `.fit` for
/// natural-aspect previews.
struct SnapImageView: View {
    let storagePath: String
    var contentMode: ContentMode = .fit

    @Environment(SupabaseService.self) private var supabase

    @State private var urls: PebbleSnapRepository.SignedURLs?
    @State private var loadError = false

    var body: some View {
        Group {
            if let urls {
                AsyncImage(url: urls.original) { phase in
                    switch phase {
                    case .empty:
                        ProgressView()
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    case .failure:
                        fallbackPlaceholder
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: contentMode)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    @unknown default:
                        fallbackPlaceholder
                    }
                }
            } else if loadError {
                fallbackPlaceholder
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .task {
            do {
                urls = try await PebbleSnapRepository(client: supabase.client)
                    .signedURLs(storagePrefix: storagePath)
            } catch {
                loadError = true
            }
        }
    }

    private var fallbackPlaceholder: some View {
        Rectangle()
            .fill(Color.secondary.opacity(0.1))
            .overlay(
                Image(systemName: "photo.on.rectangle.angled")
                    .foregroundStyle(.secondary)
            )
    }
}
```

Key differences from before:
- New `contentMode: ContentMode = .fit` parameter (default preserves current call sites' visual intent — `.fit`).
- No internal `.clipShape(...)`. Callers clip.
- No internal hardcoded `.frame(height: 200)`. Callers size.
- Fallback is `Rectangle()` (caller clips).

- [ ] **Step 2: Verify build**

```bash
cd apps/ios && xcodegen generate && xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' build -quiet
```

Expected: build succeeds. There is currently exactly one caller (`PebbleReadPicture`) and it will be deleted in Task 9, so behavior changes here are inert until then.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/SnapImageView.swift
git commit -m "refactor(ui): expose contentMode on SnapImageView, drop internal clip"
```

---

## Task 2 — Add `.chip` style variant to `PebblePrivacyBadge`

**Why:** The toolbar will render the badge as a circular floating chip instead of an outlined capsule. Keep the default style available for any future caller; add the `.chip` variant the toolbar can opt into.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Read/PebblePrivacyBadge.swift`

- [ ] **Step 1: Replace the file with the dual-style version**

Overwrite the file with:

```swift
import SwiftUI

/// Badge showing the pebble's privacy status. Two styles:
///
/// - `.capsule` (default): outlined capsule with lock + label, suitable for
///   inline placement.
/// - `.chip`: 36pt circular chip with translucent surface and a centered
///   lock icon, intended for the floating navigation-bar treatment in
///   `PebbleDetailSheet`.
struct PebblePrivacyBadge: View {
    enum Style {
        case capsule
        case chip
    }

    let visibility: Visibility
    var style: Style = .capsule

    var body: some View {
        switch style {
        case .capsule: capsuleBody
        case .chip:    chipBody
        }
    }

    private var capsuleBody: some View {
        HStack(spacing: 6) {
            Image(systemName: "lock.fill")
                .font(.caption)
                .accessibilityHidden(true)
            Text(visibility.label)
                .font(.caption)
                .fontWeight(.medium)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .overlay(
            Capsule()
                .strokeBorder(Color.pebblesBorder, lineWidth: 1)
        )
        .foregroundStyle(Color.pebblesForeground)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text("Privacy: \(String(localized: visibility.label))",
                                 comment: "Accessibility label for the pebble privacy badge"))
    }

    private var chipBody: some View {
        Image(systemName: "lock.fill")
            .font(.system(size: 14, weight: .medium))
            .foregroundStyle(Color.pebblesForeground)
            .frame(width: 36, height: 36)
            .background(
                Circle().fill(Color.pebblesBackground.opacity(0.85))
            )
            .accessibilityLabel(Text("Privacy: \(String(localized: visibility.label))",
                                     comment: "Accessibility label for the pebble privacy chip"))
    }
}

#Preview {
    VStack(spacing: 16) {
        PebblePrivacyBadge(visibility: .private)
        PebblePrivacyBadge(visibility: .public)
        PebblePrivacyBadge(visibility: .private, style: .chip)
        PebblePrivacyBadge(visibility: .public, style: .chip)
    }
    .padding()
    .background(Color.pebblesBackground)
}
```

Notes:
- Default `style: .capsule` keeps existing call sites compiling unchanged (the toolbar call site is updated in Task 8).
- The chip uses `Color.pebblesBackground.opacity(0.85)` so it reads on both the photo and the bare page background. If this contrast feels wrong during visual review, swap for a dedicated translucent token in a follow-up.

- [ ] **Step 2: Verify build**

```bash
cd apps/ios && xcodegen generate && xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' build -quiet
```

Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Read/PebblePrivacyBadge.swift
git commit -m "feat(ui): add chip style variant to PebblePrivacyBadge"
```

---

## Task 3 — Create `PebbleMetaPill`

**Why:** Atomic pill component for the metadata row and souls row. Replaces `PebbleMetadataRow`'s row layout with a compact pill. Owns the hex-color helper that today lives at the bottom of `PebbleReadView`.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Read/PebbleMetaPill.swift`

- [ ] **Step 1: Create the file**

Write `apps/ios/Pebbles/Features/Path/Read/PebbleMetaPill.swift`:

```swift
import SwiftUI

/// Compact pill used in the pebble read view to show emotion, domain,
/// collections, and souls inline. Variants:
///
/// - `.emotion(color:)`: filled pill in the emotion's color, white icon and
///   label. Always used for the emotion pill.
/// - `.neutral`: muted surface fill with normal foreground. Used for set
///   domain/collections/souls.
/// - `.unset`: same neutral fill plus a 1pt dashed stroke and muted
///   foreground. Used when domain is missing.
///
/// Spacing/sizing constants match the spec at
/// `docs/superpowers/specs/2026-04-29-ios-pebble-read-view-polish-design.md`.
struct PebbleMetaPill: View {
    enum Icon {
        case system(String)
        case glyph(Glyph)
    }

    enum Style: Equatable {
        case emotion(color: Color)
        case neutral
        case unset
    }

    let icon: Icon
    let label: LocalizedStringResource
    let style: Style

    var body: some View {
        HStack(spacing: 6) {
            iconView
                .frame(width: 16, height: 16)
                .foregroundStyle(foreground)
                .accessibilityHidden(true)
            Text(label)
                .font(.subheadline)
                .fontWeight(.medium)
                .foregroundStyle(foreground)
                .lineLimit(1)
        }
        .padding(.horizontal, 12)
        .frame(height: 32)
        .background(background)
        .overlay(strokeOverlay)
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private var iconView: some View {
        switch icon {
        case .system(let name):
            Image(systemName: name)
                .resizable()
                .scaledToFit()
        case .glyph(let glyph):
            GlyphThumbnail(
                strokes: glyph.strokes,
                side: 16,
                strokeColor: foreground,
                backgroundColor: .clear
            )
        }
    }

    @ViewBuilder
    private var background: some View {
        switch style {
        case .emotion(let color):
            Capsule().fill(color)
        case .neutral, .unset:
            Capsule().fill(Color.pebblesAccentSoft)
        }
    }

    @ViewBuilder
    private var strokeOverlay: some View {
        if case .unset = style {
            Capsule()
                .strokeBorder(
                    Color.pebblesMutedForeground,
                    style: StrokeStyle(lineWidth: 1, dash: [3])
                )
        }
    }

    private var foreground: Color {
        switch style {
        case .emotion: return .white
        case .neutral: return Color.pebblesForeground
        case .unset:   return Color.pebblesMutedForeground
        }
    }
}

// MARK: - Hex color helper

/// Parses `#RRGGBB` strings stored on `EmotionRef.color`. Falls back to
/// `nil` if the format is unexpected — caller decides on a default.
extension Color {
    init?(hex: String) {
        var trimmed = hex.trimmingCharacters(in: .whitespaces)
        if trimmed.hasPrefix("#") { trimmed.removeFirst() }
        guard trimmed.count == 6, let value = UInt32(trimmed, radix: 16) else {
            return nil
        }
        let red   = Double((value >> 16) & 0xFF) / 255.0
        let green = Double((value >> 8) & 0xFF) / 255.0
        let blue  = Double(value & 0xFF) / 255.0
        self.init(red: red, green: green, blue: blue)
    }
}

#Preview {
    VStack(alignment: .leading, spacing: 12) {
        PebbleMetaPill(
            icon: .system("heart.fill"),
            label: "Anxiety",
            style: .emotion(color: Color(red: 0.5, green: 0.4, blue: 0.95))
        )
        PebbleMetaPill(
            icon: .system("square.grid.2x2"),
            label: "Family",
            style: .neutral
        )
        PebbleMetaPill(
            icon: .system("folder.fill"),
            label: "Writing, Books",
            style: .neutral
        )
        PebbleMetaPill(
            icon: .system("square.grid.2x2"),
            label: "No domain",
            style: .unset
        )
        PebbleMetaPill(
            icon: .glyph(Glyph(
                id: UUID(),
                name: nil,
                strokes: [GlyphStroke(d: "M30,30 L170,170", width: 12)],
                viewBox: "0 0 200 200",
                userId: nil
            )),
            label: "Thierry",
            style: .neutral
        )
    }
    .padding()
    .background(Color.pebblesBackground)
}
```

Notes:
- The hex `extension Color { init?(hex:) }` is moved out of `PebbleReadView.swift` (where it's currently `private`) to file-level so callers other than `PebbleReadView` can use it. If a duplicate definition already exists elsewhere in the app this will collide at link time — search before adding (Step 2).

- [ ] **Step 2: Check for duplicate hex helper**

```bash
grep -rn "init?(hex:" apps/ios/Pebbles --include="*.swift"
```

Expected: only the existing `private extension Color` inside `PebbleReadView.swift`. If anything else turns up, change `Color(hex:)` in `PebbleMetaPill.swift` to a non-extension internal helper. **Note:** the existing helper inside `PebbleReadView.swift` is removed in Task 7.

- [ ] **Step 3: Verify build**

```bash
cd apps/ios && xcodegen generate && xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' build -quiet
```

Expected: build succeeds. The new file compiles but isn't yet consumed by anything.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Read/PebbleMetaPill.swift
git commit -m "feat(ui): add PebbleMetaPill with emotion/neutral/unset styles"
```

---

## Task 4 — Create `PebblePillFlow`

**Why:** A `Layout`-conforming container that wraps its children to multiple lines with consistent gaps. Used by both the metadata row and the souls row.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Read/PebblePillFlow.swift`

- [ ] **Step 1: Create the file**

Write `apps/ios/Pebbles/Features/Path/Read/PebblePillFlow.swift`:

```swift
import SwiftUI

/// Flow layout that places its children left-to-right and wraps to a new
/// line whenever the next child wouldn't fit in the proposed width.
/// Both axes use the same gap. iOS 17+ — uses the `Layout` protocol.
struct PebblePillFlow: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        let rows = computeRows(subviews: subviews, maxWidth: maxWidth)
        let height = rows.reduce(0) { $0 + $1.height } + spacing * CGFloat(max(rows.count - 1, 0))
        let width = rows.map(\.width).max() ?? 0
        return CGSize(width: width, height: height)
    }

    func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) {
        let rows = computeRows(subviews: subviews, maxWidth: bounds.width)
        var y = bounds.minY
        for row in rows {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(
                    at: CGPoint(x: x, y: y),
                    proposal: ProposedViewSize(width: size.width, height: size.height)
                )
                x += size.width + spacing
            }
            y += row.height + spacing
        }
    }

    private struct Row {
        var indices: [Int] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func computeRows(subviews: Subviews, maxWidth: CGFloat) -> [Row] {
        var rows: [Row] = [Row()]
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            var current = rows[rows.count - 1]
            let candidateWidth = current.width
                + (current.indices.isEmpty ? 0 : spacing)
                + size.width
            if current.indices.isEmpty || candidateWidth <= maxWidth {
                if !current.indices.isEmpty { current.width += spacing }
                current.indices.append(index)
                current.width += size.width
                current.height = max(current.height, size.height)
                rows[rows.count - 1] = current
            } else {
                var fresh = Row()
                fresh.indices.append(index)
                fresh.width = size.width
                fresh.height = size.height
                rows.append(fresh)
            }
        }
        return rows
    }
}

#Preview {
    PebblePillFlow {
        PebbleMetaPill(
            icon: .system("heart.fill"),
            label: "Anxiety",
            style: .emotion(color: Color(red: 0.5, green: 0.4, blue: 0.95))
        )
        PebbleMetaPill(icon: .system("square.grid.2x2"), label: "Family", style: .neutral)
        PebbleMetaPill(icon: .system("folder.fill"), label: "Writing, Books, Photography", style: .neutral)
        PebbleMetaPill(icon: .system("folder.fill"), label: "Travel", style: .neutral)
        PebbleMetaPill(icon: .system("folder.fill"), label: "Long collection name that wraps", style: .neutral)
    }
    .frame(width: 320)
    .padding()
    .background(Color.pebblesBackground)
}
```

- [ ] **Step 2: Verify build**

```bash
cd apps/ios && xcodegen generate && xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' build -quiet
```

Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Read/PebblePillFlow.swift
git commit -m "feat(ui): add PebblePillFlow layout for wrapping pill rows"
```

---

## Task 5 — Create `PebbleReadTitle`

**Why:** Owns the centered serif title + uppercase tracked date. Pulled out of today's `PebbleReadHeader` so it can compose independently of the banner.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Read/PebbleReadTitle.swift`

- [ ] **Step 1: Create the file**

Write `apps/ios/Pebbles/Features/Path/Read/PebbleReadTitle.swift`:

```swift
import SwiftUI

/// Title block for the pebble read view: serif name + uppercase tracked
/// date, both centered. Sized smaller than the original header so the
/// banner above it can carry the visual weight (issue #331).
struct PebbleReadTitle: View {
    let name: String
    let happenedAt: Date

    var body: some View {
        VStack(spacing: 6) {
            Text(name)
                .font(.custom("Ysabeau-SemiBold", size: 24))
                .multilineTextAlignment(.center)
                .foregroundStyle(Color.pebblesForeground)
            Text(formattedDate)
                .font(.caption)
                .tracking(1.2)
                .textCase(.uppercase)
                .foregroundStyle(Color.pebblesMutedForeground)
        }
        .frame(maxWidth: .infinity)
    }

    private var formattedDate: String {
        let date = happenedAt.formatted(
            .dateTime
                .weekday(.abbreviated)
                .month(.abbreviated)
                .day()
                .year()
        )
        let time = happenedAt.formatted(.dateTime.hour().minute())
        return "\(date) · \(time)"
    }
}

#Preview {
    VStack(spacing: 24) {
        PebbleReadTitle(name: "Publication de mon livre", happenedAt: .now)
        PebbleReadTitle(
            name: "A much longer pebble title that needs to wrap onto two lines comfortably",
            happenedAt: .now
        )
    }
    .padding()
    .background(Color.pebblesBackground)
}
```

- [ ] **Step 2: Verify build**

```bash
cd apps/ios && xcodegen generate && xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' build -quiet
```

Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Read/PebbleReadTitle.swift
git commit -m "feat(ui): add PebbleReadTitle component"
```

---

## Task 6 — Create `PebbleReadBanner`

**Why:** The most structural piece — combines the photo (when present) with the overlapping fixed-size pebble box. Handles the no-photo case by rendering only the pebble (no box) at the same vertical footprint.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift`

- [ ] **Step 1: Create the file**

Write `apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift`:

```swift
import SwiftUI

/// Top zone of the pebble read view.
///
/// With a photo: renders a 16:9 cover banner with rounded corners; the
/// pebble shape sits in a 120×120pt page-bg-fill box centered over the
/// banner's bottom edge (50% over photo, 50% below).
///
/// Without a photo: renders only the pebble centered in a 120pt-tall zone
/// — no box, no banner. Vertical footprint matches the with-photo case so
/// the layout below stays consistent.
struct PebbleReadBanner: View {
    let snapStoragePath: String?
    let renderSvg: String?
    let emotionColorHex: String
    let valence: Valence

    private let bannerCornerRadius: CGFloat = 24
    private let boxSize: CGFloat = 120
    private let boxCornerRadius: CGFloat = 24

    var body: some View {
        if let snapStoragePath {
            withPhoto(storagePath: snapStoragePath)
        } else {
            withoutPhoto
        }
    }

    @ViewBuilder
    private func withPhoto(storagePath: String) -> some View {
        VStack(spacing: 0) {
            SnapImageView(storagePath: storagePath, contentMode: .fill)
                .aspectRatio(16.0 / 9.0, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: bannerCornerRadius))
                .accessibilityHidden(true)
                .overlay(alignment: .bottom) {
                    pebbleBox
                        .offset(y: boxSize / 2)
                }
                .padding(.bottom, boxSize / 2)
        }
        .frame(maxWidth: .infinity)
    }

    private var withoutPhoto: some View {
        VStack {
            renderedPebble
        }
        .frame(maxWidth: .infinity, minHeight: boxSize)
    }

    private var pebbleBox: some View {
        renderedPebble
            .frame(width: boxSize, height: boxSize)
            .background(
                RoundedRectangle(cornerRadius: boxCornerRadius)
                    .fill(Color.pebblesBackground)
            )
    }

    @ViewBuilder
    private var renderedPebble: some View {
        if let renderSvg {
            PebbleRenderView(svg: renderSvg, strokeColor: emotionColorHex)
                .frame(height: pebbleHeight)
        } else {
            EmptyView()
        }
    }

    /// Pebble height inside the 120pt box, scaled by valence so high
    /// intensity reads bigger than low intensity but always fits comfortably.
    private var pebbleHeight: CGFloat {
        switch valence.sizeGroup {
        case .small:  return 80
        case .medium: return 100
        case .large:  return 116
        }
    }
}

#Preview("With photo · medium") {
    PebbleReadBanner(
        snapStoragePath: nil, // preview without network — see no-photo preview
        renderSvg: """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="currentColor" stroke-width="3"/>
            </svg>
            """,
        emotionColorHex: "#7C5CFA",
        valence: .neutralMedium
    )
    .padding()
    .background(Color.pebblesBackground)
}

#Preview("Without photo · large") {
    PebbleReadBanner(
        snapStoragePath: nil,
        renderSvg: """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="currentColor" stroke-width="3"/>
            </svg>
            """,
        emotionColorHex: "#7C5CFA",
        valence: .highlightLarge
    )
    .padding()
    .background(Color.pebblesBackground)
}
```

Notes:
- `pebbleHeight`: 80 / 100 / 116pt for small / medium / large. These are tuned for the 120pt box (was 180/220/260 in the full-width header). Visual review may want to tweak these — fine to adjust during the visual pass in Task 10.
- The `.overlay(alignment: .bottom) { pebbleBox.offset(y: boxSize / 2) }` + `.padding(.bottom, boxSize / 2)` pattern reserves vertical room for the half-overlap so the banner doesn't bleed into the title block.
- Photo previews use `snapStoragePath: nil` because `SnapImageView` requires `SupabaseService` from the environment. In-simulator previews of the photo case still work when running the full app.

- [ ] **Step 2: Verify build**

```bash
cd apps/ios && xcodegen generate && xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' build -quiet
```

Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift
git commit -m "feat(ui): add PebbleReadBanner with overlapping pebble box"
```

---

## Task 7 — Rewrite `PebbleReadView` to compose new components

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Read/PebbleReadView.swift`

- [ ] **Step 1: Replace the file**

Overwrite `apps/ios/Pebbles/Features/Path/Read/PebbleReadView.swift` with:

```swift
import SwiftUI

/// Body of the pebble read view. Pure UI — receives a fully-loaded
/// `PebbleDetail` and lays out the sections per spec
/// `docs/superpowers/specs/2026-04-29-ios-pebble-read-view-polish-design.md`.
///
/// `PebbleDetailSheet` wraps this view with the navigation bar (privacy
/// chip + edit button) and handles loading/error states.
struct PebbleReadView: View {
    let detail: PebbleDetail

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                PebbleReadBanner(
                    snapStoragePath: detail.snaps.first?.storagePath,
                    renderSvg: detail.renderSvg,
                    emotionColorHex: detail.emotion.color,
                    valence: detail.valence
                )

                PebbleReadTitle(name: detail.name, happenedAt: detail.happenedAt)

                metadataRow

                if let description = detail.description, !description.isEmpty {
                    Text(description)
                        .font(.system(size: 17, weight: .regular, design: .serif))
                        .foregroundStyle(Color.pebblesForeground)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                if !detail.souls.isEmpty {
                    soulsRow
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 32)
        }
        .background(Color.pebblesBackground)
    }

    @ViewBuilder
    private var metadataRow: some View {
        PebblePillFlow {
            // Emotion — always present.
            PebbleMetaPill(
                icon: .system("heart.fill"),
                label: LocalizedStringResource(stringLiteral: detail.emotion.localizedName),
                style: .emotion(color: Color(hex: detail.emotion.color) ?? Color.pebblesAccent)
            )

            // Domain — always rendered. Set when non-empty, else dashed unset.
            if detail.domains.isEmpty {
                PebbleMetaPill(
                    icon: .system("square.grid.2x2"),
                    label: "No domain",
                    style: .unset
                )
            } else {
                PebbleMetaPill(
                    icon: .system("square.grid.2x2"),
                    label: LocalizedStringResource(
                        stringLiteral: detail.domains.map(\.localizedName).joined(separator: ", ")
                    ),
                    style: .neutral
                )
            }

            // Collections — only when non-empty.
            if !detail.collections.isEmpty {
                PebbleMetaPill(
                    icon: .system("folder.fill"),
                    label: LocalizedStringResource(
                        stringLiteral: detail.collections.map(\.name).joined(separator: ", ")
                    ),
                    style: .neutral
                )
            }
        }
    }

    @ViewBuilder
    private var soulsRow: some View {
        PebblePillFlow {
            ForEach(detail.souls) { soulWithGlyph in
                PebbleMetaPill(
                    icon: .glyph(soulWithGlyph.glyph),
                    label: LocalizedStringResource(stringLiteral: soulWithGlyph.name),
                    style: .neutral
                )
            }
        }
    }
}
```

Removed compared to the old file:
- Inline `metadataBlock` using `PebbleMetadataRow`.
- Top-of-scroll `PebbleReadHeader(...)` and full-width `PebbleReadPicture(...)` calls.
- File-private `extension Color { init?(hex:) }` — that helper now lives in `PebbleMetaPill.swift` at file level.

- [ ] **Step 2: Verify build**

```bash
cd apps/ios && xcodegen generate && xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' build -quiet
```

Expected: build succeeds. The deleted components in Task 9 are now unreferenced.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Read/PebbleReadView.swift
git commit -m "feat(ui): recompose PebbleReadView with banner + pill flows"
```

---

## Task 8 — Restyle `PebbleDetailSheet` toolbar

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift`

- [ ] **Step 1: Restyle the toolbar items and make the nav bar transparent**

Apply two changes to `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift`:

**Change A** — the privacy badge call site uses the new `.chip` variant:

Replace:
```swift
                    ToolbarItem(placement: .topBarLeading) {
                        if let detail {
                            PebblePrivacyBadge(visibility: detail.visibility)
                        }
                    }
```

with:
```swift
                    ToolbarItem(placement: .topBarLeading) {
                        if let detail {
                            PebblePrivacyBadge(visibility: detail.visibility, style: .chip)
                        }
                    }
```

**Change B** — restyle the Edit button as a chip pill, and make the nav bar transparent so chips appear to float over the photo / page background.

Replace:
```swift
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("Edit") { isPresentingEdit = true }
                            .disabled(detail == nil)
                    }
```

with:
```swift
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            isPresentingEdit = true
                        } label: {
                            Text("Edit")
                                .font(.subheadline)
                                .fontWeight(.medium)
                                .foregroundStyle(Color.pebblesForeground)
                                .padding(.horizontal, 14)
                                .frame(height: 36)
                                .background(
                                    Capsule().fill(Color.pebblesBackground.opacity(0.85))
                                )
                        }
                        .buttonStyle(.plain)
                        .disabled(detail == nil)
                    }
```

And add `.toolbarBackground(.hidden, for: .navigationBar)` to the `content` chain. Replace the body's `content`-modifier stack:

```swift
            content
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
```

with:

```swift
            content
                .navigationBarTitleDisplayMode(.inline)
                .toolbarBackground(.hidden, for: .navigationBar)
                .toolbar {
```

(Leaves the rest of the `.toolbar { ... }` block untouched.)

- [ ] **Step 2: Verify build**

```bash
cd apps/ios && xcodegen generate && xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' build -quiet
```

Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift
git commit -m "feat(ui): restyle pebble detail toolbar as floating chips"
```

---

## Task 9 — Delete obsolete components

**Why:** `PebbleReadHeader`, `PebbleReadPicture`, and `PebbleMetadataRow` are unreferenced after Task 7. Remove them so the codebase doesn't accumulate dead code.

**Files:**
- Delete: `apps/ios/Pebbles/Features/Path/Read/PebbleReadHeader.swift`
- Delete: `apps/ios/Pebbles/Features/Path/Read/PebbleReadPicture.swift`
- Delete: `apps/ios/Pebbles/Features/Path/Read/PebbleMetadataRow.swift`

- [ ] **Step 1: Confirm no callers remain**

```bash
grep -rn "PebbleReadHeader\|PebbleReadPicture\|PebbleMetadataRow" apps/ios --include="*.swift"
```

Expected: only the three definitions themselves. If anything else turns up (a forgotten reference, a `#Preview`, etc.), fix that first.

- [ ] **Step 2: Delete the three files**

```bash
git rm apps/ios/Pebbles/Features/Path/Read/PebbleReadHeader.swift \
       apps/ios/Pebbles/Features/Path/Read/PebbleReadPicture.swift \
       apps/ios/Pebbles/Features/Path/Read/PebbleMetadataRow.swift
```

- [ ] **Step 3: Verify build**

```bash
cd apps/ios && xcodegen generate && xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' build -quiet
```

Expected: build succeeds.

- [ ] **Step 4: Commit**

```bash
git commit -m "chore(ui): remove obsolete PebbleReadHeader/Picture/MetadataRow"
```

---

## Task 10 — Final verification: build + lint + visual smoke + arkaik map

**Why:** Confirm the change is complete end-to-end before opening the PR.

- [ ] **Step 1: Full build**

```bash
cd apps/ios && xcodegen generate && xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' build -quiet
```

Expected: succeeds with no warnings introduced by this change.

- [ ] **Step 2: Web lint + build (project rule, even though no web files changed)**

The project's PR checklist says to run `npm run build` and `npm run lint` before opening a PR. Run them from the repo root:

```bash
npm run lint && npm run build
```

Expected: both pass. If either fails for reasons unrelated to this change, surface that to the user — don't paper over it.

- [ ] **Step 3: Visual smoke check**

Run the app in the iOS Simulator and walk through:

- Open a pebble that has a photo. Verify: 16:9 banner, rounded corners, pebble box overlapping the bottom edge with page-bg fill, floating lock chip top-leading, floating Edit chip top-trailing, transparent nav bar, smaller centered title, inline emotion+domain+collections pills, description, souls pills below if any.
- Open a pebble with no photo. Verify: no banner, pebble centered in the same vertical footprint, no box around it, layout below identical.
- Open a pebble with no domain. Verify: domain pill renders dashed with "No domain".
- Open a pebble with no description. Verify: souls row (if any) sits 20pt below the pill row, no double gap.
- Open a pebble with many souls. Verify: souls row wraps to a second line cleanly.
- Tap Edit, save a change. Verify: read view reloads in place with the new data.

If any of these fails the visual review, note the issue and either tweak constants in the relevant component (most often `PebbleReadBanner.pebbleHeight`, `PebbleReadBanner.boxSize`, or `PebbleMetaPill` height/padding) or surface it for discussion before continuing.

- [ ] **Step 4: Localizable.xcstrings review**

Per project policy, open `apps/ios/Pebbles/Resources/Localizable.xcstrings` in Xcode. Confirm:
- No entries are in `New` or `Stale` state.
- `en` and `fr` columns are populated for every row.

This change introduces no new user-facing strings (the only literal touched, "No domain", was already present), so this should be a pass-through.

- [ ] **Step 5: Update the arkaik map (if applicable)**

The pebble read surfaces (`PebbleDetailSheet`, `PebbleReadView`) already exist in the bundle. This change doesn't add or rename product surfaces — it polishes the existing read view. So **no arkaik update is needed**, but if the bundle currently lists the now-deleted child components as nodes, surgically remove those nodes via the `arkaik` skill.

```bash
grep -E "PebbleReadHeader|PebbleReadPicture|PebbleMetadataRow" docs/arkaik/bundle.json || echo "no arkaik changes needed"
```

If the grep returns nothing: skip the rest of this step.
If it returns matches: invoke the `arkaik` skill to remove them.

- [ ] **Step 6: Commit any final tweaks**

If Step 3's visual review surfaced constant tweaks, commit them now:

```bash
git add apps/ios/Pebbles/Features/Path/Read/...
git commit -m "fix(ui): tune banner/pebble sizing per visual review"
```

If no tweaks were needed, skip.

- [ ] **Step 7: Open the PR**

Per project rules, the PR title uses conventional commits, body starts with `Resolves #331`, and labels + milestone are inherited from the issue (`feat`, `ios`, `ui`, milestone `M25 · Improved core UX`) — confirm with the user before pushing labels. Branch is already `feat/329-pebble-read-view`.

---

## Self-review notes

- Spec coverage: every section of `2026-04-29-ios-pebble-read-view-polish-design.md` maps to at least one task above (banner / title / pills / souls / toolbar / loading-error / accessibility / localization / previews).
- Type consistency: `PebbleMetaPill.Style` cases (`.emotion(color:)`, `.neutral`, `.unset`), `PebbleReadBanner` constructor parameters (`snapStoragePath`, `renderSvg`, `emotionColorHex`, `valence`), and `PebblePrivacyBadge.Style` (`.capsule`, `.chip`) are referenced consistently across tasks.
- `Color(hex:)` lives in exactly one place after the migration (`PebbleMetaPill.swift`); the duplicate inside `PebbleReadView.swift` is removed in Task 7.
- `SnapImageView` no longer clips internally — the only existing caller (`PebbleReadPicture`) is deleted in Task 9, so nothing is left depending on the old clipping behavior.
- No automated tests are written, matching project policy. Each component has `#Preview` blocks for in-Xcode visual regression checks.
