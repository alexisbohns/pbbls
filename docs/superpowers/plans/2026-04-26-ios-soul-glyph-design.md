# iOS — Soul Glyph Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Attribute a glyph to every soul, surface it in a 3-column grid on the Souls list, in the soul detail header, and inside create/edit sheets — backed by a new `souls.glyph_id` column.

**Architecture:** A nullable→backfill→tighten migration adds `souls.glyph_id` with the system default glyph as the FK target. iOS reads souls with a nested PostgREST select that joins `glyphs(id, strokes, view_box)` in one round-trip and decodes into a new `SoulWithGlyph` value. Create/edit sheets reuse the existing `GlyphPickerSheet` + `GlyphCarveSheet` flow; `SoulDraft` mirrors `PebbleDraft` and carries an in-memory `currentGlyph` cache so the form thumbnail renders without a refetch. Saves stay direct single-table INSERT / UPDATE per `AGENTS.md` (no RPC).

**Tech Stack:** SwiftUI (iOS 17+, `@Observable`), Supabase Swift SDK, PostgREST. Postgres migration via `packages/supabase/supabase/migrations/`. Swift Testing for unit tests. Strings in `Localizable.xcstrings` (en + fr).

**Spec:** `docs/superpowers/specs/2026-04-25-ios-soul-glyph-design.md` (issue #298).

---

## File Structure

**Created:**
- `packages/supabase/supabase/migrations/20260426000000_add_glyph_to_souls.sql` — adds nullable `glyph_id`, seeds default glyph, backfills, sets NOT NULL + default.
- `apps/ios/Pebbles/Features/Profile/Models/SoulDraft.swift` — value-type form state (mirrors `PebbleDraft`).
- `apps/ios/Pebbles/Features/Profile/Models/SoulWithGlyph.swift` — decoded shape for the joined select used by list and detail views.
- `apps/ios/Pebbles/Features/Profile/Lists/SoulGridCell.swift` — pure presentational cell: glyph thumbnail above the soul's name.
- `apps/ios/Pebbles/Features/Glyph/Utils/SystemGlyph.swift` — single source of truth for the default glyph UUID (avoids stringly-typed UUIDs across files).
- `apps/ios/PebblesTests/SoulWithGlyphDecodingTests.swift` — verifies the joined payload decodes correctly.
- `apps/ios/PebblesTests/SoulInsertPayloadEncodingTests.swift` — verifies create payload emits `glyph_id` snake-cased.
- `apps/ios/PebblesTests/SoulUpdatePayloadEncodingTests.swift` — verifies update payload emits `name` and `glyph_id` snake-cased.

**Modified:**
- `packages/supabase/types/database.ts` — regenerated via `npm run db:types --workspace=packages/supabase`.
- `apps/ios/Pebbles/Features/Path/Models/Soul.swift` — adds `glyphId: UUID` (non-optional).
- `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift` — replaces `List` with a 3-column `LazyVGrid` of `SoulGridCell`; switches the fetch to the joined select; refactors local state to `[SoulWithGlyph]`.
- `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift` — adds compact glyph header, switches the soul fetch to include the joined glyph, passes `currentGlyph` to `EditSoulSheet`.
- `apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift` — adds Glyph row, picker presentation, default-glyph fetch on `.task`, `glyph_id` on save.
- `apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift` — adds Glyph row pre-filled from the parent's `currentGlyph`, picker presentation, `glyph_id` on save.
- `apps/ios/Pebbles/Resources/Localizable.xcstrings` — adds new strings: "Glyph", "Tap to choose", and a pluralised `%lld pebbles` (en/fr).
- `docs/arkaik/bundle.json` — `DM-soul` description gets a one-line note that souls now carry a glyph; `V-souls-list` description switches "flat list" → "3-column grid".

**No changes needed to:**
- `GlyphPickerSheet.swift` / `GlyphCarveSheet.swift` / `GlyphThumbnail.swift` — already accept the right props and behave as required.
- `apps/ios/project.yml` — xcodegen auto-discovers under `Pebbles/` and `PebblesTests/`. New files require `xcodegen generate` (or `npm run generate --workspace=@pbbls/ios`) before the next Xcode build.

---

## Task 1: Database migration — add `souls.glyph_id`

**Files:**
- Create: `packages/supabase/supabase/migrations/20260426000000_add_glyph_to_souls.sql`
- Modify: `packages/supabase/types/database.ts` (regenerated, not hand-edited)

The migration inserts the system default glyph if missing, adds the column nullable, backfills, then tightens to NOT NULL with a default. `ON DELETE RESTRICT` prevents a soul from being silently stranded if the default glyph were ever deleted.

The default glyph UUID `4759c37c-68a6-46a6-b4fc-046bd0316752` already exists in the remote DB as one of the 18 system glyphs seeded by `20260415000001_remote_pebble_engine.sql`. The `INSERT … ON CONFLICT DO NOTHING` here is purely defensive in case a fresh local environment runs migrations before the seed.

- [ ] **Step 1: Create the migration file**

Create `packages/supabase/supabase/migrations/20260426000000_add_glyph_to_souls.sql` with:

```sql
-- Migration: Add glyph_id to souls (issue #298)
--
-- Souls today carry only a name. This migration adds an FK to glyphs so each
-- soul has a visual identity (rendered in the iOS Souls grid + detail header).
--
-- Strategy:
--   1. Defensively guarantee the system default glyph exists.
--   2. Add column nullable (so existing rows don't violate the constraint).
--   3. Backfill all rows to the system default.
--   4. Tighten: NOT NULL + default = system default glyph.
--
-- ON DELETE RESTRICT: glyphs aren't user-deletable today, but if that changes
-- the constraint stops a deletion from silently stranding a soul.
-- The default glyph itself must never be deletable while any soul references it.

-- 1. Defensively ensure the system default glyph row exists.
--    The remote DB already has it (seeded by 20260415000001), but a fresh local
--    DB might not have run that seed yet. ON CONFLICT keeps it idempotent.
insert into public.glyphs (id, user_id, shape_id, strokes, view_box)
values (
  '4759c37c-68a6-46a6-b4fc-046bd0316752',
  null,
  null,
  '[]'::jsonb,
  '0 0 200 200'
)
on conflict (id) do nothing;

-- 2. Add column nullable, FK to glyphs.
alter table public.souls
  add column glyph_id uuid references public.glyphs(id) on delete restrict;

-- 3. Backfill existing rows to the system default.
update public.souls
set glyph_id = '4759c37c-68a6-46a6-b4fc-046bd0316752'
where glyph_id is null;

-- 4. Tighten: NOT NULL + default.
alter table public.souls
  alter column glyph_id set not null,
  alter column glyph_id set default '4759c37c-68a6-46a6-b4fc-046bd0316752';
```

- [ ] **Step 2: Apply the migration to the remote DB**

This project deploys to remote Supabase rather than running a local container (per the user's avoid-Docker preference noted in MEMORY.md / `feedback_avoid_docker.md`). Apply via the workspace's existing migration push command — confirm with the user before running if uncertain. Typical command:

```bash
npm run db:push --workspace=packages/supabase
```

Expected: migration applies cleanly with no errors. If `db:push` is named differently in this workspace, run `npm run -w packages/supabase --silent run-script ls 2>/dev/null` or read `packages/supabase/package.json` to find the right script.

- [ ] **Step 3: Regenerate TypeScript types**

Run from the repo root:

```bash
npm run db:types --workspace=packages/supabase
```

Expected: `packages/supabase/types/database.ts` now lists `glyph_id: string` (not `string | null`) under `souls.Row` and `souls.Insert` (with default). Verify by grepping:

```bash
grep -A 12 'souls: {' packages/supabase/types/database.ts | head -40
```

- [ ] **Step 4: Commit migration + regenerated types**

```bash
git add packages/supabase/supabase/migrations/20260426000000_add_glyph_to_souls.sql packages/supabase/types/database.ts
git commit -m "feat(db): add glyph_id to souls (#298)"
```

---

## Task 2: Add `glyphId` to the iOS `Soul` model

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/Soul.swift`

The `Soul` value type used today decodes only `id, name`. Selectors that don't request `glyph_id` would now break decoding because `glyphId` is non-optional. Every `select(...)` on `souls` is updated in later tasks; this task lands the model change in lockstep.

- [ ] **Step 1: Update `Soul.swift`**

Replace contents with:

```swift
import Foundation

struct Soul: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
    let glyphId: UUID

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case glyphId = "glyph_id"
    }
}
```

- [ ] **Step 2: Build the iOS target to surface every call site that breaks**

```bash
xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build 2>&1 | head -40
```

Expected: the build fails with errors at every site that constructs `Soul(...)` without `glyphId` and at every `.from("souls").select("id, name")` call site (decoding-time errors only show up at runtime; compile errors come from constructor sites and SwiftUI previews). Note each error site — they're all addressed in subsequent tasks.

If the workspace name is different (e.g. `Pebbles.xcodeproj`), discover via:

```bash
ls apps/ios/*.xcworkspace apps/ios/*.xcodeproj 2>/dev/null
```

- [ ] **Step 3: Update `Soul(...)` constructor sites in previews and tests**

Search for every constructor:

```bash
grep -rn "Soul(id:" apps/ios/Pebbles apps/ios/PebblesTests
```

For each match, add `glyphId: UUID()` (or a fixed UUID like `UUID(uuidString: "4759c37c-68a6-46a6-b4fc-046bd0316752")!` for previews). At the time of writing the known sites are the `#Preview` blocks in `SoulsListView.swift`, `SoulDetailView.swift`, `EditSoulSheet.swift` — verify with the grep above.

Example fix for `EditSoulSheet.swift` preview:

```swift
#Preview {
    EditSoulSheet(
        soul: Soul(id: UUID(), name: "Preview", glyphId: UUID()),
        onSaved: {}
    )
    .environment(SupabaseService())
}
```

- [ ] **Step 4: Re-run the build**

```bash
xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build 2>&1 | tail -20
```

Expected: build succeeds. (Runtime decode errors will appear in the next tasks until the `select(...)` strings are widened to include `glyph_id`.)

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/Soul.swift apps/ios/Pebbles/Features/Profile/
git commit -m "feat(ios): add glyphId to Soul model"
```

---

## Task 3: Add `SystemGlyph` constant + `SoulWithGlyph` decode model

**Files:**
- Create: `apps/ios/Pebbles/Features/Glyph/Utils/SystemGlyph.swift`
- Create: `apps/ios/Pebbles/Features/Profile/Models/SoulWithGlyph.swift`
- Create: `apps/ios/PebblesTests/SoulWithGlyphDecodingTests.swift`

Centralise the default glyph UUID so the iOS code never duplicates the literal. `SoulWithGlyph` decodes the joined PostgREST payload (`select("id, name, glyph_id, glyphs(id, strokes, view_box)")`) into one value used by the list and detail views.

PostgREST returns the joined relation as a nested object keyed by the relation name (`glyphs`). The decoder needs a custom `init(from:)` because `Glyph.CodingKeys` doesn't include the parent's `name`/`id` fields — the nested `glyphs` JSON object decodes via `Glyph`'s own `Decodable` synthesis.

- [ ] **Step 1: Create the `SystemGlyph` constant**

Create `apps/ios/Pebbles/Features/Glyph/Utils/SystemGlyph.swift`:

```swift
import Foundation

/// UUIDs for the system glyphs seeded server-side (migration
/// `20260415000001_remote_pebble_engine.sql` and re-asserted by
/// `20260426000000_add_glyph_to_souls.sql`).
///
/// `default` is the canonical fallback used when a soul or domain has no
/// user-carved glyph attached. iOS uses it to seed `SoulDraft.glyphId`
/// when creating a new soul, and the migration uses it as the column default.
enum SystemGlyph {
    static let `default` = UUID(uuidString: "4759c37c-68a6-46a6-b4fc-046bd0316752")!
}
```

- [ ] **Step 2: Create the `SoulWithGlyph` value**

Create `apps/ios/Pebbles/Features/Profile/Models/SoulWithGlyph.swift`:

```swift
import Foundation

/// A soul together with its joined glyph, decoded from a single PostgREST
/// request:
///
///     supabase.from("souls")
///         .select("id, name, glyph_id, glyphs(id, strokes, view_box)")
///
/// PostgREST nests the joined row under the relation name (`glyphs`).
struct SoulWithGlyph: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
    let glyphId: UUID
    let glyph: Glyph

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case glyphId = "glyph_id"
        case glyph = "glyphs"
    }

    /// Convenience for code paths that already hold a `Soul` and need to
    /// drop the joined glyph (e.g. passing into `EditSoulSheet` which only
    /// needs the `Soul` shape).
    var soul: Soul {
        Soul(id: id, name: name, glyphId: glyphId)
    }
}
```

- [ ] **Step 3: Write the decoding test (failing first)**

Create `apps/ios/PebblesTests/SoulWithGlyphDecodingTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("SoulWithGlyph decoding")
struct SoulWithGlyphDecodingTests {

    private let soulId = UUID(uuidString: "11111111-1111-1111-1111-111111111111")!
    private let glyphId = UUID(uuidString: "4759c37c-68a6-46a6-b4fc-046bd0316752")!

    @Test("decodes the joined PostgREST payload for the system default glyph")
    func decodesSystemDefault() throws {
        let json = """
        {
          "id": "\(soulId.uuidString)",
          "name": "Alex",
          "glyph_id": "\(glyphId.uuidString)",
          "glyphs": {
            "id": "\(glyphId.uuidString)",
            "name": null,
            "strokes": [],
            "view_box": "0 0 200 200"
          }
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(SoulWithGlyph.self, from: json)
        #expect(decoded.id == soulId)
        #expect(decoded.name == "Alex")
        #expect(decoded.glyphId == glyphId)
        #expect(decoded.glyph.id == glyphId)
        #expect(decoded.glyph.viewBox == "0 0 200 200")
        #expect(decoded.glyph.strokes.isEmpty)
    }

    @Test("decodes a soul with a user-carved glyph that has strokes")
    func decodesUserGlyph() throws {
        let userGlyphId = UUID()
        let json = """
        {
          "id": "\(soulId.uuidString)",
          "name": "Sam",
          "glyph_id": "\(userGlyphId.uuidString)",
          "glyphs": {
            "id": "\(userGlyphId.uuidString)",
            "name": "wave",
            "strokes": [{"d": "M0,0 L10,10", "width": 6}],
            "view_box": "0 0 200 200"
          }
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(SoulWithGlyph.self, from: json)
        #expect(decoded.glyph.name == "wave")
        #expect(decoded.glyph.strokes.count == 1)
        #expect(decoded.glyph.strokes.first?.d == "M0,0 L10,10")
    }

    @Test("soul accessor strips the joined glyph")
    func soulAccessor() throws {
        let json = """
        {
          "id": "\(soulId.uuidString)",
          "name": "Alex",
          "glyph_id": "\(glyphId.uuidString)",
          "glyphs": { "id": "\(glyphId.uuidString)", "name": null, "strokes": [], "view_box": "0 0 200 200" }
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(SoulWithGlyph.self, from: json)
        #expect(decoded.soul.id == soulId)
        #expect(decoded.soul.name == "Alex")
        #expect(decoded.soul.glyphId == glyphId)
    }
}
```

- [ ] **Step 4: Regenerate the Xcode project so new files are included**

```bash
npm run generate --workspace=@pbbls/ios
```

Expected: `xcodegen generate` runs and refreshes the pbxproj.

- [ ] **Step 5: Run the tests**

```bash
xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 16' test -only-testing:PebblesTests/SoulWithGlyphDecodingTests 2>&1 | tail -30
```

Expected: all three tests pass.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Glyph/Utils/SystemGlyph.swift apps/ios/Pebbles/Features/Profile/Models/SoulWithGlyph.swift apps/ios/PebblesTests/SoulWithGlyphDecodingTests.swift apps/ios/Pebbles.xcodeproj
git commit -m "feat(ios): add SystemGlyph constant and SoulWithGlyph decode model"
```

---

## Task 4: `SoulDraft` form-state value

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Models/SoulDraft.swift`

A small value type held by `@State` in the create/edit sheets. Mirrors `PebbleDraft` so the patterns match.

- [ ] **Step 1: Create `SoulDraft.swift`**

```swift
import Foundation

/// In-progress form state for the create/edit-soul sheets.
/// A value type held in `@State`. `currentGlyph` is the in-memory cache
/// so the form's thumbnail row renders without a glyph-by-id refetch
/// after the picker returns a selection.
struct SoulDraft {
    var name: String = ""
    var glyphId: UUID = SystemGlyph.default
    var currentGlyph: Glyph?

    /// True when every mandatory field is set.
    var isValid: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty
    }
}

extension SoulDraft {
    /// Build a prefilled draft from a fetched `SoulWithGlyph`.
    /// Used by `EditSoulSheet` to populate the form with current values.
    init(from soulWithGlyph: SoulWithGlyph) {
        self.name = soulWithGlyph.name
        self.glyphId = soulWithGlyph.glyphId
        self.currentGlyph = soulWithGlyph.glyph
    }
}
```

- [ ] **Step 2: Regenerate the Xcode project**

```bash
npm run generate --workspace=@pbbls/ios
```

- [ ] **Step 3: Build to confirm it compiles**

```bash
xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build 2>&1 | tail -10
```

Expected: build succeeds.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Models/SoulDraft.swift apps/ios/Pebbles.xcodeproj
git commit -m "feat(ios): add SoulDraft form-state value"
```

---

## Task 5: Insert/update payload encoding tests

**Files:**
- Create: `apps/ios/PebblesTests/SoulInsertPayloadEncodingTests.swift`
- Create: `apps/ios/PebblesTests/SoulUpdatePayloadEncodingTests.swift`

Lock down the wire format before changing the sheet code so the round-trip is provably correct. The payload types themselves are private inside the sheets today; the tests force us to lift them into a shared file in Task 6.

- [ ] **Step 1: Write the failing insert encoding test**

Create `apps/ios/PebblesTests/SoulInsertPayloadEncodingTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("SoulInsertPayload encoding")
struct SoulInsertPayloadEncodingTests {

    private let userId = UUID(uuidString: "11111111-1111-1111-1111-111111111111")!
    private let glyphId = UUID(uuidString: "4759c37c-68a6-46a6-b4fc-046bd0316752")!

    private func encode(_ payload: SoulInsertPayload) throws -> [String: Any] {
        let data = try JSONEncoder().encode(payload)
        let object = try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
        return try #require(object as? [String: Any])
    }

    @Test("encodes user_id, name, glyph_id with snake_case keys")
    func snakeCaseKeys() throws {
        let payload = SoulInsertPayload(userId: userId, name: "Alex", glyphId: glyphId)
        let json = try encode(payload)
        #expect(json["user_id"] as? String == userId.uuidString)
        #expect(json["name"] as? String == "Alex")
        #expect(json["glyph_id"] as? String == glyphId.uuidString)
    }

    @Test("does not emit a top-level id field")
    func noIdField() throws {
        let payload = SoulInsertPayload(userId: userId, name: "Alex", glyphId: glyphId)
        let json = try encode(payload)
        #expect(json["id"] == nil)
    }
}
```

- [ ] **Step 2: Write the failing update encoding test**

Create `apps/ios/PebblesTests/SoulUpdatePayloadEncodingTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("SoulUpdatePayload encoding")
struct SoulUpdatePayloadEncodingTests {

    private let glyphId = UUID(uuidString: "4759c37c-68a6-46a6-b4fc-046bd0316752")!

    private func encode(_ payload: SoulUpdatePayload) throws -> [String: Any] {
        let data = try JSONEncoder().encode(payload)
        let object = try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
        return try #require(object as? [String: Any])
    }

    @Test("encodes name and glyph_id with snake_case keys")
    func snakeCaseKeys() throws {
        let payload = SoulUpdatePayload(name: "Alex", glyphId: glyphId)
        let json = try encode(payload)
        #expect(json["name"] as? String == "Alex")
        #expect(json["glyph_id"] as? String == glyphId.uuidString)
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail (types not yet declared)**

```bash
xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 16' test -only-testing:PebblesTests/SoulInsertPayloadEncodingTests -only-testing:PebblesTests/SoulUpdatePayloadEncodingTests 2>&1 | tail -10
```

Expected: build failure with "Cannot find 'SoulInsertPayload' / 'SoulUpdatePayload' in scope". This is intentional — Task 6 promotes them into a shared file.

---

## Task 6: Promote `SoulInsertPayload` / `SoulUpdatePayload` to shared file

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Models/SoulPayloads.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift` (remove the private struct)
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift` (remove the private struct)

Today both payload types live as `private` siblings of the sheets, which the unit tests can't reach. Lift both into a shared file under Profile/Models. This also makes them symmetric — both carry `glyph_id`.

- [ ] **Step 1: Create `SoulPayloads.swift`**

```swift
import Foundation

/// Body for `INSERT INTO public.souls`. RLS requires `user_id` to match
/// `auth.uid()`, so the sheet supplies it from the active session.
struct SoulInsertPayload: Encodable {
    let userId: UUID
    let name: String
    let glyphId: UUID

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case name
        case glyphId = "glyph_id"
    }
}

/// Body for `UPDATE public.souls SET ... WHERE id = ?`. Owned by the
/// caller — `id` is supplied via the `.eq("id", value:)` filter, not in the body.
struct SoulUpdatePayload: Encodable {
    let name: String
    let glyphId: UUID

    enum CodingKeys: String, CodingKey {
        case name
        case glyphId = "glyph_id"
    }
}
```

- [ ] **Step 2: Remove the private payload struct from `CreateSoulSheet.swift`**

Open `apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift`. Delete this block (currently lines 87–95):

```swift
private struct SoulInsertPayload: Encodable {
    let userId: UUID
    let name: String

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case name
    }
}
```

(This file gets a more substantial rewrite in Task 8 — for now we just delete the duplicate struct so the build picks up the new shared one. The existing call to `SoulInsertPayload(userId:, name:)` will fail to compile because the new shared struct now requires `glyphId`. That call site is rewritten in Task 8.)

- [ ] **Step 3: Remove the private payload struct from `EditSoulSheet.swift`**

Open `apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift`. Delete this block (currently lines 91–93):

```swift
private struct SoulUpdatePayload: Encodable {
    let name: String
}
```

The existing call to `SoulUpdatePayload(name:)` will fail to compile — that call site is rewritten in Task 9.

- [ ] **Step 4: Regenerate Xcode project + run tests**

```bash
npm run generate --workspace=@pbbls/ios
xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 16' test -only-testing:PebblesTests/SoulInsertPayloadEncodingTests -only-testing:PebblesTests/SoulUpdatePayloadEncodingTests 2>&1 | tail -15
```

Expected: tests pass. The full app build will still fail at the call sites in `CreateSoulSheet`/`EditSoulSheet` — that's resolved in Tasks 8 + 9.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Models/SoulPayloads.swift apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift apps/ios/PebblesTests/SoulInsertPayloadEncodingTests.swift apps/ios/PebblesTests/SoulUpdatePayloadEncodingTests.swift apps/ios/Pebbles.xcodeproj
git commit -m "test(ios): cover SoulInsertPayload and SoulUpdatePayload encoding"
```

---

## Task 7: `SoulGridCell` view

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Lists/SoulGridCell.swift`

Pure presentational cell used by `SoulsListView`. Square `GlyphThumbnail` above a single-line truncating name.

- [ ] **Step 1: Create the cell**

```swift
import SwiftUI

/// One cell in the Souls 3-column grid. Square glyph thumbnail above a
/// single-line truncating name. Tap target wraps the entire cell — tap
/// behaviour is owned by the parent `NavigationLink`, this view is purely
/// visual.
struct SoulGridCell: View {
    let soul: SoulWithGlyph

    var body: some View {
        VStack(spacing: 8) {
            GlyphThumbnail(strokes: soul.glyph.strokes, side: 96)
                .accessibilityHidden(true)
            Text(soul.name)
                .font(.callout)
                .lineLimit(1)
                .truncationMode(.tail)
                .frame(maxWidth: .infinity)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(soul.name)
    }
}

#Preview {
    SoulGridCell(
        soul: SoulWithGlyph(
            id: UUID(),
            name: "Preview Soul",
            glyphId: SystemGlyph.default,
            glyph: Glyph(
                id: SystemGlyph.default,
                name: nil,
                strokes: [GlyphStroke(d: "M30,30 L170,170", width: 6)],
                viewBox: "0 0 200 200"
            )
        )
    )
    .padding()
}
```

- [ ] **Step 2: Regenerate Xcode project + build**

```bash
npm run generate --workspace=@pbbls/ios
xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build 2>&1 | tail -10
```

Expected: still failing at the `CreateSoulSheet` / `EditSoulSheet` call sites from Task 6, but no new errors from this file.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Lists/SoulGridCell.swift apps/ios/Pebbles.xcodeproj
git commit -m "feat(ios): add SoulGridCell view"
```

---

## Task 8: Rewrite `CreateSoulSheet` with glyph row

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift`

Switch `@State` from `name: String` to a `SoulDraft`. Add a "Glyph" `Section` with a button that opens `GlyphPickerSheet`. Eagerly fetch the system default glyph on `.task` so the row renders the correct thumbnail before the user taps anything. On save, send the new shared `SoulInsertPayload` with `glyphId`.

- [ ] **Step 1: Replace the file body**

Replace contents of `apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift` with:

```swift
import SwiftUI
import os

/// Sheet for creating a new soul. Name + glyph row, save/cancel toolbar.
/// INSERT goes directly to `public.souls` — RLS scopes to the current user.
/// `glyph_id` is initialised to the system default; the user can swap it
/// via `GlyphPickerSheet` (which itself can carve a fresh glyph).
struct CreateSoulSheet: View {
    let onCreated: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var draft = SoulDraft()
    @State private var isSaving = false
    @State private var saveError: String?
    @State private var isPresentingPicker = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.souls")

    var body: some View {
        NavigationStack {
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
            .navigationTitle("New soul")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if isSaving {
                        ProgressView()
                    } else {
                        Button("Save") {
                            Task { await save() }
                        }
                        .disabled(!draft.isValid)
                    }
                }
            }
            .pebblesScreen()
            .task { await loadDefaultGlyph() }
            .sheet(isPresented: $isPresentingPicker) {
                GlyphPickerSheet(
                    currentGlyphId: draft.glyphId,
                    onSelected: { selected in
                        // Picker returns the chosen glyph's id. Update draft
                        // and refetch the glyph so the row's thumbnail
                        // re-renders without waiting for a list reload.
                        if let selected {
                            draft.glyphId = selected
                            Task { await loadGlyph(id: selected) }
                        }
                    }
                )
            }
        }
    }

    private func loadDefaultGlyph() async {
        // Idempotent: only fetch if we still hold the system default and
        // haven't already loaded its strokes.
        guard draft.glyphId == SystemGlyph.default,
              draft.currentGlyph?.id != SystemGlyph.default else { return }
        await loadGlyph(id: SystemGlyph.default)
    }

    private func loadGlyph(id: UUID) async {
        do {
            let fetched: Glyph = try await supabase.client
                .from("glyphs")
                .select("id, name, strokes, view_box")
                .eq("id", value: id)
                .single()
                .execute()
                .value
            draft.currentGlyph = fetched
        } catch {
            logger.error("create soul: load glyph failed: \(error.localizedDescription, privacy: .private)")
            // Leave currentGlyph as-is; the empty thumbnail still works as a tap target.
        }
    }

    private func save() async {
        guard draft.isValid else { return }
        guard let userId = supabase.session?.user.id else {
            logger.error("create soul: no session")
            saveError = "You're signed out. Please sign in again."
            return
        }
        isSaving = true
        saveError = nil
        do {
            let payload = SoulInsertPayload(
                userId: userId,
                name: draft.name.trimmingCharacters(in: .whitespacesAndNewlines),
                glyphId: draft.glyphId
            )
            try await supabase.client
                .from("souls")
                .insert(payload)
                .execute()
            onCreated()
            dismiss()
        } catch {
            logger.error("create soul failed: \(error.localizedDescription, privacy: .private)")
            saveError = "Couldn't save the soul. Please try again."
            isSaving = false
        }
    }
}

/// Row used by both create and edit soul sheets. Shows the current glyph
/// thumbnail (or a dashed placeholder when not yet loaded) + label + chevron.
private struct GlyphRow: View {
    let glyph: Glyph?
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                if let glyph {
                    GlyphThumbnail(strokes: glyph.strokes, side: 32)
                        .accessibilityHidden(true)
                } else {
                    RoundedRectangle(cornerRadius: 6)
                        .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [3]))
                        .frame(width: 32, height: 32)
                        .foregroundStyle(.secondary)
                }
                Text("Tap to choose")
                    .foregroundStyle(.primary)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
            }
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    CreateSoulSheet(onCreated: {})
        .environment(SupabaseService())
}
```

- [ ] **Step 2: Build to confirm `CreateSoulSheet` compiles**

```bash
xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build 2>&1 | tail -10
```

Expected: still failing only at the `EditSoulSheet` save site (resolved in Task 9).

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift
git commit -m "feat(ios): add glyph row to create-soul sheet (#298)"
```

---

## Task 9: Rewrite `EditSoulSheet` with glyph row

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift`

Same pattern as `CreateSoulSheet`. Init now takes a `SoulWithGlyph` so the picker row can render immediately from the parent's already-fetched glyph (no second fetch). The `GlyphRow` view is duplicated as a private struct here for now; if a third caller appears, lift it into `Profile/Components/`.

- [ ] **Step 1: Replace the file body**

Replace contents of `apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift` with:

```swift
import SwiftUI
import os

/// Sheet for editing a soul. Name + glyph row, save/cancel toolbar.
/// UPDATE goes directly to `public.souls` — RLS scopes to the owner.
struct EditSoulSheet: View {
    let original: SoulWithGlyph
    let onSaved: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var draft: SoulDraft
    @State private var isSaving = false
    @State private var saveError: String?
    @State private var isPresentingPicker = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.souls")

    init(original: SoulWithGlyph, onSaved: @escaping () -> Void) {
        self.original = original
        self.onSaved = onSaved
        self._draft = State(initialValue: SoulDraft(from: original))
    }

    private var canSave: Bool {
        guard draft.isValid else { return false }
        let trimmed = draft.name.trimmingCharacters(in: .whitespacesAndNewlines)
        let nameChanged = trimmed != original.name
        let glyphChanged = draft.glyphId != original.glyphId
        return nameChanged || glyphChanged
    }

    var body: some View {
        NavigationStack {
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
            .navigationTitle("Edit soul")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if isSaving {
                        ProgressView()
                    } else {
                        Button("Save") {
                            Task { await save() }
                        }
                        .disabled(!canSave)
                    }
                }
            }
            .pebblesScreen()
            .sheet(isPresented: $isPresentingPicker) {
                GlyphPickerSheet(
                    currentGlyphId: draft.glyphId,
                    onSelected: { selected in
                        if let selected {
                            draft.glyphId = selected
                            Task { await loadGlyph(id: selected) }
                        }
                    }
                )
            }
        }
    }

    private func loadGlyph(id: UUID) async {
        do {
            let fetched: Glyph = try await supabase.client
                .from("glyphs")
                .select("id, name, strokes, view_box")
                .eq("id", value: id)
                .single()
                .execute()
                .value
            draft.currentGlyph = fetched
        } catch {
            logger.error("edit soul: load glyph failed: \(error.localizedDescription, privacy: .private)")
        }
    }

    private func save() async {
        guard canSave else { return }
        isSaving = true
        saveError = nil
        do {
            let payload = SoulUpdatePayload(
                name: draft.name.trimmingCharacters(in: .whitespacesAndNewlines),
                glyphId: draft.glyphId
            )
            try await supabase.client
                .from("souls")
                .update(payload)
                .eq("id", value: original.id)
                .execute()
            onSaved()
            dismiss()
        } catch {
            logger.error("update soul failed: \(error.localizedDescription, privacy: .private)")
            saveError = "Couldn't save your changes. Please try again."
            isSaving = false
        }
    }
}

/// Row shared with `CreateSoulSheet`. Kept duplicated as `private` here because
/// only two callers exist; lift to `Profile/Components/` if a third appears.
private struct GlyphRow: View {
    let glyph: Glyph?
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                if let glyph {
                    GlyphThumbnail(strokes: glyph.strokes, side: 32)
                        .accessibilityHidden(true)
                } else {
                    RoundedRectangle(cornerRadius: 6)
                        .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [3]))
                        .frame(width: 32, height: 32)
                        .foregroundStyle(.secondary)
                }
                Text("Tap to choose")
                    .foregroundStyle(.primary)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
            }
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    EditSoulSheet(
        original: SoulWithGlyph(
            id: UUID(),
            name: "Preview",
            glyphId: SystemGlyph.default,
            glyph: Glyph(
                id: SystemGlyph.default,
                name: nil,
                strokes: [],
                viewBox: "0 0 200 200"
            )
        ),
        onSaved: {}
    )
    .environment(SupabaseService())
}
```

- [ ] **Step 2: Build**

```bash
xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build 2>&1 | tail -10
```

Expected: still fails at the `EditSoulSheet(soul:)` call site inside `SoulDetailView` (its signature changed from `soul:` to `original:` and now takes a `SoulWithGlyph`). That site is rewritten in Task 11.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift
git commit -m "feat(ios): add glyph row to edit-soul sheet (#298)"
```

---

## Task 10: Rebuild `SoulsListView` as a 3-column grid

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift`

Swap `[Soul]` for `[SoulWithGlyph]`. Replace the `List` with a `LazyVGrid` of 3 adaptive columns, each cell wrapped in a `NavigationLink`. Toolbar `+` button stays. Delete via long-press context menu (replaces the current swipe action — `LazyVGrid` cells don't support `.swipeActions`).

- [ ] **Step 1: Replace the file body**

Replace contents of `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift` with:

```swift
import SwiftUI
import os

struct SoulsListView: View {
    @Environment(SupabaseService.self) private var supabase
    @State private var items: [SoulWithGlyph] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var isPresentingCreate = false
    @State private var pendingDeletion: SoulWithGlyph?
    @State private var deleteError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.souls")

    private let columns = [GridItem(.adaptive(minimum: 96), spacing: 16)]

    var body: some View {
        content
            .navigationTitle("Souls")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        isPresentingCreate = true
                    } label: {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("Add soul")
                }
            }
            .task { await load() }
            .sheet(isPresented: $isPresentingCreate) {
                CreateSoulSheet(onCreated: {
                    Task { await load() }
                })
            }
            .confirmationDialog(
                pendingDeletion.map { "Delete \($0.name)?" } ?? "",
                isPresented: Binding(
                    get: { pendingDeletion != nil },
                    set: { if !$0 { pendingDeletion = nil } }
                ),
                titleVisibility: .visible,
                presenting: pendingDeletion
            ) { soul in
                Button("Delete", role: .destructive) {
                    Task { await delete(soul) }
                }
                Button("Cancel", role: .cancel) {
                    pendingDeletion = nil
                }
            } message: { _ in
                Text("Linked pebbles stay; only the soul and its links are removed.")
            }
            .alert(
                "Couldn't delete",
                isPresented: Binding(
                    get: { deleteError != nil },
                    set: { if !$0 { deleteError = nil } }
                ),
                presenting: deleteError
            ) { _ in
                Button("OK", role: .cancel) { deleteError = nil }
            } message: { message in
                Text(message)
            }
            .pebblesScreen()
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError {
            ContentUnavailableView(
                "Couldn't load souls",
                systemImage: "exclamationmark.triangle",
                description: Text(loadError)
            )
        } else if items.isEmpty {
            ContentUnavailableView(
                "No souls yet",
                systemImage: "person.2",
                description: Text("People and beings you tag on your pebbles will appear here.")
            )
        } else {
            ScrollView {
                LazyVGrid(columns: columns, spacing: 16) {
                    ForEach(items) { item in
                        NavigationLink {
                            SoulDetailView(initial: item, onChanged: {
                                Task { await load() }
                            })
                        } label: {
                            SoulGridCell(soul: item)
                        }
                        .buttonStyle(.plain)
                        .contextMenu {
                            Button(role: .destructive) {
                                pendingDeletion = item
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }
                        }
                    }
                }
                .padding()
            }
        }
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            let result: [SoulWithGlyph] = try await supabase.client
                .from("souls")
                .select("id, name, glyph_id, glyphs(id, name, strokes, view_box)")
                .order("name", ascending: true)
                .execute()
                .value
            self.items = result
        } catch {
            logger.error("souls fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Something went wrong. Please try again."
        }
        self.isLoading = false
    }

    private func delete(_ soul: SoulWithGlyph) async {
        pendingDeletion = nil
        do {
            try await supabase.client
                .from("souls")
                .delete()
                .eq("id", value: soul.id)
                .execute()
            await load()
        } catch {
            logger.error("delete soul failed: \(error.localizedDescription, privacy: .private)")
            deleteError = "Something went wrong. Please try again."
        }
    }
}

#Preview {
    NavigationStack {
        SoulsListView()
            .environment(SupabaseService())
    }
}
```

- [ ] **Step 2: Build**

Build will fail because `SoulDetailView(initial:onChanged:)` doesn't yet exist (current init takes `soul:`). That's resolved in Task 11.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift
git commit -m "feat(ios): render Souls as 3-column glyph grid (#298)"
```

---

## Task 11: Add compact glyph header to `SoulDetailView`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift`

The view receives a `SoulWithGlyph` from the list (renamed init param `initial:`). Switch the `reloadSoul()` fetch to also pull the joined glyph. Build a compact header above the existing pebble list: 56pt thumbnail on the left, name + "<n> pebbles" on the right. Pass `original: soulWithGlyph` to `EditSoulSheet`.

The pluralised string `"<n> pebbles"` uses `LocalizedStringResource` with a stringsdict-style plural — added to `Localizable.xcstrings` in Task 12.

- [ ] **Step 1: Replace the file body**

Replace contents of `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift` with:

```swift
import SwiftUI
import os

/// Pushed detail view for a single soul.
///
/// - Compact header: 56pt glyph thumbnail + name + pebble count.
/// - Below the header: pebbles tagged with this soul (filtered via
///   `pebble_souls` inner join).
/// - Edit toolbar action presents `EditSoulSheet`. The sheet receives the
///   already-fetched `SoulWithGlyph` so its glyph row renders immediately
///   without a second fetch.
struct SoulDetailView: View {
    let onChanged: () -> Void

    @Environment(SupabaseService.self) private var supabase

    @State private var soulWithGlyph: SoulWithGlyph
    @State private var pebbles: [Pebble] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var selectedPebbleId: UUID?
    @State private var isPresentingEdit = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.soul.detail")

    init(initial: SoulWithGlyph, onChanged: @escaping () -> Void) {
        self.onChanged = onChanged
        self._soulWithGlyph = State(initialValue: initial)
    }

    var body: some View {
        content
            .navigationTitle(soulWithGlyph.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button("Edit") {
                        isPresentingEdit = true
                    }
                }
            }
            .task { await load() }
            .sheet(isPresented: $isPresentingEdit) {
                EditSoulSheet(original: soulWithGlyph, onSaved: {
                    Task { await reloadSoul() }
                    onChanged()
                })
            }
            .sheet(item: $selectedPebbleId) { id in
                EditPebbleSheet(pebbleId: id, onSaved: {
                    Task { await load() }
                })
            }
            .pebblesScreen()
    }

    private var header: some View {
        HStack(spacing: 12) {
            GlyphThumbnail(strokes: soulWithGlyph.glyph.strokes, side: 56)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(soulWithGlyph.name)
                    .font(.headline)
                Text("^[\(pebbles.count) pebbles](inflect: true)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError {
            ContentUnavailableView(
                "Couldn't load pebbles",
                systemImage: "exclamationmark.triangle",
                description: Text(loadError)
            )
        } else {
            VStack(spacing: 0) {
                header
                if pebbles.isEmpty {
                    ContentUnavailableView(
                        "No pebbles yet",
                        systemImage: "circle.grid.2x1",
                        description: Text("Pebbles you tag with this soul will appear here.")
                    )
                    .frame(maxHeight: .infinity)
                } else {
                    List(pebbles) { pebble in
                        Button {
                            selectedPebbleId = pebble.id
                        } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(pebble.name).font(.body)
                                Text(pebble.happenedAt, style: .date)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private func reloadSoul() async {
        do {
            let refreshed: SoulWithGlyph = try await supabase.client
                .from("souls")
                .select("id, name, glyph_id, glyphs(id, name, strokes, view_box)")
                .eq("id", value: soulWithGlyph.id)
                .single()
                .execute()
                .value
            self.soulWithGlyph = refreshed
        } catch {
            logger.error("soul reload failed: \(error.localizedDescription, privacy: .private)")
            // Leave stale state; next navigation will refresh.
        }
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            let result: [Pebble] = try await supabase.client
                .from("pebbles")
                .select("id, name, happened_at, pebble_souls!inner(soul_id)")
                .eq("pebble_souls.soul_id", value: soulWithGlyph.id)
                .order("happened_at", ascending: false)
                .execute()
                .value
            self.pebbles = result
        } catch {
            logger.error("soul pebbles fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Something went wrong. Please try again."
        }
        self.isLoading = false
    }
}

#Preview {
    NavigationStack {
        SoulDetailView(
            initial: SoulWithGlyph(
                id: UUID(),
                name: "Preview Soul",
                glyphId: SystemGlyph.default,
                glyph: Glyph(
                    id: SystemGlyph.default,
                    name: nil,
                    strokes: [],
                    viewBox: "0 0 200 200"
                )
            ),
            onChanged: {}
        )
        .environment(SupabaseService())
    }
}
```

- [ ] **Step 2: Build**

```bash
xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build 2>&1 | tail -10
```

Expected: build succeeds. The `^[count pebbles](inflect: true)` automatic-grammar string requires a matching variation in `Localizable.xcstrings`, which Task 12 adds — at runtime without that entry, SwiftUI falls back to `"<n> pebbles"` literally, which is fine until Task 12 lands (no compile error).

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift
git commit -m "feat(ios): add glyph header to soul detail (#298)"
```

---

## Task 12: Localised strings (en + fr)

**Files:**
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`

Three new entries:

| Key                             | English          | French            |
|---------------------------------|------------------|-------------------|
| `Glyph`                         | Glyph            | Glyphe            |
| `Tap to choose`                 | Tap to choose    | Toucher pour choisir |
| `^[%lld pebbles](inflect: true)` | one: 1 pebble / other: %lld pebbles | one: 1 pebble / other: %lld pebbles (FR plural per SwiftUI inflection) |

The pluralised entry uses Apple's automatic-grammar markup (`inflect: true`) — SwiftUI handles `1 pebble` / `2 pebbles` for English. For French, mirror the same plural shape (`1 pebble` / `%lld pebbles` until a French translation is finalised — log this as an open follow-up if not yet translated).

- [ ] **Step 1: Open the catalog in Xcode**

```bash
open apps/ios/Pebbles/Resources/Localizable.xcstrings
```

- [ ] **Step 2: Confirm Xcode auto-extracted the new keys**

Build the iOS target once so `SWIFT_EMIT_LOC_STRINGS=YES` extracts the literals from Steps in Tasks 8/9/11. After building, re-open the catalog. Expect new entries in `New` state for: `Glyph`, `Tap to choose`, `%lld pebbles` (in inflected form).

```bash
xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build 2>&1 | tail -5
```

- [ ] **Step 3: Add `en` + `fr` translations for each new key**

In Xcode's catalog UI, for each row in `New` state:
- Set English value (matches the source literal).
- Set French value:
  - `Glyph` → `Glyphe`
  - `Tap to choose` → `Toucher pour choisir`
  - `%lld pebbles` (inflected) → variations: `one: %lld caillou`, `other: %lld cailloux`

For the inflected plural, set both English and French variations explicitly:
- English: `one: %lld pebble`, `other: %lld pebbles`
- French: `one: %lld caillou`, `other: %lld cailloux`

Mark each row as `Translated` (Xcode does this automatically once both columns have values).

- [ ] **Step 4: Verify no `New` / `Stale` rows remain**

Spot-check the catalog: every row that touches the Soul* views must be `Translated` for both `en` and `fr`. If any row is `Stale`, review and re-translate.

- [ ] **Step 5: Build + manually verify French locale**

```bash
xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build 2>&1 | tail -5
```

Then run the simulator with `-AppleLanguages '(fr-FR)' -AppleLocale fr_FR` (Xcode → Edit Scheme → Run → App Language: French) and confirm the Souls list, detail header ("X cailloux"), and create/edit sheets render in French.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ios): localise soul glyph strings (en + fr)"
```

---

## Task 13: Update Arkaik product map

**Files:**
- Modify: `docs/arkaik/bundle.json`

Use the `arkaik` skill (`.claude/skills/arkaik/`) — it explains the schema and provides a validation script. Two surgical edits:

- [ ] **Step 1: Invoke the arkaik skill**

Read `.claude/skills/arkaik/SKILL.md` (via the Skill tool). Follow its update pattern.

- [ ] **Step 2: Update `DM-soul` description**

Find the node `DM-soul` (around line 656 in `docs/arkaik/bundle.json`). Append " Each soul carries a glyph for visual identity." to its `description`. Keep all other fields untouched.

- [ ] **Step 3: Update `V-souls-list` description**

Find the node `V-souls-list` (around line 202). Update `description` from "Browse and manage all souls (people in your life)." to "Browse and manage all souls (people in your life) as a 3-column glyph grid."

- [ ] **Step 4: Run the arkaik validation script**

```bash
node .claude/skills/arkaik/validate.mjs docs/arkaik/bundle.json
```

(Use the exact script path the skill specifies — verify by reading the skill's instructions.) Expected: validation passes.

- [ ] **Step 5: Commit**

```bash
git add docs/arkaik/bundle.json
git commit -m "docs(arkaik): note soul glyph in souls list and DM-soul"
```

---

## Task 14: Final verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full test suite**

```bash
xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 16' test 2>&1 | tail -30
```

Expected: every test passes — including the 3 new ones added in Tasks 3, 5, 6.

- [ ] **Step 2: Run the workspace lint**

```bash
npm run lint
```

Expected: clean exit (no warnings, no errors).

- [ ] **Step 3: Manual smoke on simulator**

Boot the simulator and verify each scenario from the spec:
1. Existing pre-migration soul shows the system default glyph in the grid and detail header.
2. Create a soul without tapping the glyph row → soul saved, default glyph appears in the grid.
3. Create a soul, open picker, carve a fresh glyph → soul saved with the new glyph; grid reflects it.
4. Edit an existing soul, change the glyph via picker → grid updates after dismissal.
5. Switch simulator to French → every Soul* string renders in French.

If any scenario fails, return to the relevant task and fix before proceeding.

- [ ] **Step 4: Confirm no stale strings**

Re-open `Localizable.xcstrings` in Xcode. Confirm no `New` or `Stale` rows for any Soul-related keys.

- [ ] **Step 5: Push branch + open PR**

The branch should already match `feat/298-ios-soul-glyph-design` (per memory & repo conventions). Push and open the PR with the exact issue/labels/milestone inheritance flow from `CLAUDE.md`:

```bash
git push -u origin feat/298-ios-soul-glyph-design
```

Then propose to the user: "PR will resolve #298, inheriting labels (`feat`, `ios`, `core`) and milestone (`M25 · Improved core UX`) from the issue. Confirm?"

After confirmation, create the PR with `gh pr create` per the project's PR Workflow Checklist (title in conventional commits format, body starting with `Resolves #298`, key files listed).

---

## Notes for the implementer

- **No RPC.** Both writes are single-table single-statement (per `AGENTS.md`). Don't be tempted to wrap them in a function.
- **No `await` inside `onAuthStateChange`.** Not a concern in any of these files, but worth keeping in mind if you touch session code by accident.
- **Picker `onSelected` signature.** Today `GlyphPickerSheet.onSelected: (UUID?) -> Void` — tolerant of nil so the picker can pass through a "cleared" selection. We treat `nil` here as "no change" (the spec says: picker dismissed without selection → keep prior value), so the `if let selected` guard inside the create/edit sheets is intentional. Don't refactor the picker's signature in this PR.
- **`GlyphRow` duplication between sheets.** Two callers, kept private. If a third caller shows up later, lift to `apps/ios/Pebbles/Features/Profile/Components/GlyphRow.swift`.
- **`xcodegen generate` after every new file.** The pbxproj is regenerated; commit it together with the new source file (don't gitignore changes there during this work).
