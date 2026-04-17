# Display Pebble Render in Path — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show each pebble's server-composed SVG visual, colored by the pebble's emotion, as a 40×40 leading thumbnail in every row of the Path list. Edited pebbles refresh automatically when returning to Path.

**Architecture:** Extend the `Pebble` model with `renderSvg: String?` and `emotion: EmotionRef?` and update `PathView`'s PostgREST select to pull them. Replace the row's `VStack` with an `HStack` that leads with `PebbleRenderView` (or a placeholder `RoundedRectangle` when the SVG is missing). Existing refresh-after-sheet-dismiss wiring covers the edit-refresh acceptance criterion.

**Tech Stack:** SwiftUI, iOS 17+, SVGView (exyte), Supabase Swift client (PostgREST)

**Spec:** `docs/superpowers/specs/2026-04-17-ios-path-pebble-render-design.md`

**Branch:** `feat/276-path-pebble-render`

---

### File Map

- **Modify:** `apps/ios/Pebbles/Features/Path/Models/Pebble.swift` — add `renderSvg` + `emotion` fields with nested-emotion decoding
- **Modify:** `apps/ios/Pebbles/Features/Path/PathView.swift` — extend select; replace row `VStack` with `HStack` containing leading visual

---

### Task 1: Extend `Pebble` model

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/Pebble.swift`

- [ ] **Step 1: Add fields and custom decoder**

The existing struct uses synthesized `Decodable`. We need to decode a nested `emotion:emotions(...)` relation, which requires a custom `init(from:)` that pulls the color from the nested object. Reuse the established `EmotionRef` type already defined in `PebbleDetail.swift` (id/name/color) — consistent with how `EditPebbleSheet` / `PebbleDetailSheet` handle nested emotion.

Replace the file contents with:

```swift
import Foundation

struct Pebble: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
    let happenedAt: Date
    let renderSvg: String?
    let emotion: EmotionRef?

    private enum CodingKeys: String, CodingKey {
        case id
        case name
        case happenedAt = "happened_at"
        case renderSvg = "render_svg"
        case emotion
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try container.decode(UUID.self, forKey: .id)
        self.name = try container.decode(String.self, forKey: .name)
        self.happenedAt = try container.decode(Date.self, forKey: .happenedAt)
        self.renderSvg = try container.decodeIfPresent(String.self, forKey: .renderSvg)
        self.emotion = try container.decodeIfPresent(EmotionRef.self, forKey: .emotion)
    }
}
```

Both `renderSvg` and `emotion` are optional: `render_svg` can be `NULL` when the `compose-pebble` edge function fails (soft-success path), and we want Path to survive that rather than crash the list.

---

### Task 2: Update Path query to fetch SVG + emotion color

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PathView.swift` (lines 74-89)

- [ ] **Step 1: Change the PostgREST select**

Replace the current `load()` implementation:

```swift
private func load() async {
    do {
        let result: [Pebble] = try await supabase.client
            .from("pebbles")
            .select("id, name, happened_at, render_svg, emotion:emotions(id, name, color)")
            .order("happened_at", ascending: false)
            .execute()
            .value
        self.pebbles = result
        self.isLoading = false
    } catch {
        logger.error("path fetch failed: \(error.localizedDescription, privacy: .private)")
        self.loadError = "Couldn't load your pebbles."
        self.isLoading = false
    }
}
```

The new select fields (`render_svg`, nested `emotion`) come back as part of the same round-trip. Error handling is unchanged.

- [ ] **Step 2: Build to verify decoding compiles**

Run: `npm run build --workspace=@pbbls/ios 2>&1 | tail -30`

Expected: BUILD SUCCEEDED. (The row body still uses only `pebble.name` and `pebble.happenedAt`, so no call-site errors yet — we're verifying the model + query compile cleanly before touching the UI.)

---

### Task 3: Replace row `VStack` with `HStack` + leading visual

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PathView.swift` (lines 55-69)

- [ ] **Step 1: Refactor the row body**

Replace the `Section("Path") { ... }` block with:

```swift
Section("Path") {
    ForEach(pebbles) { pebble in
        Button {
            selectedPebbleId = pebble.id
        } label: {
            HStack(spacing: 12) {
                pebbleThumbnail(for: pebble)
                VStack(alignment: .leading, spacing: 4) {
                    Text(pebble.name).font(.body)
                    Text(pebble.happenedAt, style: .date)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .buttonStyle(.plain)
    }
}
```

- [ ] **Step 2: Add the thumbnail helper**

Add this `@ViewBuilder` method inside `PathView`, next to the existing `content` computed property:

```swift
@ViewBuilder
private func pebbleThumbnail(for pebble: Pebble) -> some View {
    if let svg = pebble.renderSvg {
        PebbleRenderView(svg: svg, strokeColor: pebble.emotion?.color)
            .frame(width: 40, height: 40)
    } else {
        RoundedRectangle(cornerRadius: 6)
            .fill(Color.secondary.opacity(0.15))
            .frame(width: 40, height: 40)
    }
}
```

`PebbleRenderView` already handles `strokeColor: nil` by skipping the `currentColor` replacement, so passing `pebble.emotion?.color` directly is safe even when emotion is missing. The placeholder branch keeps row height stable for the soft-success case.

- [ ] **Step 3: Build**

Run: `npm run build --workspace=@pbbls/ios 2>&1 | tail -30`

Expected: BUILD SUCCEEDED.

- [ ] **Step 4: Lint**

Run: `npm run lint --workspace=@pbbls/ios 2>&1 | tail -20`

Expected: no new violations introduced by the change. (Pre-existing warnings elsewhere in the package may remain — only regressions in the files you touched are blockers.)

---

### Task 4: Manual QA in the iOS simulator

Required before shipping — the build tells us the code compiles, not that it looks right.

- [ ] **Step 1: Run the app on the iPhone 17 simulator**

Open the generated `.xcodeproj` in Xcode and run, or use the CLI:

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' -configuration Debug build
```

Then launch the resulting `.app` in Simulator.app, or run directly from Xcode with ⌘R.

- [ ] **Step 2: Verify the golden path**

Sign in and navigate to Path. Confirm:
- Every row shows a 40×40 pebble visual on the leading edge.
- Each visual is stroked in the emotion's color (should differ per pebble based on the emotion assigned).
- Name and date remain legible and aligned with the visual's vertical center.
- Tapping a row still opens the edit sheet.

- [ ] **Step 3: Verify the refresh-on-edit flow**

- Tap a pebble → edit sheet opens.
- Change the name (keeping the same emotion) → save.
- Confirm the path row's text updates. The visual should remain unchanged (same `render_svg` on the server).
- Open the same pebble again and change the emotion → save.
- Confirm the path row's text and color refresh. (If color does not refresh, it means the server did not re-stroke on edit — this is expected per the spec's "non-goals"; recomposition on edit is out of scope for #276.)

- [ ] **Step 4: Verify the soft-success fallback**

If you have (or can create) a pebble where `render_svg` is `NULL` in the DB, confirm the row renders a subtle empty rounded-rect placeholder instead of crashing or collapsing the row height. If no such pebble exists, skip this step and note it in the PR body.

---

### Task 5: Commit

- [ ] **Step 1: Stage and commit both files**

```bash
git add apps/ios/Pebbles/Features/Path/Models/Pebble.swift apps/ios/Pebbles/Features/Path/PathView.swift
git commit -m "feat(ui): display pebble render in path (#276)"
```

- [ ] **Step 2: Push and open PR**

```bash
git push -u origin feat/276-path-pebble-render
```

Follow the project's PR workflow (title `feat(ui): display pebble render in path`, body starts with `Resolves #276`, inherit labels `feat` + `ios` + `ui` and milestone `M23 · TestFlight V1` from the issue — confirm with user before opening).

---

## Out of scope reminders (do NOT implement here)

- Server-side recomposition when editing a pebble's emotion or valence.
- Animation of the pebble visual.
- Changes to `PebbleRenderView` itself (it's already frame-agnostic).
- Any redesign of Path rows beyond adding the leading visual.
- Pagination / prefetch / caching of SVG strings.
