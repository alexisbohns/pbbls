# iOS — Name and Rename Glyphs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users name a glyph while carving it during pebble collection, and rename a glyph from Profile > Glyphs via a native iOS alert.

**Architecture:** No DB or model changes — `glyphs.name` is already nullable text, `Glyph.name: String?` already decodes, and `GlyphService.create(strokes:name:)` already accepts a name. This plan wires the existing column end-to-end: a name `TextField` above the carve canvas (with `@FocusState`-driven keyboard dismiss), a tap-to-rename `.alert` on the glyphs list, and a new single-table `GlyphService.updateName(id:name:)`. A single `normalizedName(_:)` helper on the service trims and nil-collapses input for both create and rename.

**Tech Stack:** SwiftUI (iOS 17+, `@Observable`), Supabase Swift SDK, PostgREST single-table update, Swift Testing for unit tests. Strings in `Localizable.xcstrings` (en + fr).

**Spec:** `docs/superpowers/specs/2026-04-26-ios-glyph-naming-design.md` (issue #300).

**Branch:** `feat/300-glyph-name` (already created).

---

## File Structure

**Created:**

- `apps/ios/PebblesTests/Features/Glyph/GlyphUpdateNameEncodingTests.swift` — verifies the dict body sent to Supabase encodes a Swift `nil` as a JSON `null` (not omitted) and a non-empty string as a JSON string.

**Modified:**

- `apps/ios/Pebbles/Features/Glyph/Services/GlyphService.swift` — adds `updateName(id:name:)`, adds private `normalizedName(_:)`, refactors `create(strokes:name:)` to call the helper.
- `apps/ios/Pebbles/Features/Glyph/Views/GlyphCarveSheet.swift` — adds `name` state, `@FocusState`, name `TextField` above the canvas, keyboard-toolbar Done button, tap-to-dismiss on the surrounding `VStack`, passes `name` through `service.create(...)`.
- `apps/ios/Pebbles/Features/Glyph/Views/GlyphsListView.swift` — wraps each grid cell in a `Button`, adds rename alert state, draft, and inline error, calls `service.updateName(...)` with optimistic update + revert on failure, attaches accessibility labels and hints.
- `apps/ios/Pebbles/Resources/Localizable.xcstrings` — adds new strings (en + fr): `Name (optional)`, `Glyph name`, `Rename glyph`, `Untitled glyph`, `Double tap to rename`, `Couldn't rename glyph. Please try again.`. Reuses existing `Done`, `Save`, `Cancel`.

**No changes needed to:**

- `apps/ios/Pebbles/Features/Glyph/Models/Glyph.swift` — `name: String?` already present.
- `apps/ios/Pebbles/Features/Glyph/Models/GlyphInsertPayload.swift` — already encodes `name`.
- `apps/ios/Pebbles/Features/Glyph/Views/GlyphPickerSheet.swift` — picker presents `GlyphCarveSheet` for new glyphs, which now collects the name; existing glyphs are renamed only from Profile > Glyphs.
- `packages/supabase/supabase/migrations/` — no migration. `glyphs.name` is already nullable; `glyphs_update` RLS already permits owner updates.
- `packages/supabase/types/database.ts` — no regeneration.
- `apps/ios/project.yml` — xcodegen auto-discovers under `Pebbles/` and `PebblesTests/`; new test file requires `xcodegen generate` (or `npm run generate --workspace=@pbbls/ios`) before the next Xcode build.
- `docs/arkaik/bundle.json` — naming a glyph adds no screen, route, model, or endpoint.

---

## Task 1 — Add `normalizedName` helper and refactor `create(...)`

**Files:**

- Modify: `apps/ios/Pebbles/Features/Glyph/Services/GlyphService.swift`

Pure refactor with no behavior change. Sets up the single source of truth for trim + empty-to-nil that Task 3 (`updateName`) and Task 5 (carve sheet wiring) will both depend on.

- [ ] **Step 1: Add the private helper and refactor `create(...)`**

Replace the body of `create(strokes:name:)` so it calls `normalizedName(_:)` instead of forwarding `name` raw. Add the helper at the bottom of the struct, above the closing brace.

Final file should match this (only the changed regions are shown — keep the existing imports, doc comments, `list()`, and `GlyphServiceError`):

```swift
@MainActor
struct GlyphService {
    let supabase: SupabaseService

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "glyph-service")

    /// Fetches glyphs visible to the current user. In V1 this also includes
    /// system glyphs (user_id is null) — the RLS policy allows them for
    /// domain-default fallback reads elsewhere, and filtering them out
    /// client-side would require adding user_id to the Glyph model.
    /// Not blocking — deferred until the picker needs the distinction.
    func list() async throws -> [Glyph] {
        let rows: [Glyph] = try await supabase.client
            .from("glyphs")
            .select("id, name, strokes, view_box")
            .order("created_at", ascending: false)
            .execute()
            .value
        return rows
    }

    /// Inserts a new glyph owned by the current user. Returns the persisted row.
    func create(strokes: [GlyphStroke], name: String? = nil) async throws -> Glyph {
        guard let userId = supabase.session?.user.id else {
            Self.logger.error("glyph save without session")
            throw GlyphServiceError.missingSession
        }
        let payload = GlyphInsertPayload(
            userId: userId,
            strokes: strokes,
            viewBox: "0 0 200 200",
            name: normalizedName(name)
        )
        let created: Glyph = try await supabase.client
            .from("glyphs")
            .insert(payload)
            .select("id, name, strokes, view_box")
            .single()
            .execute()
            .value
        return created
    }

    private func normalizedName(_ raw: String?) -> String? {
        let trimmed = raw?.trimmingCharacters(in: .whitespacesAndNewlines)
        return (trimmed?.isEmpty ?? true) ? nil : trimmed
    }
}
```

- [ ] **Step 2: Build to confirm no syntax errors**

Run: `npm run generate --workspace=@pbbls/ios` (xcodegen — picks up no new files yet, but cheap to run).

Then in Xcode, build the `Pebbles` scheme (`Cmd-B`).

Expected: build succeeds.

- [ ] **Step 3: Run the existing `GlyphInsertPayloadEncodingTests` to confirm no regression**

In Xcode, open `apps/ios/PebblesTests/Features/Glyph/GlyphInsertPayloadEncodingTests.swift` and run the suite (`Cmd-U` for the test target, or click the diamond next to `@Suite`).

Expected: both tests pass — `snakeCaseKeys` and `strokeShape`. The refactor doesn't touch the payload struct, so behavior is unchanged.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Glyph/Services/GlyphService.swift
git commit -m "$(cat <<'EOF'
refactor(ios): centralize glyph name trim/nil rule in GlyphService (#300)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2 — Write the failing rename-payload encoding test

**Files:**

- Create: `apps/ios/PebblesTests/Features/Glyph/GlyphUpdateNameEncodingTests.swift`

Locks the contract that the `["name": value]` dict we'll pass to `.update(...)` in Task 3 encodes `nil` as JSON `null` (not omitted) and a string as a JSON string. The test mirrors the style of the existing `GlyphInsertPayloadEncodingTests` next to it.

- [ ] **Step 1: Create the test file**

```swift
import Foundation
import Testing
@testable import Pebbles

/// Verifies the dict body sent to PostgREST in `GlyphService.updateName(...)`
/// encodes a Swift `nil` as a JSON `null` (so the column is cleared) and a
/// non-empty string as a JSON string. Guards against accidental omission.
@Suite("Glyph update-name encoding")
struct GlyphUpdateNameEncodingTests {

    private func encode(_ value: [String: String?]) throws -> [String: Any] {
        let data = try JSONEncoder().encode(value)
        let object = try JSONSerialization.jsonObject(with: data)
        return try #require(object as? [String: Any])
    }

    @Test("non-empty name encodes as a JSON string")
    func nameEncodesAsString() throws {
        let json = try encode(["name": "Pebble"])
        #expect((json["name"] as? String) == "Pebble")
    }

    @Test("nil name encodes as JSON null, not omitted")
    func nilEncodesAsNull() throws {
        let json = try encode(["name": nil])
        #expect(json["name"] is NSNull, "nil must serialize as JSON null so the DB column is cleared")
    }
}
```

- [ ] **Step 2: Regenerate the Xcode project so xcodegen picks up the new file**

Run: `npm run generate --workspace=@pbbls/ios`

Expected: `pbbls.xcodeproj` updates silently.

- [ ] **Step 3: Run the new suite to confirm it passes**

In Xcode, open the new file and click the diamond next to `@Suite`. (The contract is already true of `JSONEncoder` for `[String: String?]` — this is a regression guard, not a TDD red→green cycle.)

Expected: both tests pass.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/PebblesTests/Features/Glyph/GlyphUpdateNameEncodingTests.swift apps/ios/pbbls.xcodeproj
git commit -m "$(cat <<'EOF'
test(ios): guard glyph rename payload encodes nil as JSON null (#300)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

> **Note:** `apps/ios/pbbls.xcodeproj` is git-ignored per `apps/ios/CLAUDE.md` ("`.xcodeproj` is a git-ignored build artifact"). If `git add apps/ios/pbbls.xcodeproj` reports nothing to add, that's expected — only the test file gets committed.

---

## Task 3 — Add `GlyphService.updateName(id:name:)`

**Files:**

- Modify: `apps/ios/Pebbles/Features/Glyph/Services/GlyphService.swift`

Single-table update. Relies on the existing `glyphs_update` RLS policy (`user_id = auth.uid()`) for ownership — no client-side `eq("user_id", ...)` filter needed.

- [ ] **Step 1: Add `updateName(id:name:)` below `create(...)` and above `normalizedName(_:)`**

```swift
    /// Updates a glyph's name. Pass `nil`, `""`, or any whitespace-only string
    /// to clear it. Single-table write — no RPC needed (per AGENTS.md). RLS
    /// `glyphs_update` enforces ownership.
    func updateName(id: UUID, name: String?) async throws -> Glyph {
        let value = normalizedName(name)
        let updated: Glyph = try await supabase.client
            .from("glyphs")
            .update(["name": value])
            .eq("id", value: id)
            .select("id, name, strokes, view_box")
            .single()
            .execute()
            .value
        return updated
    }
```

- [ ] **Step 2: Build to confirm the new method compiles**

In Xcode, build the `Pebbles` scheme (`Cmd-B`).

Expected: build succeeds. (`["name": value]` where `value: String?` matches the SDK's `[String: String?]` overload of `.update(...)` — that's exactly what the Task 2 test guarded.)

- [ ] **Step 3: Re-run `GlyphUpdateNameEncodingTests` and `GlyphInsertPayloadEncodingTests`**

In Xcode, run both suites.

Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Glyph/Services/GlyphService.swift
git commit -m "$(cat <<'EOF'
feat(ios): add GlyphService.updateName for single-table rename (#300)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4 — Add name field to `GlyphCarveSheet`

**Files:**

- Modify: `apps/ios/Pebbles/Features/Glyph/Views/GlyphCarveSheet.swift`

Adds the name input above the canvas, wires `@FocusState` for the two keyboard-dismiss behaviors from #300's acceptance criteria, and passes the (trimmed-by-service) name through to `create(...)`.

- [ ] **Step 1: Add `name` and `nameFieldFocused` state**

In `GlyphCarveSheet`, after the existing `@State private var showDiscardAlert = false` line, add:

```swift
    @State private var name: String = ""
    @FocusState private var nameFieldFocused: Bool
```

- [ ] **Step 2: Insert the name `TextField` at the top of `content`**

Replace the existing `content` computed property with:

```swift
    @ViewBuilder
    private var content: some View {
        VStack(spacing: 24) {
            Spacer(minLength: 0)

            TextField("Name (optional)", text: $name)
                .textFieldStyle(.roundedBorder)
                .textInputAutocapitalization(.words)
                .submitLabel(.done)
                .focused($nameFieldFocused)
                .onSubmit { nameFieldFocused = false }
                .accessibilityLabel("Glyph name")

            GlyphCanvasView(
                committedStrokes: strokes,
                onStrokeCommit: { stroke in strokes.append(stroke) },
                strokeColor: Color.pebblesAccent
            )

            if let saveError {
                Text(saveError)
                    .foregroundStyle(.red)
                    .font(.callout)
            }

            HStack(spacing: 24) {
                Button {
                    if !strokes.isEmpty { strokes.removeLast() }
                } label: {
                    Label("Undo", systemImage: "arrow.uturn.backward")
                }
                .disabled(strokes.isEmpty)

                Button(role: .destructive) {
                    strokes.removeAll()
                } label: {
                    Label("Clear", systemImage: "trash")
                }
                .disabled(strokes.isEmpty)
            }
            .buttonStyle(.bordered)

            Spacer(minLength: 0)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .contentShape(Rectangle())
        .onTapGesture { nameFieldFocused = false }
    }
```

The `.contentShape(Rectangle())` + `.onTapGesture { nameFieldFocused = false }` are on the **outer** `VStack`. `GlyphCanvasView` is inside that `VStack` but consumes its own gestures (drag-to-stroke), so taps on the canvas don't bubble out — taps on the surrounding padding/Spacer area dismiss the keyboard. This is the acceptance criterion "tap outside the keyboard → keyboard closes".

- [ ] **Step 3: Add the keyboard toolbar Done button**

Inside the existing `.toolbar { ... }` modifier on `NavigationStack { content ... }`, add a third toolbar item:

```swift
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { cancelTapped() }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        if isSaving {
                            ProgressView()
                        } else {
                            Button("Save") {
                                Task { await save() }
                            }
                            .disabled(strokes.isEmpty)
                        }
                    }
                    ToolbarItemGroup(placement: .keyboard) {
                        Spacer()
                        Button("Done") { nameFieldFocused = false }
                    }
                }
```

The keyboard placement only renders the Done button when the keyboard is up, satisfying "tap the close-keyboard button → keyboard closes".

- [ ] **Step 4: Pass `name` through `save()`**

Replace the existing `save()` method with:

```swift
    private func save() async {
        guard !strokes.isEmpty else { return }
        isSaving = true
        saveError = nil
        do {
            let glyph = try await service.create(strokes: strokes, name: name)
            onSaved(glyph)
            dismiss()
        } catch {
            logger.error("glyph create failed: \(error.localizedDescription, privacy: .private)")
            self.saveError = "Couldn't save your glyph. Please try again."
            self.isSaving = false
        }
    }
```

(The service trims and nil-collapses internally — the view passes the raw string.)

- [ ] **Step 5: Build and run on the simulator**

In Xcode, build and run on an iPhone simulator. Open Profile > Glyphs > **+**.

Verify all four acceptance behaviors:

1. Type a name in the new field, draw a stroke, tap **Save** → returns to the list; the new glyph shows under its name in the grid.
2. With the name field focused, tap on empty padding area (above the canvas Spacer or below the buttons) → keyboard dismisses, no extra stroke registered.
3. With the name field focused, tap the **Done** button on the keyboard toolbar → keyboard dismisses.
4. Leave the name blank, draw a stroke, tap Save → glyph saves with `name = nil` (still works as today).

Expected: all four behaviors pass.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Glyph/Views/GlyphCarveSheet.swift
git commit -m "$(cat <<'EOF'
feat(ios): name a glyph while carving (#300)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5 — Tap-to-rename alert in `GlyphsListView`

**Files:**

- Modify: `apps/ios/Pebbles/Features/Glyph/Views/GlyphsListView.swift`

Wraps each grid cell in a `Button`, adds an `.alert` with a `TextField` and Cancel/Save, optimistic local update with revert on failure, and an inline error.

- [ ] **Step 1: Add new `@State` for the rename flow**

After the existing `@State private var showCarveSheet = false`, add:

```swift
    @State private var renaming: Glyph?
    @State private var renameDraft: String = ""
    @State private var renameError: String?
```

- [ ] **Step 2: Wrap each grid cell in a `Button` and attach accessibility**

Replace the existing `ForEach(glyphs) { glyph in VStack(spacing: 4) { ... } }` block with:

```swift
                    ForEach(glyphs) { glyph in
                        Button {
                            renameDraft = glyph.name ?? ""
                            renaming = glyph
                        } label: {
                            VStack(spacing: 4) {
                                GlyphThumbnail(
                                    strokes: glyph.strokes,
                                    side: 96,
                                    strokeColor: Color.pebblesAccent
                                )
                                if let name = glyph.name {
                                    Text(name)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                        .lineLimit(1)
                                }
                            }
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(glyph.name ?? "Untitled glyph")
                        .accessibilityHint("Double tap to rename")
                    }
```

- [ ] **Step 3: Render the inline rename error above the grid**

Inside the `else` branch of `content` (the one that contains the `ScrollView`), wrap the existing `ScrollView` in a `VStack` so the error sits above it. Replace:

```swift
        } else {
            ScrollView {
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(glyphs) { glyph in
                        // ... (the Button from Step 2)
                    }
                }
                .padding()
            }
        }
```

with:

```swift
        } else {
            VStack(spacing: 0) {
                if let renameError {
                    Text(renameError)
                        .font(.callout)
                        .foregroundStyle(.red)
                        .padding(.horizontal)
                        .padding(.top, 8)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                ScrollView {
                    LazyVGrid(columns: columns, spacing: 12) {
                        ForEach(glyphs) { glyph in
                            Button {
                                renameDraft = glyph.name ?? ""
                                renaming = glyph
                            } label: {
                                VStack(spacing: 4) {
                                    GlyphThumbnail(
                                        strokes: glyph.strokes,
                                        side: 96,
                                        strokeColor: Color.pebblesAccent
                                    )
                                    if let name = glyph.name {
                                        Text(name)
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                            .lineLimit(1)
                                    }
                                }
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(glyph.name ?? "Untitled glyph")
                            .accessibilityHint("Double tap to rename")
                        }
                    }
                    .padding()
                }
            }
        }
```

- [ ] **Step 4: Attach the rename `.alert` to the body**

In `var body: some View`, add the alert after the existing `.fullScreenCover(...)`:

```swift
            .alert(
                "Rename glyph",
                isPresented: Binding(
                    get: { renaming != nil },
                    set: { if !$0 { renaming = nil } }
                ),
                presenting: renaming
            ) { glyph in
                TextField("Name (optional)", text: $renameDraft)
                    .textInputAutocapitalization(.words)
                Button("Cancel", role: .cancel) {}
                Button("Save") {
                    Task { await commitRename(glyph) }
                }
            }
```

- [ ] **Step 5: Add the `commitRename(_:)` method**

Add this method inside `GlyphsListView`, alongside `load()`:

```swift
    private func commitRename(_ glyph: Glyph) async {
        renameError = nil
        guard let index = glyphs.firstIndex(where: { $0.id == glyph.id }) else { return }
        let original = glyphs[index]
        let trimmed = renameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        let optimisticName: String? = trimmed.isEmpty ? nil : trimmed

        // Optimistic update
        glyphs[index] = Glyph(
            id: glyph.id,
            name: optimisticName,
            strokes: glyph.strokes,
            viewBox: glyph.viewBox
        )

        do {
            let updated = try await service.updateName(id: glyph.id, name: renameDraft)
            if let i = glyphs.firstIndex(where: { $0.id == updated.id }) {
                glyphs[i] = updated
            }
        } catch {
            logger.error("glyph rename failed: \(error.localizedDescription, privacy: .private)")
            // Revert
            if let i = glyphs.firstIndex(where: { $0.id == glyph.id }) {
                glyphs[i] = original
            }
            renameError = "Couldn't rename glyph. Please try again."
        }
    }
```

> **Note on the `Glyph` initializer:** `Glyph` is `Decodable` only — it has no synthesized memberwise initializer because it's a `struct` declared in a different module-internal scope. **Verify this by reading `apps/ios/Pebbles/Features/Glyph/Models/Glyph.swift` before writing this step.** If the struct exposes a memberwise init (Swift synthesizes one for `internal` structs unless a custom init exists), the code above works as-is. If it doesn't, replace the optimistic-update block with a small `Glyph(from:)` decode workaround OR — preferred — extend `Glyph` with a memberwise init in this file's scope or the model file. The cleanest fix is to add an explicit memberwise init to `Glyph.swift`:
>
> ```swift
> init(id: UUID, name: String?, strokes: [GlyphStroke], viewBox: String) {
>     self.id = id
>     self.name = name
>     self.strokes = strokes
>     self.viewBox = viewBox
> }
> ```
>
> Add it only if the build fails on the optimistic-update line.

- [ ] **Step 6: Build to confirm**

In Xcode, build the `Pebbles` scheme (`Cmd-B`).

Expected: build succeeds. If it fails on the `Glyph(...)` call, apply the memberwise-init fix from the note above and rebuild.

- [ ] **Step 7: Manual QA on the simulator**

Run the app, open Profile > Glyphs.

1. Tap a named glyph → alert appears with the current name pre-filled. Edit it, tap Save → grid updates with the new name.
2. Tap an unnamed glyph → alert appears with empty field. Type a name, tap Save → grid updates with the new name shown under the thumbnail.
3. Tap a glyph, clear the field entirely, tap Save → name becomes nil; thumbnail no longer shows a caption.
4. Tap a glyph, tap Cancel → no change.
5. (Optional) Disconnect from the network, attempt rename → after the request fails, the optimistic name reverts and the red `renameError` text appears above the grid.
6. VoiceOver: enable VoiceOver, swipe to a glyph cell → announced as `<name>` (or `Untitled glyph`) followed by `Double tap to rename`.

Expected: all six behaviors pass.

- [ ] **Step 8: Commit**

```bash
git add apps/ios/Pebbles/Features/Glyph/Views/GlyphsListView.swift apps/ios/Pebbles/Features/Glyph/Models/Glyph.swift
git commit -m "$(cat <<'EOF'
feat(ios): rename glyphs from Profile via alert (#300)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

(`Glyph.swift` only included if the memberwise-init fix was applied.)

---

## Task 6 — Localization pass

**Files:**

- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`

After Tasks 4 and 5, Xcode's build-time string extraction (`SWIFT_EMIT_LOC_STRINGS=YES`) will have created new entries in the catalog. This task fills both `en` and `fr` columns and confirms no entry is in `New` or `Stale` state, per `apps/ios/CLAUDE.md`.

- [ ] **Step 1: Trigger string extraction by building**

In Xcode, build the `Pebbles` scheme (`Cmd-B`). Building re-runs string extraction, so any new `Text`, `Button`, `TextField`, `.alert` titles, `.accessibilityLabel`, and `.accessibilityHint` literals appear in `Localizable.xcstrings`.

- [ ] **Step 2: Open `Localizable.xcstrings` in Xcode and fill the new keys**

Open `apps/ios/Pebbles/Resources/Localizable.xcstrings`. Filter by **State: New**. Expected new keys (English source):

| Key | English | French |
|---|---|---|
| `Name (optional)` | Name (optional) | Nom (facultatif) |
| `Glyph name` | Glyph name | Nom du glyphe |
| `Rename glyph` | Rename glyph | Renommer le glyphe |
| `Untitled glyph` | Untitled glyph | Glyphe sans nom |
| `Double tap to rename` | Double tap to rename | Touchez deux fois pour renommer |
| `Couldn't rename glyph. Please try again.` | Couldn't rename glyph. Please try again. | Impossible de renommer le glyphe. Veuillez réessayer. |

For each key:
1. Click the row.
2. Confirm the English value.
3. Click the **fr** column and paste the French translation from the table.
4. Confirm the **State** column shows **Translated** (green check) for both languages.

Already-existing keys to verify (no edit needed unless they're showing `New`/`Stale`): `Done`, `Save`, `Cancel`.

- [ ] **Step 3: Verify zero `New` / `Stale` rows**

Filter the catalog by **State: New** — expected: zero results.
Filter by **State: Stale** — expected: zero results (or only pre-existing stale rows from main, if any; do not fix unrelated stale rows in this PR).

- [ ] **Step 4: Build and re-run on the simulator in French**

In Xcode, edit the scheme: Run > Options > App Language > **French**. Build and run.

Open Profile > Glyphs and the carve sheet. Verify:
- Carve sheet name field placeholder reads "Nom (facultatif)".
- Save error text and Done button remain correctly localized.
- Tap a glyph → alert title reads "Renommer le glyphe", Cancel/Save read "Annuler"/"Enregistrer".
- Trigger a rename failure (airplane mode) → error reads "Impossible de renommer le glyphe. Veuillez réessayer."

Reset the scheme back to System Language afterward.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "$(cat <<'EOF'
feat(ios): localize glyph naming strings (en + fr) (#300)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7 — Final verification and PR

**Files:** none modified.

- [ ] **Step 1: Run the full test suite**

In Xcode, run the `PebblesTests` target (`Cmd-U`).

Expected: all tests pass — including the new `GlyphUpdateNameEncodingTests` and the existing `GlyphInsertPayloadEncodingTests`.

- [ ] **Step 2: Run swiftlint via the build (it's wired into the project)**

In Xcode, build the `Pebbles` scheme.

Expected: no new swiftlint warnings introduced by this work. (Pre-existing warnings on main are not in scope.)

- [ ] **Step 3: Re-run the manual QA checklist against the four #300 acceptance criteria**

1. ✅ Carving a glyph during pebble collection: name field is reachable, optional, saves through to the new glyph.
2. ✅ Tap outside the keyboard while the name field is focused → keyboard dismisses (no extra canvas stroke).
3. ✅ Tap the keyboard-toolbar **Done** button → keyboard dismisses.
4. ✅ Tapping a glyph on Profile > Glyphs opens the rename alert and persists the new name.

- [ ] **Step 4: Push the branch**

```bash
git push -u origin feat/300-glyph-name
```

- [ ] **Step 5: Open the PR with inherited labels and milestone**

Confirm with the user before running. Default values from issue #300:
- Title: `feat(ios): name and rename glyphs`
- Body opens with `Resolves #300`, lists files changed, calls out the four acceptance criteria.
- Labels: `core`, `feat`, `ios` (inherited from #300 — `feat` instead of `bug`-style mapping).
- Milestone: `M25 · Improved core UX`.

```bash
gh pr create \
  --title "feat(ios): name and rename glyphs" \
  --body "$(cat <<'EOF'
Resolves #300

## Summary

- Adds a name field above the canvas in `GlyphCarveSheet`. Optional. Trimmed and nil-collapsed in `GlyphService` so empty/whitespace stays NULL in the DB.
- Adds tap-to-rename on Profile > Glyphs via a native iOS `.alert` containing a `TextField`. Optimistic update with revert on failure and an inline retry message above the grid.
- New `GlyphService.updateName(id:name:)` — single-table update, RLS-enforced ownership.
- Keyboard dismissal in the carve sheet: tap-outside on the surrounding `VStack` and a keyboard-toolbar Done button, both via `@FocusState`.
- Localized en + fr; verified no `New`/`Stale` rows in `Localizable.xcstrings`.

## Files changed

- `apps/ios/Pebbles/Features/Glyph/Services/GlyphService.swift`
- `apps/ios/Pebbles/Features/Glyph/Views/GlyphCarveSheet.swift`
- `apps/ios/Pebbles/Features/Glyph/Views/GlyphsListView.swift`
- `apps/ios/Pebbles/Resources/Localizable.xcstrings`
- `apps/ios/PebblesTests/Features/Glyph/GlyphUpdateNameEncodingTests.swift` (new)

## Acceptance

- [x] Carving a new glyph: name field is set during carve.
- [x] Tap outside keyboard → keyboard closes.
- [x] Keyboard-toolbar Done → keyboard closes.
- [x] Profile > Glyphs: tap a glyph → rename it.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)" \
  --label core --label feat --label ios \
  --milestone "M25 · Improved core UX"
```

Expected: PR opens; URL printed.

- [ ] **Step 6: Verify the PR landing checks pass**

Watch CI in `gh pr checks` (or the GitHub UI). If a check fails, address it before requesting review.

---

## Self-Review

**Spec coverage:**

- ✅ Carve-sheet name field (Task 4) — spec UI section.
- ✅ Tap-outside keyboard dismiss (Task 4 Step 2) — acceptance criterion 2.
- ✅ Keyboard-toolbar Done (Task 4 Step 3) — acceptance criterion 3.
- ✅ Profile > Glyphs tap-to-rename (Task 5) — acceptance criterion 4.
- ✅ Service `updateName` (Task 3) + `normalizedName` helper (Task 1) — spec data layer.
- ✅ Optimistic update + revert + inline error (Task 5 Step 5) — spec error handling.
- ✅ Encoding contract test (Task 2) — spec testing.
- ✅ Localization en + fr (Task 6) — spec localization.
- ✅ Accessibility labels and hint (Task 5 Step 2) — spec accessibility.
- ✅ No DB migration, no `database.ts` regen, no Arkaik update — confirmed in File Structure.

**Placeholder scan:** No "TBD", "implement later", "similar to Task N", or instructions without code. The one spot referencing "if the build fails" (Task 5 Step 5 note) is a pre-emptive workaround with the exact code to apply, not a placeholder.

**Type consistency:**
- `GlyphService.updateName(id: UUID, name: String?)` — same name and signature in Tasks 3, 5.
- `normalizedName(_ raw: String?) -> String?` — same name and signature in Tasks 1, 3.
- `Glyph` initializer call in Task 5 Step 5 matches the property names in `Glyph.swift` (id, name, strokes, viewBox).
- `renaming`, `renameDraft`, `renameError` state names consistent across Task 5 Steps 1, 4, 5.
- `nameFieldFocused` consistent across Task 4 Steps 1–3.

No drift detected.
