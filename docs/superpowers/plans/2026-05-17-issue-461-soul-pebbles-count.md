# Issue #461 — Soul Pebbles Count Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Surface the live `pebble_souls` count next to every soul in `SoulsListView` and `SoulPickerSheet`, by extending the existing PostgREST select with a nested aggregate and threading `SoulWithGlyph.pebblesCount` into the already-rendered `SoulItem` meta row.

**Architecture:** PostgREST nested aggregate (`pebbles_count:pebble_souls(count)`) appended to the existing `souls` select in three call sites. `SoulWithGlyph` gains `pebblesCount: Int` and a custom `init(from:)` that unwraps `[{count: Int}]`. View component `SoulItem` is unchanged — only its `count:` parameter goes from `nil` to `soul.pebblesCount` at the call site. No migration, no RPC, no Realtime.

**Tech Stack:** Swift 5.9 / SwiftUI / iOS 17+ / `supabase-swift` / PostgREST.

**Spec:** [docs/superpowers/specs/2026-05-17-issue-461-soul-pebbles-count-design.md](../specs/2026-05-17-issue-461-soul-pebbles-count-design.md)

**Branch:** `feat/461-soul-pebbles-count` (already created)

---

## File Map

- **Modify:** `apps/ios/Pebbles/Features/Profile/Models/SoulWithGlyph.swift` — add `pebblesCount`, custom `init(from:)`, explicit memberwise init.
- **Modify:** `apps/ios/Pebbles/Features/Shared/SoulItem.swift` — preview literal switches to the explicit memberwise init.
- **Modify:** `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift` — select string + `count:` argument.
- **Modify:** `apps/ios/Pebbles/Features/Path/SoulPickerSheet.swift` — select string + `count:` argument.
- **Modify:** `apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift` (path subject to verification — `find` it if not exactly there) — insert's `.select(...)` gains the aggregate.

No new files. No deletions.

## Verification approach

The iOS app has no Swift Testing target today (see `apps/ios/CLAUDE.md`). Each task's verification gate is:

1. `xcodebuild` compiles the `Pebbles` scheme without errors.
2. Manual smoke in the iOS simulator against the acceptance criteria from the spec § 7.

If you want to add a Swift Testing target as part of this work, stop and ask first — that's out of scope for #461.

---

## Task 1: Extend `SoulWithGlyph` with `pebblesCount`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Models/SoulWithGlyph.swift`
- Modify: `apps/ios/Pebbles/Features/Shared/SoulItem.swift` (preview literal only)

The custom `init(from:)` is the foundation — every other task assumes `SoulWithGlyph` has `pebblesCount`. Adding any initializer in the struct body suppresses the synthesized memberwise init, so we must re-declare it explicitly; the `SoulItem` preview uses that memberwise init and would otherwise stop compiling.

- [ ] **Step 1: Replace `SoulWithGlyph.swift` with the extended model**

Open `apps/ios/Pebbles/Features/Profile/Models/SoulWithGlyph.swift` and replace the full file with:

```swift
import Foundation

/// A soul together with its joined glyph and the live count of pebbles
/// linked to it. Decoded from a single PostgREST request:
///
///     supabase.from("souls")
///         .select("""
///             id, name, glyph_id,
///             glyphs(id, name, strokes, view_box),
///             pebbles_count:pebble_souls(count)
///         """)
///
/// PostgREST nests the joined glyph row under the relation name (`glyphs`)
/// and returns the aggregate as a single-element array of `{count: Int}`
/// objects (e.g. `[{ "count": 12 }]`). A soul with zero linked pebbles is
/// expected to come back as `[{ "count": 0 }]`; an empty array is treated
/// as 0 defensively.
struct SoulWithGlyph: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
    let glyphId: UUID
    let glyph: Glyph
    let pebblesCount: Int

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case glyphId      = "glyph_id"
        case glyph        = "glyphs"
        case pebblesCount = "pebbles_count"
    }

    private struct CountWrapper: Decodable { let count: Int }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id      = try c.decode(UUID.self,   forKey: .id)
        name    = try c.decode(String.self, forKey: .name)
        glyphId = try c.decode(UUID.self,   forKey: .glyphId)
        glyph   = try c.decode(Glyph.self,  forKey: .glyph)
        let wraps = try c.decodeIfPresent([CountWrapper].self, forKey: .pebblesCount) ?? []
        pebblesCount = wraps.first?.count ?? 0
    }

    // Required: providing any initializer in the struct body suppresses
    // the synthesized memberwise init. Preview and test code construct
    // instances without a network response, so re-declare it explicitly.
    init(id: UUID, name: String, glyphId: UUID, glyph: Glyph, pebblesCount: Int) {
        self.id = id
        self.name = name
        self.glyphId = glyphId
        self.glyph = glyph
        self.pebblesCount = pebblesCount
    }

    /// Convenience for code paths that already hold a `Soul` and need to
    /// drop the joined glyph (e.g. passing into `EditSoulSheet` which only
    /// needs the `Soul` shape).
    var soul: Soul {
        Soul(id: id, name: name, glyphId: glyphId)
    }
}
```

- [ ] **Step 2: Update the `SoulItem.swift` preview literal**

In `apps/ios/Pebbles/Features/Shared/SoulItem.swift`, find the `#Preview("All cases")` block. Replace the `let sample = SoulWithGlyph(...)` initializer with the explicit memberwise form and add `pebblesCount`:

Old:
```swift
    let sample = SoulWithGlyph(
        id: UUID(),
        name: "Molly",
        glyphId: SystemGlyph.default,
        glyph: Glyph(
            id: SystemGlyph.default,
            name: nil,
            strokes: [
                GlyphStroke(d: "M30,30 C60,10 140,10 170,30 S190,140 170,170 S60,190 30,170 S10,60 30,30", width: 6)
            ],
            viewBox: "0 0 200 200",
            userId: nil
        )
    )
```

New (only difference: trailing `pebblesCount:` argument):
```swift
    let sample = SoulWithGlyph(
        id: UUID(),
        name: "Molly",
        glyphId: SystemGlyph.default,
        glyph: Glyph(
            id: SystemGlyph.default,
            name: nil,
            strokes: [
                GlyphStroke(d: "M30,30 C60,10 140,10 170,30 S190,140 170,170 S60,190 30,170 S10,60 30,30", width: 6)
            ],
            viewBox: "0 0 200 200",
            userId: nil
        ),
        pebblesCount: 12
    )
```

Leave the rest of the preview body untouched — the three non-create cells already pass distinct integer literals to `SoulItem(count:)`, so they continue to demonstrate the meta row.

- [ ] **Step 3: Build**

Run:
```bash
xcodebuild \
  -workspace apps/ios/Pebbles.xcworkspace \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  build 2>&1 | tail -20
```

If the workspace path differs, use:
```bash
xcodebuild -list -project apps/ios/Pebbles.xcodeproj
```
to locate the scheme. The iOS project may use `.xcodeproj` directly — `project.yml` is source of truth per `apps/ios/CLAUDE.md`.

Expected: `** BUILD SUCCEEDED **`. No new warnings.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Models/SoulWithGlyph.swift \
        apps/ios/Pebbles/Features/Shared/SoulItem.swift
git commit -m "$(cat <<'EOF'
feat(ios): add pebblesCount to SoulWithGlyph

Refs #461

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Wire `SoulsListView` to the count

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift`

Two edits in the same file: extend the select string in `load()` to fetch the aggregate, then pass `item.pebblesCount` into `SoulItem` instead of `nil`.

- [ ] **Step 1: Extend the select string**

In `load()` (around line 124–129), find:
```swift
            let result: [SoulWithGlyph] = try await supabase.client
                .from("souls")
                .select("id, name, glyph_id, glyphs(id, name, strokes, view_box)")
                .order("name", ascending: true)
                .execute()
                .value
```

Replace the `.select(...)` line with:
```swift
                .select("id, name, glyph_id, glyphs(id, name, strokes, view_box), pebbles_count:pebble_souls(count)")
```

- [ ] **Step 2: Pass the live count to `SoulItem`**

Around line 103, find:
```swift
                        } label: {
                            SoulItem(case: .default, soul: item, count: nil)
                        }
```

Replace with:
```swift
                        } label: {
                            SoulItem(case: .default, soul: item, count: item.pebblesCount)
                        }
```

- [ ] **Step 3: Build**

```bash
xcodebuild \
  -workspace apps/ios/Pebbles.xcworkspace \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  build 2>&1 | tail -20
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift
git commit -m "$(cat <<'EOF'
feat(ios): wire pebbles count into SoulsListView

Refs #461

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Wire `SoulPickerSheet` to the count

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/SoulPickerSheet.swift`

Mirror of Task 2 in the picker.

- [ ] **Step 1: Extend the select string**

In `load()` (around line 126–131), find:
```swift
            let result: [SoulWithGlyph] = try await supabase.client
                .from("souls")
                .select("id, name, glyph_id, glyphs(id, name, strokes, view_box)")
                .order("name", ascending: true)
                .execute()
                .value
```

Replace the `.select(...)` line with:
```swift
                .select("id, name, glyph_id, glyphs(id, name, strokes, view_box), pebbles_count:pebble_souls(count)")
```

- [ ] **Step 2: Pass the live count to `SoulItem`**

Around line 84–92, find:
```swift
                        ForEach(souls) { soul in
                            SoulItem(
                                case: itemCase(for: soul.id),
                                soul: soul,
                                count: nil
                            ) {
                                toggle(soul.id)
                            }
                        }
```

Replace `count: nil` with `count: soul.pebblesCount`:
```swift
                        ForEach(souls) { soul in
                            SoulItem(
                                case: itemCase(for: soul.id),
                                soul: soul,
                                count: soul.pebblesCount
                            ) {
                                toggle(soul.id)
                            }
                        }
```

Do **not** modify the `.create` tile a few lines above — it still takes `soul: nil, count: nil`.

- [ ] **Step 3: Build**

```bash
xcodebuild \
  -workspace apps/ios/Pebbles.xcworkspace \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  build 2>&1 | tail -20
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/SoulPickerSheet.swift
git commit -m "$(cat <<'EOF'
feat(ios): wire pebbles count into SoulPickerSheet

Refs #461

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Include the aggregate in `CreateSoulSheet`'s insert

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift`

When `CreateSoulSheet` inserts a new soul, the returned `SoulWithGlyph` flows to the picker's `souls.append(inserted)`. We want the inserted value's shape to match the list query so `inserted.pebblesCount == 0` directly from the server (a fresh soul has no `pebble_souls` rows yet, so the aggregate returns `[{count: 0}]`). The custom `init(from:)` would also default to 0 if the key were missing, but symmetry is worth the trivial one-liner.

- [ ] **Step 1: Extend the insert's select**

In `save()`, find the insert chain:
```swift
            let inserted: SoulWithGlyph = try await supabase.client
                .from("souls")
                .insert(payload)
                .select("id, name, glyph_id, glyphs(id, name, strokes, view_box)")
                .single()
                .execute()
                .value
```

Replace the `.select(...)` line with:
```swift
                .select("id, name, glyph_id, glyphs(id, name, strokes, view_box), pebbles_count:pebble_souls(count)")
```

Nothing else in `save()` changes.

- [ ] **Step 2: Build**

```bash
xcodebuild \
  -workspace apps/ios/Pebbles.xcworkspace \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  build 2>&1 | tail -20
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift
git commit -m "$(cat <<'EOF'
feat(ios): include pebbles count in CreateSoulSheet insert

Refs #461

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Simulator smoke test against acceptance criteria

**Files:** None modified. Verification only.

This task is the gate that all the wiring works end-to-end against a real Supabase backend. Run through every bullet from the spec § 7 in order. If any fails, do **not** proceed — open the failing scenario, diagnose, and either fix in place (small) or stop and report (large).

- [ ] **Step 1: Launch the app in the simulator**

```bash
xcodebuild \
  -workspace apps/ios/Pebbles.xcworkspace \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -configuration Debug \
  build 2>&1 | tail -5
```

Then open the workspace in Xcode (or use `xcrun simctl` if you prefer headless) and Run on a simulator with a signed-in user that has at least 2 souls and at least 1 pebble linking to one of them.

- [ ] **Step 2: Verify Souls list (Profile → Souls)**

Expected:
- Each cell shows `🐚 N` below the soul name, where `N` is the live count.
- Souls with zero pebbles show `🐚 0`.
- Glyph + name layout unchanged from before.

If any soul shows no meta row, the decoding is silently failing — capture the response shape via Xcode's network debugger and revisit Task 1.

- [ ] **Step 3: Verify Soul picker (from any pebble form's "Add souls" button)**

Expected:
- Same `🐚 N` meta row on every soul tile.
- Tapping to select toggles between `.default` / `.selected` / `.unselected` cases — the count remains visible in all three.
- The `+ New` (`.create`) tile shows no count and no glyph stroke (unchanged).

- [ ] **Step 4: Verify create flow**

Create a new soul via `+ New` → save.

Expected: it lands in the picker (and, after a back+forward navigation to the list, in the souls list) with `🐚 0`.

- [ ] **Step 5: Verify increment/decrement on pebble create/delete**

Create a pebble linking 2 souls. Navigate back to Souls list (or relaunch the screen). Expected: both linked souls' counts are +1 vs. step 2.

Delete the pebble. Reopen Souls list. Expected: both counts return to their pre-create values.

- [ ] **Step 6: Verify no regression elsewhere**

- Path screen: `SoulPill` chips render as before (no count, no layout shift).
- Pebble detail / edit: `PebbleMetaPill` unchanged.
- `Localizable.xcstrings`: no changes since this PR introduced no new strings. Confirm with:
  ```bash
  git diff main -- apps/ios/Pebbles/Resources/Localizable.xcstrings
  ```
  Expected: empty diff.

- [ ] **Step 7: Final build (clean)**

```bash
xcodebuild \
  -workspace apps/ios/Pebbles.xcworkspace \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  clean build 2>&1 | tail -10
```

Expected: `** BUILD SUCCEEDED **` with no new warnings.

- [ ] **Step 8: Push and open the PR**

```bash
git push -u origin feat/461-soul-pebbles-count
```

Then open a PR titled `feat(ios): wire per-soul pebbles count` with body starting `Resolves #461`. Apply labels `feat` + `db` + `ios` (inherited from the issue) and milestone `M32 · iOS Quality`. **Confirm labels and milestone with the user before clicking create** — per project conventions, no PR ships without them.
