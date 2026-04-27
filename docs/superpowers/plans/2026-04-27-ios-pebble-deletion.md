# iOS Pebble Deletion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add long-press deletion to every iOS list where a pebble row appears, and harmonize those rows into a shared `PebbleRow` component so the visual treatment (thumbnail + name + date) is consistent.

**Architecture:** Introduce a shared `PebbleRow` view in a new `apps/ios/Pebbles/Components/` folder. The row owns the `.contextMenu` with a destructive Delete action and exposes `onTap` / `onDelete` closures. Each parent list (`PathView`, `SoulDetailView`, `CollectionDetailView`) owns the `pendingDeletion` state, `confirmationDialog`, error `alert`, and the `delete_pebble` RPC call — same shape as the existing `SoulsListView`. The two detail views' `load()` selects are extended to fetch `render_svg` and the `emotion` join so the new row's thumbnail has data.

**Tech Stack:** SwiftUI (iOS 17+), `@Observable` services, Supabase Swift SDK (`.rpc(...)`), Swift Testing for unit tests, `Localizable.xcstrings` for i18n. Project regen via `xcodegen` (run `npm run generate --workspace=@pbbls/ios`).

**TDD note:** Per `apps/ios/CLAUDE.md`, the iOS app has unit tests for pure functions / encoders / decoders only — there are no UI tests for SwiftUI views. The destructive flow is a SwiftUI state machine with a single RPC call and no pure-function logic, so this plan does not include unit tests for the delete state machine itself. It does add a Swift Testing case for the new localization key (matching existing `LocalizationTests` patterns) and verifies each step with build + lint + manual run.

**Branch:** `feat/327-ios-pebble-deletion` (already created; the spec is committed to it).

---

## File Map

**New**
- `apps/ios/Pebbles/Components/PebbleRow.swift` — single-responsibility row view used by all three lists.

**Modified**
- `apps/ios/Pebbles/Features/Path/PathView.swift` — drop the inline row + `pebbleThumbnail` helper, render `PebbleRow`, add deletion state and handlers.
- `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift` — extend the pebbles select; render `PebbleRow`; add deletion state and handlers.
- `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift` — extend the pebbles select; render `PebbleRow`; add deletion state and handlers.
- `apps/ios/Pebbles/Resources/Localizable.xcstrings` — add `"This can't be undone."` with `en` and `fr` values.
- `apps/ios/PebblesTests/LocalizationTests.swift` — add a coverage test for the new key.

**Build artefact (gitignored, regenerated)**
- `apps/ios/Pebbles.xcodeproj/...` — must be regenerated with `xcodegen` after `Components/` is added.

---

## Task 1: Create the shared `PebbleRow` component

**Files:**
- Create: `apps/ios/Pebbles/Components/PebbleRow.swift`

- [ ] **Step 1: Verify the new folder is auto-included**

Run from repo root:
```bash
grep -A 2 "    sources:" apps/ios/project.yml | head -5
```
Expected output contains:
```
    sources:
      - path: Pebbles
```
This confirms `Pebbles/Components/**.swift` is auto-included on the next `xcodegen generate`. No `project.yml` edit is needed.

- [ ] **Step 2: Create the component file**

Write `apps/ios/Pebbles/Components/PebbleRow.swift` with this exact content:

```swift
import SwiftUI

/// Shared row view for a pebble in a list. Used by `PathView`,
/// `SoulDetailView`, and `CollectionDetailView` so the thumbnail +
/// name + date treatment stays consistent across the app.
///
/// The row owns the long-press contextual menu so any new list that
/// uses `PebbleRow` automatically gets the Delete affordance. The
/// parent owns the destructive flow itself: confirmation dialog,
/// error alert, the `delete_pebble` RPC call, and the reload.
///
/// `pebble` must be loaded with `render_svg` and the
/// `emotion:emotions(id, slug, name, color)` join populated for the
/// thumbnail to render correctly. When `render_svg` is nil the row
/// falls back to a neutral rounded rectangle.
struct PebbleRow: View {
    let pebble: Pebble
    let onTap: () -> Void
    let onDelete: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                thumbnail
                VStack(alignment: .leading, spacing: 4) {
                    Text(pebble.name).font(.body)
                    Text(pebble.happenedAt, style: .date)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button(role: .destructive, action: onDelete) {
                Label("Delete", systemImage: "trash")
            }
        }
    }

    @ViewBuilder
    private var thumbnail: some View {
        if let svg = pebble.renderSvg {
            PebbleRenderView(svg: svg, strokeColor: pebble.emotion?.color)
                .frame(width: 40, height: 40)
        } else {
            RoundedRectangle(cornerRadius: 6)
                .fill(Color.secondary.opacity(0.15))
                .frame(width: 40, height: 40)
        }
    }
}

#Preview {
    List {
        PebbleRow(
            pebble: Pebble(
                id: UUID(),
                name: "Sample pebble",
                happenedAt: Date(),
                renderSvg: nil,
                emotion: nil
            ),
            onTap: {},
            onDelete: {}
        )
    }
}
```

- [ ] **Step 3: Regenerate the Xcode project**

Run:
```bash
npm run generate --workspace=@pbbls/ios
```
Expected: `Generated project successfully` (or equivalent xcodegen success line).

- [ ] **Step 4: Build to confirm it compiles**

Run:
```bash
npm run build --workspace=@pbbls/ios
```
Expected: `BUILD SUCCEEDED` at the end. If `Pebble`'s init signature differs from what the `#Preview` block uses, fix the `#Preview` to match `Pebble`'s actual init — keep the body of `PebbleRow` itself unchanged.

- [ ] **Step 5: Lint**

Run:
```bash
npm run lint --workspace=@pbbls/ios
```
Expected: zero violations.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Components/PebbleRow.swift
git commit -m "feat(core): add shared PebbleRow component"
```

---

## Task 2: Adopt `PebbleRow` and add deletion in `PathView`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PathView.swift`

- [ ] **Step 1: Add deletion state**

In `PathView.swift`, add two `@State` properties next to the existing ones (right after `@State private var isPresentingOnboarding = false`):

```swift
    @State private var pendingDeletion: Pebble?
    @State private var deleteError: String?
```

- [ ] **Step 2: Replace the inline row with `PebbleRow`**

Locate the `Section("Path") { ... }` block in `content` (around lines 74–92). Replace the entire `ForEach(pebbles) { pebble in ... }` body with:

```swift
                    ForEach(pebbles) { pebble in
                        PebbleRow(
                            pebble: pebble,
                            onTap: { selectedPebbleId = pebble.id },
                            onDelete: { pendingDeletion = pebble }
                        )
                        .listRowBackground(Color.pebblesListRow)
                    }
```

- [ ] **Step 3: Remove the now-unused `pebbleThumbnail` helper**

Delete the entire `@ViewBuilder private func pebbleThumbnail(for pebble: Pebble) -> some View { ... }` block (lines 97–107 in the current file). `PebbleRow` now owns the thumbnail.

- [ ] **Step 4: Add the confirmation dialog and error alert**

In the `body` block, append the following modifiers after the existing `.fullScreenCover(isPresented: $isPresentingOnboarding) { ... }`:

```swift
        .confirmationDialog(
            pendingDeletion.map { "Delete \($0.name)?" } ?? "",
            isPresented: Binding(
                get: { pendingDeletion != nil },
                set: { if !$0 { pendingDeletion = nil } }
            ),
            titleVisibility: .visible,
            presenting: pendingDeletion
        ) { pebble in
            Button("Delete", role: .destructive) {
                Task { await delete(pebble) }
            }
            Button("Cancel", role: .cancel) {
                pendingDeletion = nil
            }
        } message: { _ in
            Text("This can't be undone.")
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
```

- [ ] **Step 5: Add the `delete(_:)` function**

Inside the `PathView` struct, immediately after the `private func load() async { ... }` function, add:

```swift
    private func delete(_ pebble: Pebble) async {
        pendingDeletion = nil
        do {
            try await supabase.client
                .rpc("delete_pebble", params: ["p_pebble_id": pebble.id.uuidString])
                .execute()
            await load()
        } catch {
            logger.error("delete pebble failed: \(error.localizedDescription, privacy: .private)")
            deleteError = "Something went wrong. Please try again."
        }
    }
```

- [ ] **Step 6: Build**

```bash
npm run build --workspace=@pbbls/ios
```
Expected: `BUILD SUCCEEDED`.

- [ ] **Step 7: Lint**

```bash
npm run lint --workspace=@pbbls/ios
```
Expected: zero violations.

- [ ] **Step 8: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PathView.swift
git commit -m "feat(core): add long-press pebble deletion in PathView"
```

---

## Task 3: Adopt `PebbleRow` and add deletion in `SoulDetailView`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift`

- [ ] **Step 1: Extend the load query**

Find the select in `load()` (around line 136):

```swift
                .select("id, name, happened_at, pebble_souls!inner(soul_id)")
```

Replace it with:

```swift
                .select("id, name, happened_at, render_svg, emotion:emotions(id, slug, name, color), pebble_souls!inner(soul_id)")
```

- [ ] **Step 2: Add deletion state**

Right after `@State private var isPresentingEdit = false`, add:

```swift
    @State private var pendingDeletion: Pebble?
    @State private var deleteError: String?
```

- [ ] **Step 3: Replace the inline row with `PebbleRow`**

In `content`, locate the `List(pebbles) { pebble in ... }` block (currently lines 96–108). Replace its contents with:

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

(The switch from `List(pebbles)` to `List { ForEach(...) }` is needed so `ForEach` can wrap `PebbleRow` and avoid a row-init ambiguity.)

- [ ] **Step 4: Add the confirmation dialog and error alert**

In `body`, append the following modifiers after the existing `.sheet(item: $selectedPebbleId) { ... }`:

```swift
            .confirmationDialog(
                pendingDeletion.map { "Delete \($0.name)?" } ?? "",
                isPresented: Binding(
                    get: { pendingDeletion != nil },
                    set: { if !$0 { pendingDeletion = nil } }
                ),
                titleVisibility: .visible,
                presenting: pendingDeletion
            ) { pebble in
                Button("Delete", role: .destructive) {
                    Task { await delete(pebble) }
                }
                Button("Cancel", role: .cancel) {
                    pendingDeletion = nil
                }
            } message: { _ in
                Text("This can't be undone.")
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
```

- [ ] **Step 5: Add the `delete(_:)` function**

Immediately after the `private func load() async { ... }` function, add:

```swift
    private func delete(_ pebble: Pebble) async {
        pendingDeletion = nil
        do {
            try await supabase.client
                .rpc("delete_pebble", params: ["p_pebble_id": pebble.id.uuidString])
                .execute()
            await load()
        } catch {
            logger.error("delete pebble failed: \(error.localizedDescription, privacy: .private)")
            deleteError = "Something went wrong. Please try again."
        }
    }
```

- [ ] **Step 6: Build**

```bash
npm run build --workspace=@pbbls/ios
```
Expected: `BUILD SUCCEEDED`.

- [ ] **Step 7: Lint**

```bash
npm run lint --workspace=@pbbls/ios
```
Expected: zero violations.

- [ ] **Step 8: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift
git commit -m "feat(core): add long-press pebble deletion in SoulDetailView"
```

---

## Task 4: Adopt `PebbleRow` and add deletion in `CollectionDetailView`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift`

- [ ] **Step 1: Extend the load query**

Find the select in `load()` (around line 137):

```swift
                .select("id, name, happened_at, collection_pebbles!inner(collection_id)")
```

Replace it with:

```swift
                .select("id, name, happened_at, render_svg, emotion:emotions(id, slug, name, color), collection_pebbles!inner(collection_id)")
```

- [ ] **Step 2: Add deletion state**

Right after `@State private var isPresentingEdit = false`, add:

```swift
    @State private var pendingDeletion: Pebble?
    @State private var deleteError: String?
```

- [ ] **Step 3: Replace the inline row with `PebbleRow`**

In `content`, locate the inner `ForEach(group.value) { pebble in ... }` block (currently lines 97–109). Replace its body with:

```swift
                        ForEach(group.value) { pebble in
                            PebbleRow(
                                pebble: pebble,
                                onTap: { selectedPebbleId = pebble.id },
                                onDelete: { pendingDeletion = pebble }
                            )
                        }
```

- [ ] **Step 4: Add the confirmation dialog and error alert**

In `body`, append the following modifiers after the existing `.sheet(item: $selectedPebbleId) { ... }`:

```swift
            .confirmationDialog(
                pendingDeletion.map { "Delete \($0.name)?" } ?? "",
                isPresented: Binding(
                    get: { pendingDeletion != nil },
                    set: { if !$0 { pendingDeletion = nil } }
                ),
                titleVisibility: .visible,
                presenting: pendingDeletion
            ) { pebble in
                Button("Delete", role: .destructive) {
                    Task { await delete(pebble) }
                }
                Button("Cancel", role: .cancel) {
                    pendingDeletion = nil
                }
            } message: { _ in
                Text("This can't be undone.")
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
```

- [ ] **Step 5: Add the `delete(_:)` function**

Immediately after the `private func load() async { ... }` function, add:

```swift
    private func delete(_ pebble: Pebble) async {
        pendingDeletion = nil
        do {
            try await supabase.client
                .rpc("delete_pebble", params: ["p_pebble_id": pebble.id.uuidString])
                .execute()
            await load()
        } catch {
            logger.error("delete pebble failed: \(error.localizedDescription, privacy: .private)")
            deleteError = "Something went wrong. Please try again."
        }
    }
```

- [ ] **Step 6: Build**

```bash
npm run build --workspace=@pbbls/ios
```
Expected: `BUILD SUCCEEDED`.

- [ ] **Step 7: Lint**

```bash
npm run lint --workspace=@pbbls/ios
```
Expected: zero violations.

- [ ] **Step 8: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift
git commit -m "feat(core): add long-press pebble deletion in CollectionDetailView"
```

---

## Task 5: Add the new localization entry

**Files:**
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`

- [ ] **Step 1: Verify the key is missing**

Run:
```bash
python3 -c "import json; d=json.load(open('apps/ios/Pebbles/Resources/Localizable.xcstrings')); print(\"This can't be undone.\" in d['strings'])"
```
Expected: `False` (or `True` if a previous build already auto-extracted it; either is fine — proceed to step 2 to ensure both `en` and `fr` are populated).

- [ ] **Step 2: Add the entry with both locales**

Run this Python one-liner from the repo root to insert / update the entry:
```bash
python3 -c "
import json
path = 'apps/ios/Pebbles/Resources/Localizable.xcstrings'
d = json.load(open(path))
d['strings'][\"This can't be undone.\"] = {
    'extractionState': 'manual',
    'localizations': {
        'en': {'stringUnit': {'state': 'translated', 'value': \"This can't be undone.\"}},
        'fr': {'stringUnit': {'state': 'translated', 'value': 'Cette action est irréversible.'}}
    }
}
json.dump(d, open(path, 'w'), indent=2, ensure_ascii=False)
open(path, 'a').write('\n')
"
```

- [ ] **Step 3: Verify the entry**

```bash
python3 -c "
import json
d = json.load(open('apps/ios/Pebbles/Resources/Localizable.xcstrings'))
e = d['strings'][\"This can't be undone.\"]
print('en:', e['localizations']['en']['stringUnit']['value'])
print('fr:', e['localizations']['fr']['stringUnit']['value'])
"
```
Expected:
```
en: This can't be undone.
fr: Cette action est irréversible.
```

- [ ] **Step 4: Build to confirm catalog still parses**

```bash
npm run build --workspace=@pbbls/ios
```
Expected: `BUILD SUCCEEDED`. (If Xcode reports a malformed catalog, re-run step 2.)

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(core): localize pebble deletion confirmation copy"
```

---

## Task 6: Add a localization-coverage test for the new key

**Files:**
- Modify: `apps/ios/PebblesTests/LocalizationTests.swift`

- [ ] **Step 1: Read the existing coverage suite**

Open `apps/ios/PebblesTests/LocalizationTests.swift` and scroll to the second suite (`@Suite("Localization — Pattern C coverage")`). This is where catalog-presence assertions live.

- [ ] **Step 2: Append a new test case**

At the bottom of the `LocalizationPatternCCoverageTests` suite (before its closing `}`), add:

```swift
    @Test("'This can\u{2019}t be undone.' has en and fr catalog entries")
    func deletionConfirmationMessageLocalized() {
        let key: LocalizedStringResource = "This can't be undone."
        let en = String(localized: key, locale: Locale(identifier: "en"))
        let fr = String(localized: key, locale: Locale(identifier: "fr"))
        #expect(en == "This can't be undone.")
        #expect(fr == "Cette action est irréversible.")
        #expect(en != fr)
    }
```

(The Unicode escape in the test description avoids tripping over the apostrophe inside the `@Test` argument string.)

- [ ] **Step 3: Run the suite**

```bash
npm run test --workspace=@pbbls/ios
```
Expected: all tests pass, including the new `deletionConfirmationMessageLocalized` test. If it fails with `en == fr` (i.e. `fr` returned the English fallback), Xcode's auto-extraction has not picked up the catalog change yet — clean and rebuild:
```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' clean
npm run test --workspace=@pbbls/ios
```

- [ ] **Step 4: Commit**

```bash
git add apps/ios/PebblesTests/LocalizationTests.swift
git commit -m "test(core): cover deletion confirmation localization"
```

---

## Task 7: Manual verification on the simulator

There are no UI tests, so the deletion flow must be exercised manually on at least one simulator run.

- [ ] **Step 1: Boot the app on the simulator**

```bash
xcodegen generate --project apps/ios
open apps/ios/Pebbles.xcodeproj
```
In Xcode: select the `iPhone 17` simulator → Run.

- [ ] **Step 2: Verify the Path list deletion flow**

1. Sign in (or use an existing session) on a user that has at least two pebbles.
2. In the Path tab, long-press a pebble row.
3. Confirm the contextual menu shows **Delete** (red, with a trash icon).
4. Tap **Delete** → a confirmation dialog appears titled **Delete <pebble name>?** with the message **This can't be undone.**
5. Tap **Cancel** → the dialog dismisses, the pebble is still in the list.
6. Long-press → Delete → confirm with **Delete**. The pebble disappears from the list. (The list reloads from Supabase.)
7. Pull-to-refresh / leave and return — the pebble is still gone.

- [ ] **Step 3: Verify the Soul detail deletion flow**

1. Navigate to Profile → Souls → tap a soul that has at least one tagged pebble.
2. Confirm the soul's pebbles now render with thumbnails (this was the harmonization point — verify the thumbnail isn't blank).
3. Long-press a pebble → Delete → confirm. The pebble vanishes from the soul detail. Pop back to the souls list and re-enter — still gone.

- [ ] **Step 4: Verify the Collection detail deletion flow**

1. Navigate to Profile → Collections → tap a collection that has at least one pebble.
2. Confirm pebbles render with thumbnails.
3. Long-press → Delete → confirm. Pebble vanishes.

- [ ] **Step 5: Verify the error path**

Disable the simulator's network (Settings → Developer → Network Link Conditioner: 100% loss, or simply turn off your machine's network).
1. Long-press → Delete → confirm.
2. The "Couldn't delete" alert appears with **OK** dismiss.
3. The pebble remains in the list.
4. Re-enable network — a subsequent delete succeeds.

- [ ] **Step 6: Verify French localization**

In Xcode, edit the scheme: **Run → Options → App Language → Français**. Re-run.
1. Open Path → long-press → confirm the menu shows **Supprimer**.
2. Tap → dialog shows **Supprimer <pebble> ?** with message **Cette action est irréversible.** and buttons **Supprimer** / **Annuler**.

- [ ] **Step 7: Verify the localization catalog has no `New`/`Stale` entries**

Open `apps/ios/Pebbles/Resources/Localizable.xcstrings` in Xcode (double-click in the Project navigator).
1. Confirm that the `Localizable` table shows no rows in the `New` or `Stale` state.
2. Specifically locate `This can't be undone.` and confirm `en` and `fr` are both green/translated.

If any row is in `New` or `Stale`, fix it in Xcode and re-commit before opening the PR.

---

## Task 8: Open the pull request

- [ ] **Step 1: Confirm clean state and summary**

```bash
git status
git log --oneline main..HEAD
```
Expected: a clean working tree and a series of conventional commits on `feat/327-ios-pebble-deletion`.

- [ ] **Step 2: Push the branch**

```bash
git push -u origin feat/327-ios-pebble-deletion
```

- [ ] **Step 3: Confirm the issue's labels and milestone with the user**

Issue #327 currently has labels `feat`, `ios` and milestone `M25 · Improved core UX`. Per the project's PR workflow checklist (`CLAUDE.md`):

> If the PR resolves an issue, propose inheriting the same labels and milestone from that issue and ask the user to confirm.

Ask the user: *"PR will inherit `feat`, `ios` labels and `M25 · Improved core UX` milestone from #327. OK?"* — and wait for the answer before running step 4.

- [ ] **Step 4: Open the PR**

After the user confirms labels/milestone, run:

```bash
gh pr create --title "feat(core): pebble deletion on iOS" --label feat --label ios --milestone "M25 · Improved core UX" --body "$(cat <<'EOF'
Resolves #327

## Summary
- Adds long-press → contextual menu → confirmation dialog → `delete_pebble` RPC flow on iOS.
- Available everywhere a pebble row appears: `PathView`, `SoulDetailView`, `CollectionDetailView`.
- Harmonizes the three previously-divergent inline rows behind a new shared `PebbleRow` component (`apps/ios/Pebbles/Components/PebbleRow.swift`). The two detail views now show the pebble thumbnail consistently with Path.

## Key files
- `apps/ios/Pebbles/Components/PebbleRow.swift` (new)
- `apps/ios/Pebbles/Features/Path/PathView.swift`
- `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift`
- `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift`
- `apps/ios/Pebbles/Resources/Localizable.xcstrings`
- `apps/ios/PebblesTests/LocalizationTests.swift`

## Implementation notes
- `delete_pebble` already exists in DB (`packages/supabase/supabase/migrations/20260411000003_rpc_functions.sql`) and handles ownership check, karma reversal (`pebble_deleted` event), and DB cascade to `cards`/`snaps`/junction tables atomically.
- Storage cleanup for orphan snap files is intentionally out of scope — same gap exists on web today; tracked separately.
- Soul / collection detail selects extended to fetch `render_svg` and the `emotion` join so the shared row can render thumbnails.
- Spec: `docs/superpowers/specs/2026-04-27-ios-pebble-deletion-design.md`.

## Test plan
- [ ] Long-press a pebble in Path → Delete → confirm → pebble removed and list reloads
- [ ] Long-press a pebble in a Soul detail view → Delete → confirm → removed
- [ ] Long-press a pebble in a Collection detail view → Delete → confirm → removed
- [ ] Cancel from the confirmation dialog leaves the pebble intact
- [ ] Network failure surfaces the "Couldn't delete" alert and leaves the pebble intact
- [ ] French locale shows translated confirmation copy ("Cette action est irréversible.")
- [ ] `npm run build --workspace=@pbbls/ios` passes
- [ ] `npm run lint --workspace=@pbbls/ios` passes
- [ ] `npm run test --workspace=@pbbls/ios` passes
EOF
)"
```

- [ ] **Step 5: Update the Arkaik product map if needed**

Per `CLAUDE.md`'s Arkaik instruction: this change adds a destructive flow on three existing screens and does not introduce a new screen, route, model, or endpoint. No node/edge changes are needed in `docs/arkaik/bundle.json`. Skip.

---

## Self-Review

**Spec coverage check** — every section of the spec maps to a task:

- *Component shape (`PebbleRow`, contextMenu, onTap/onDelete)* → Task 1.
- *Responsibility split (parents own confirmation/alert/RPC/reload)* → Tasks 2, 3, 4 add the same dialogs and `delete(_:)` to each parent.
- *Extended queries in `SoulDetailView` and `CollectionDetailView`* → Tasks 3 step 1 and 4 step 1.
- *Deletion via `delete_pebble` RPC* → Tasks 2/3/4 step 5.
- *Localization (`This can't be undone.` new; others reused)* → Task 5 adds the entry; Task 6 covers it with a test; Task 7 step 6 verifies the catalog state in Xcode.
- *Build + xcodegen step* → Task 1 step 3.
- *Storage cleanup out of scope* → not implemented, called out in the PR body.
- *Manual verification* → Task 7.
- *PR workflow (branch name, conventional title, labels, milestone)* → Task 8.

**Placeholder scan:** No "TBD" / "TODO" / "implement later" / "appropriate error handling" patterns. Each step has either explicit code or an explicit shell command with expected output.

**Type/method consistency:**
- `PebbleRow(pebble:onTap:onDelete:)` — same signature in Tasks 1, 2, 3, 4.
- `pendingDeletion: Pebble?` and `deleteError: String?` — same names in Tasks 2, 3, 4.
- `delete(_ pebble: Pebble) async` — same signature in Tasks 2, 3, 4.
- The RPC name and parameter — `"delete_pebble"` with `["p_pebble_id": pebble.id.uuidString]` — identical across all three call sites (matches the web provider and the migration definition).
- The localization key string — `"This can't be undone."` — identical between the catalog entry (Task 5), the SwiftUI Text usage (Tasks 2/3/4), and the test (Task 6).
