# Profile Settings sheet — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the placeholder `SettingsStubSheet` on iOS Profile with a real Edit-mode `SettingsSheet` that lets the user change their glyph, display name, password (email-only accounts), view linked SSO providers (SSO accounts), and reach Terms/Privacy. Closes #452.

**Architecture:** Single-file SwiftUI sheet (`SettingsSheet.swift`) following the `EditCollectionSheet` pattern — `Form` with `Section`s, `Cancel`/`Save` toolbar, view-local state, `Save` disabled until dirty. Persists via the existing `update_profile` RPC (added in PR #453) and `client.auth.update(user:)` for password. Provider detection comes from `client.auth.currentSession?.user.identities`. No new service classes — Sheet talks to `SupabaseService` directly, mirroring how `EditCollectionSheet`, `CreateCollectionSheet`, etc. already do it.

**Tech Stack:** SwiftUI (iOS 17), Supabase Swift SDK, `os.Logger`, `Localizable.xcstrings`.

---

## Context for the executor

Read these before starting any task:

- **Spec:** `docs/superpowers/specs/2026-05-16-ios-profile-redesign-and-settings-design.md` § Issue 3.
- **Migration (already merged in PR #453):** `packages/supabase/supabase/migrations/20260516104231_profile_glyph_and_engagement.sql` — `update_profile(p_display_name text, p_glyph_id uuid)` returns the updated `profiles` row; null arg means "don't change". `glyph_id` cannot be cleared by passing null — we accept that asymmetry.
- **Stub to replace:** `apps/ios/Pebbles/Features/Profile/Sheets/SettingsStubSheet.swift` (28 LOC). Wired in `ProfileView.swift:80-82` via `.sheet(isPresented: $isPresentingSettings) { SettingsStubSheet() }`.
- **Glyph picker (existing):** `apps/ios/Pebbles/Features/Glyph/Views/GlyphPickerSheet.swift` — signature `GlyphPickerSheet(currentGlyphId: UUID?, onSelected: (Glyph) -> Void)`. Loads its own data. Already includes a "Carve new glyph" CTA leading to `GlyphCarveSheet`. We present it as a sheet from inside `SettingsSheet`; on selection we hold the new `Glyph` in local state and persist only on `Save`.
- **Glyph thumbnail (existing):** `apps/ios/Pebbles/Features/Glyph/Views/GlyphThumbnail.swift` — `GlyphThumbnail(strokes:side:strokeColor:backgroundColor:)`.
- **Legal sheet (existing):** `apps/ios/Pebbles/Features/Auth/LegalDocumentSheet.swift` presented via `.sheet(item: LegalDoc?) { LegalDocumentSheet(url: doc.url) }`. `LegalDoc` is an enum with `.terms` and `.privacy`. See `ProfileView.swift:126-150` for current usage.
- **Pattern reference:** `apps/ios/Pebbles/Features/Profile/Sheets/EditCollectionSheet.swift` — Form/Section structure, `Cancel`/`Save` toolbar with `ProgressView` while saving, inline error Section. Mimic this exactly.
- **Text input style:** Inside `Form` sections, plain `TextField` / `SecureField` (NOT `PebblesTextInput`, which is for auth screens — see `EditCollectionSheet.swift:40` for precedent).
- **Theming:** `.pebblesScreen()` modifier inside `NavigationStack`. Colors via `Color.pebblesMutedForeground`, etc.
- **Auth API for password change:** The Supabase Swift SDK exposes `client.auth.update(user: UserAttributes(...))` where `UserAttributes` has a `password: String?` field. Pattern reference: SDK source in `node_modules` is not Swift, but the SDK shape is `UserAttributes(email:, phone:, password:, data:)`. Confirm exact symbol exists by reading `Pods`/SwiftPM Auth source if uncertain; the import is `import Supabase`.
- **iOS conventions:** `apps/ios/CLAUDE.md` — `@Observable`, `os.Logger`, `Localizable.xcstrings` for every user-facing string in both `en` and `fr`, `LocalizedStringResource` for view-model copy, never `print`, never iOS-16 `if #available` guards.
- **Project conventions:** branch `feat/452-profile-settings-sheet`, conventional commits, PR title `feat(ios): profile settings sheet`, body starts with `Resolves #452`, labels inherit from issue (`feat`, `ios`, `ui`) + milestone `M22 · Bounce karma & gamification`.

## Open-question resolutions (decided here, not during impl)

These are answered up-front so executor doesn't stall on the spec's open questions:

1. **Current-password field** → **drop it.** The Supabase Swift SDK's `auth.update(user: .init(password:))` works without a current-password proof when the session is fresh, and the cleanest path is to not show a field whose value we don't validate. The spec's default was option (b), client-side re-verify via no-op signIn, but that path is fragile (an email-with-+ scrubber would reject the very email we just signed in with; rate limits etc.). Surfaces the "needs recent login" error inline if the SDK returns one.
2. **Replay onboarding** → **drop entirely.** Matches the mockup; not in Settings, not in Profile.
3. **Banner-side glyph placeholder** → not in scope of #452 (Issue 2 already shipped `ProfileBanner`).
4. **Engagement copy pluralization** → not in scope of #452.

---

## File Structure

**Create:**
- `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift` — the full sheet, single file (~280 LOC, comparable to `CreateSoulSheet` at 167 + extra sections).

**Modify:**
- `apps/ios/Pebbles/Features/Profile/ProfileView.swift` — swap `SettingsStubSheet` for `SettingsSheet`, pass current `displayName` / `glyphId` / `glyphStrokes` in, refresh local state on save.
- `apps/ios/Pebbles/Resources/Localizable.xcstrings` — add new strings in `en` + `fr`.
- `docs/arkaik/bundle.json` — repurpose existing `V-settings` node (currently described as "App settings including privacy, notifications, and account" with stale `e-V-home-V-settings` edge) into the iOS Profile Settings sheet; drop the stale home edge; add `e-V-profile-V-settings` edge.

**Delete:**
- `apps/ios/Pebbles/Features/Profile/Sheets/SettingsStubSheet.swift` — fully replaced.

No new components, no service-layer changes, no `project.yml` change (new file lives under the existing `Features/Profile/Sheets/` glob).

---

## Task 1: Create the feature branch

**Files:** none.

- [ ] **Step 1: Create and switch to the branch**

Run:

```bash
git checkout -b feat/452-profile-settings-sheet
```

Expected: `Switched to a new branch 'feat/452-profile-settings-sheet'`.

- [ ] **Step 2: Confirm clean tree**

Run: `git status`
Expected: `nothing to commit, working tree clean`.

---

## Task 2: Scaffold SettingsSheet with the toolbar and header section

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift`

Build the outer shell + header (glyph thumbnail tappable → `GlyphPickerSheet`). No persistence yet, no other sections. The `Save` button is disabled (no dirtiness yet).

- [ ] **Step 1: Write the file**

```swift
import SwiftUI
import os

/// Edit-mode sheet for profile management. Presented from `ProfileView`'s gear button.
///
/// Sections shown depend on whether the account is SSO (Apple/Google) or email-only:
/// SSO sees a read-only Providers list; email-only sees a Password change form.
struct SettingsSheet: View {
    let initialDisplayName: String
    let initialGlyphId: UUID?
    let initialGlyphStrokes: [GlyphStroke]?
    let email: String?
    let onSaved: (_ displayName: String, _ glyph: Glyph?) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var displayName: String
    @State private var pickedGlyph: Glyph?
    @State private var currentPassword: String = ""
    @State private var newPassword: String = ""
    @State private var isSaving = false
    @State private var saveError: String?
    @State private var presentedLegalDoc: LegalDoc?
    @State private var isPresentingGlyphPicker = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "settings-sheet")

    init(
        initialDisplayName: String,
        initialGlyphId: UUID?,
        initialGlyphStrokes: [GlyphStroke]?,
        email: String?,
        onSaved: @escaping (_ displayName: String, _ glyph: Glyph?) -> Void
    ) {
        self.initialDisplayName = initialDisplayName
        self.initialGlyphId = initialGlyphId
        self.initialGlyphStrokes = initialGlyphStrokes
        self.email = email
        self.onSaved = onSaved
        self._displayName = State(initialValue: initialDisplayName)
    }

    private var trimmedDisplayName: String {
        displayName.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var isDirty: Bool {
        let nameChanged = trimmedDisplayName != initialDisplayName && !trimmedDisplayName.isEmpty
        let glyphChanged = pickedGlyph != nil && pickedGlyph?.id != initialGlyphId
        let passwordSet = !newPassword.isEmpty
        return nameChanged || glyphChanged || passwordSet
    }

    private var currentStrokes: [GlyphStroke]? {
        pickedGlyph?.strokes ?? initialGlyphStrokes
    }

    var body: some View {
        NavigationStack {
            Form {
                headerSection
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if isSaving {
                        ProgressView()
                    } else {
                        Button("Save") { Task { await save() } }
                            .disabled(!isDirty)
                    }
                }
            }
            .pebblesScreen()
            .sheet(isPresented: $isPresentingGlyphPicker) {
                GlyphPickerSheet(currentGlyphId: pickedGlyph?.id ?? initialGlyphId) { glyph in
                    pickedGlyph = glyph
                }
            }
            .sheet(item: $presentedLegalDoc) { doc in
                LegalDocumentSheet(url: doc.url)
                    .ignoresSafeArea()
            }
        }
    }

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

    @ViewBuilder
    private var glyphView: some View {
        if let strokes = currentStrokes, !strokes.isEmpty {
            GlyphThumbnail(strokes: strokes, side: 120)
        } else {
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.secondary.opacity(0.08))
                .frame(width: 120, height: 120)
                .overlay {
                    Image(systemName: "scribble")
                        .font(.title)
                        .foregroundStyle(Color.pebblesMutedForeground)
                }
        }
    }

    private func save() async {
        // Implemented in Task 6.
    }
}

#Preview("Email user") {
    SettingsSheet(
        initialDisplayName: "Alexis",
        initialGlyphId: nil,
        initialGlyphStrokes: nil,
        email: "hello@bohns.design",
        onSaved: { _, _ in }
    )
    .environment(SupabaseService())
}
```

- [ ] **Step 2: xcodegen + build the iOS app**

Run from repo root:

```bash
npm run generate --workspace=@pbbls/ios && npm run build --workspace=@pbbls/ios
```

Expected: build succeeds. (We haven't wired the sheet anywhere yet — it compiles standalone.)

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift
git commit -m "$(cat <<'EOF'
feat(ios): scaffold settings sheet with glyph header

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Add Informations + Legal sections

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift`

Add the always-present sections: editable display name + read-only email, and Terms · Privacy footer links. Both go inside the existing `Form`, below `headerSection`.

- [ ] **Step 1: Insert the two new sections in `body`**

Replace the body's `Form` block:

```swift
            Form {
                headerSection
            }
```

with:

```swift
            Form {
                headerSection
                informationsSection
                legalSection
            }
```

- [ ] **Step 2: Add the two computed properties**

Insert above `save()`:

```swift
    private var informationsSection: some View {
        Section("Informations") {
            HStack {
                Text("Name")
                    .foregroundStyle(Color.pebblesMutedForeground)
                Spacer()
                TextField("Your name", text: $displayName)
                    .multilineTextAlignment(.trailing)
                    .textInputAutocapitalization(.words)
                    .autocorrectionDisabled(false)
            }
            HStack {
                Text("Email")
                    .foregroundStyle(Color.pebblesMutedForeground)
                Spacer()
                Text(email ?? "—")
                    .foregroundStyle(Color.pebblesMutedForeground)
            }
        }
    }

    private var legalSection: some View {
        Section("Legal") {
            Button { presentedLegalDoc = .terms } label: {
                Label("Terms of Service", systemImage: "doc.text")
            }
            .buttonStyle(.plain)
            Button { presentedLegalDoc = .privacy } label: {
                Label("Privacy Policy", systemImage: "hand.raised")
            }
            .buttonStyle(.plain)
        }
    }
```

- [ ] **Step 3: Build**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: success.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift
git commit -m "$(cat <<'EOF'
feat(ios): add informations and legal sections to settings sheet

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Add Providers section (SSO accounts) and detection helper

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift`

Detect linked non-email identities from the current session. Show a read-only list of provider rows; hide the section entirely for email-only accounts.

- [ ] **Step 1: Add provider detection + the section**

Insert above `informationsSection`:

```swift
    private struct LinkedProvider: Identifiable, Hashable {
        let id: String       // provider raw value: "apple" | "google" | …
        let label: LocalizedStringResource
        let systemImage: String
    }

    private var linkedProviders: [LinkedProvider] {
        let identities = supabase.session?.user.identities ?? []
        return identities.compactMap { identity in
            switch identity.provider {
            case "apple":
                return LinkedProvider(id: "apple", label: "Apple", systemImage: "apple.logo")
            case "google":
                return LinkedProvider(id: "google", label: "Google", systemImage: "g.circle")
            case "email":
                return nil
            default:
                return nil
            }
        }
    }

    private var isSSO: Bool { !linkedProviders.isEmpty }

    private var providersSection: some View {
        Section("Providers") {
            ForEach(linkedProviders) { provider in
                HStack(spacing: 12) {
                    Image(systemName: provider.systemImage)
                        .foregroundStyle(Color.pebblesMutedForeground)
                    Text(provider.label)
                    Spacer()
                }
            }
        }
    }
```

Note on `identity.provider`: the Supabase Swift SDK exposes each `UserIdentity` with a `provider: String` property (raw provider name, e.g. `"email"`, `"apple"`, `"google"`). If the field happens to be named differently in the installed SDK version, surface this immediately — do not invent a workaround. Symbol lookup: open `~/Library/Developer/Xcode/DerivedData/.../SourcePackages/checkouts/auth-swift/Sources/Auth/Types.swift` and grep `struct UserIdentity`.

- [ ] **Step 2: Wire the section conditionally into `body`**

Change the `Form` block to:

```swift
            Form {
                headerSection
                informationsSection
                if isSSO {
                    providersSection
                }
                legalSection
            }
```

- [ ] **Step 3: Build**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: success. If `identity.provider` field name is wrong, the error is the only acceptable place to deviate from this plan — fix locally by reading the SDK source as instructed above.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift
git commit -m "$(cat <<'EOF'
feat(ios): add linked providers section to settings sheet

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Add Password section (email-only accounts)

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift`

Only shown when `isSSO == false`. Single field — "New password". (Per plan's open-question resolution, we drop "Current password" as the SDK doesn't enforce it and we can't faithfully verify client-side.)

- [ ] **Step 1: Add the section**

Insert above `legalSection`:

```swift
    private var passwordSection: some View {
        Section {
            SecureField("New password", text: $newPassword)
                .textContentType(.newPassword)
                .autocorrectionDisabled(true)
                .textInputAutocapitalization(.never)
        } header: {
            Text("Password")
        } footer: {
            Text("Leave blank to keep your current password.")
        }
    }
```

- [ ] **Step 2: Wire it conditionally into `body`**

Change the `Form` block to:

```swift
            Form {
                headerSection
                informationsSection
                if isSSO {
                    providersSection
                } else {
                    passwordSection
                }
                legalSection
            }
```

- [ ] **Step 3: Build**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: success.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift
git commit -m "$(cat <<'EOF'
feat(ios): add password section to settings sheet for email accounts

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Implement Save (display name + glyph + password) and inline error

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift`

Three sequential operations inside one `Task`: `update_profile` RPC for name+glyph (only if those changed), then `client.auth.update(user:)` for the password (only if non-empty). Any error logs via `os.Logger`, surfaces inline in a red Section, and keeps the sheet open. Full success calls `onSaved(...)` then `dismiss()`.

- [ ] **Step 1: Add the error Section**

Insert above `legalSection` in `body`'s `Form`:

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
```

- [ ] **Step 2: Add the update payload + replace `save()`**

Add at the bottom of the file, outside the `SettingsSheet` struct:

```swift
/// Wire shape for `update_profile` RPC. Null fields tell Postgres "don't change".
private struct UpdateProfileParams: Encodable {
    let p_display_name: String?
    let p_glyph_id: String?
}
```

Replace `save()` with:

```swift
    private func save() async {
        guard isDirty, !isSaving else { return }
        isSaving = true
        saveError = nil

        let nameToSend: String? = {
            let trimmed = trimmedDisplayName
            return (trimmed != initialDisplayName && !trimmed.isEmpty) ? trimmed : nil
        }()
        let glyphIdToSend: String? = {
            guard let picked = pickedGlyph, picked.id != initialGlyphId else { return nil }
            return picked.id.uuidString
        }()
        let passwordToSend: String? = newPassword.isEmpty ? nil : newPassword

        do {
            if nameToSend != nil || glyphIdToSend != nil {
                let params = UpdateProfileParams(
                    p_display_name: nameToSend,
                    p_glyph_id: glyphIdToSend
                )
                try await supabase.client
                    .rpc("update_profile", params: params)
                    .execute()
            }

            if let passwordToSend {
                try await supabase.client.auth.update(
                    user: UserAttributes(password: passwordToSend)
                )
            }

            onSaved(nameToSend ?? initialDisplayName, pickedGlyph)
            dismiss()
        } catch {
            logger.error("settings save failed: \(error.localizedDescription, privacy: .private)")
            saveError = "Couldn't save your changes. Please try again."
            isSaving = false
        }
    }
```

- [ ] **Step 3: Build**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: success. If `UserAttributes(password:)` doesn't compile, find the actual initializer signature in the Auth SDK (`UserAttributes` source under `~/Library/Developer/Xcode/DerivedData/.../auth-swift/Sources/Auth/Types.swift` — likely `UserAttributes(email:phone:password:data:nonce:)`). Pass `nil` for unused fields.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift
git commit -m "$(cat <<'EOF'
feat(ios): persist settings via update_profile rpc and auth.update

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Wire SettingsSheet into ProfileView and delete the stub

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/ProfileView.swift`
- Delete: `apps/ios/Pebbles/Features/Profile/Sheets/SettingsStubSheet.swift`

ProfileView already owns `displayName`, `createdAt`, `glyphId` (via the `ProfileRow` it fetches in `loadProfile()`) and `glyphStrokes`. It needs the user's email — pull from `supabase.session?.user.email`. On `onSaved`, patch local state so the banner reflects the change immediately without a refetch.

- [ ] **Step 1: Replace the sheet presentation in ProfileView.swift**

Find:

```swift
        .sheet(isPresented: $isPresentingSettings) {
            SettingsStubSheet()
        }
```

Replace with:

```swift
        .sheet(isPresented: $isPresentingSettings) {
            SettingsSheet(
                initialDisplayName: profile?.displayName ?? "",
                initialGlyphId: profile?.glyphId,
                initialGlyphStrokes: glyphStrokes,
                email: supabase.session?.user.email,
                onSaved: { newName, newGlyph in
                    if var current = profile {
                        current.displayName = newName
                        current.glyphId = newGlyph?.id ?? current.glyphId
                        profile = current
                    }
                    if let strokes = newGlyph?.strokes {
                        glyphStrokes = strokes
                    }
                }
            )
        }
```

- [ ] **Step 2: Make `ProfileRow` mutable**

Find:

```swift
private struct ProfileRow: Decodable {
    let displayName: String?
    let createdAt: Date
    let glyphId: UUID?
```

Replace `let` with `var` for `displayName` and `glyphId` (`createdAt` stays `let` — never mutated):

```swift
private struct ProfileRow: Decodable {
    var displayName: String?
    let createdAt: Date
    var glyphId: UUID?
```

- [ ] **Step 3: Delete the stub file**

```bash
rm apps/ios/Pebbles/Features/Profile/Sheets/SettingsStubSheet.swift
```

- [ ] **Step 4: Regenerate xcode project + build**

```bash
npm run generate --workspace=@pbbls/ios && npm run build --workspace=@pbbls/ios
```

Expected: success. (`xcodegen` picks up the deletion automatically since the project uses globbing under `Features/Profile/Sheets/`.)

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/ProfileView.swift apps/ios/Pebbles/Features/Profile/Sheets/SettingsStubSheet.swift apps/ios/Pebbles.xcodeproj
git commit -m "$(cat <<'EOF'
feat(ios): swap settings stub for real sheet in profile

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

If `Pebbles.xcodeproj` is gitignored (per `apps/ios/CLAUDE.md` it is), the `git add` will quietly skip it — that's fine.

---

## Task 8: Update Arkaik bundle

**Files:**
- Modify: `docs/arkaik/bundle.json`

The bundle already has a `V-settings` node (lines 398-406) but it's described as a generic "App settings including privacy, notifications, and account" with a stale edge from `V-home`. We repurpose it for the iOS Profile Settings sheet per the spec ("add a SettingsSheet node under the Profile screen, edge `opens_settings` from Profile").

- [ ] **Step 1: Update the V-settings node**

Find (lines ~398-406):

```json
    {
      "id": "V-settings",
      "project_id": "pebbles",
      "species": "view",
      "title": "Settings",
      "description": "App settings including privacy, notifications, and account.",
      "status": "idea",
      "platforms": ["web", "ios", "android"]
    },
```

Replace with:

```json
    {
      "id": "V-settings",
      "project_id": "pebbles",
      "species": "view",
      "title": "Settings",
      "description": "Profile settings sheet. iOS (#452): Edit-mode sheet presented from Profile's gear button. Sections: header glyph (tap → GlyphPickerSheet), Informations (editable display_name, read-only email), Providers (read-only Apple/Google list for SSO accounts) or Password (new password field for email-only accounts), Legal (Terms, Privacy). Persists name+glyph via update_profile RPC and password via auth.update.",
      "status": "development",
      "platforms": ["ios"]
    },
```

- [ ] **Step 2: Drop the stale V-home → V-settings edge and add V-profile → V-settings**

Find:

```json
    { "id": "e-V-home-V-settings", "project_id": "pebbles", "source_id": "V-home", "target_id": "V-settings", "edge_type": "composes" },
```

Replace with:

```json
    { "id": "e-V-profile-V-settings", "project_id": "pebbles", "source_id": "V-profile", "target_id": "V-settings", "edge_type": "composes" },
```

- [ ] **Step 3: Bump project.updated_at**

Find:

```json
    "updated_at": "2026-05-14T00:00:00.000Z"
```

(near the top, inside the `project` object). Replace with:

```json
    "updated_at": "2026-05-16T00:00:00.000Z"
```

- [ ] **Step 4: Commit**

```bash
git add docs/arkaik/bundle.json
git commit -m "$(cat <<'EOF'
docs(arkaik): repurpose v-settings node for ios profile settings sheet

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Localization audit

**Files:**
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`

Every literal passed to `Text`, `Button`, `.navigationTitle`, `Section("…")`, `Label`, and `TextField/SecureField` placeholders is auto-extracted by Xcode's `SWIFT_EMIT_LOC_STRINGS=YES`. After building (Task 8 already did), open `Localizable.xcstrings` and ensure every new entry has both `en` and `fr` values, with state `translated`.

The new keys this sheet introduces:

- `"Settings"` (navigationTitle — likely already exists)
- `"Cancel"`, `"Save"` (toolbar — already exist)
- `"Informations"`, `"Legal"`, `"Providers"`, `"Password"` (Section headers)
- `"Name"`, `"Your name"`, `"Email"`
- `"Terms of Service"`, `"Privacy Policy"` (likely already exist — check `LegalLinks` in ProfileView)
- `"New password"`
- `"Leave blank to keep your current password."`
- `"Couldn't save your changes. Please try again."` (likely already exists from `EditCollectionSheet`)
- `"Apple"`, `"Google"` — brand names. Per `apps/ios/CLAUDE.md`, brand-name literals that must render in English regardless of locale should use `Text(verbatim:)`.

- [ ] **Step 1: Fix Apple/Google to use `Text(verbatim:)`**

In `SettingsSheet.swift`, the `LinkedProvider.label` is `LocalizedStringResource` for "Apple"/"Google" — this means Xcode will extract them as localizable strings, which is wrong (brand names). Change the model:

Find:

```swift
    private struct LinkedProvider: Identifiable, Hashable {
        let id: String       // provider raw value: "apple" | "google" | …
        let label: LocalizedStringResource
        let systemImage: String
    }
```

Replace with:

```swift
    private struct LinkedProvider: Identifiable, Hashable {
        let id: String       // provider raw value: "apple" | "google" | …
        let label: String    // brand name; rendered verbatim, not localized.
        let systemImage: String
    }
```

Find:

```swift
            case "apple":
                return LinkedProvider(id: "apple", label: "Apple", systemImage: "apple.logo")
            case "google":
                return LinkedProvider(id: "google", label: "Google", systemImage: "g.circle")
```

Keep as-is — the `String` field now correctly skips extraction.

Find:

```swift
                    Text(provider.label)
```

Replace with:

```swift
                    Text(verbatim: provider.label)
```

- [ ] **Step 2: Build to populate the xcstrings file**

```bash
npm run build --workspace=@pbbls/ios
```

- [ ] **Step 3: Open `Localizable.xcstrings` and fill in `fr` for any `New` or `Stale` rows**

Open in Xcode: `apps/ios/Pebbles/Resources/Localizable.xcstrings`. For each new key introduced by this PR, set the French translation:

| Key                                              | fr                                                          |
|--------------------------------------------------|-------------------------------------------------------------|
| `Informations`                                   | `Informations`                                              |
| `Legal`                                          | `Légal`                                                     |
| `Providers`                                      | `Fournisseurs`                                              |
| `Password`                                       | `Mot de passe`                                              |
| `Name`                                           | `Nom`                                                       |
| `Your name`                                      | `Votre nom`                                                 |
| `Email`                                          | `Email`                                                     |
| `New password`                                   | `Nouveau mot de passe`                                      |
| `Leave blank to keep your current password.`     | `Laissez vide pour conserver votre mot de passe actuel.`    |

Skip keys that already exist with both translations (`Cancel`, `Save`, `Settings`, `Terms of Service`, `Privacy Policy`, `Couldn't save your changes. Please try again.`). Set every new entry's state to `translated` for both `en` and `fr`.

- [ ] **Step 4: Commit the xcstrings update**

```bash
git add apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "$(cat <<'EOF'
feat(ios): localize settings sheet strings to fr

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: Final verification + open the PR

**Files:** none.

- [ ] **Step 1: Final build**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: success with no warnings about `New`/`Stale` localization rows.

- [ ] **Step 2: Push the branch**

```bash
git push -u origin feat/452-profile-settings-sheet
```

- [ ] **Step 3: Open the PR (inheriting #452 labels + milestone)**

```bash
gh pr create --title "feat(ios): profile settings sheet" --label feat --label ios --label ui --milestone "M22 · Bounce karma & gamification" --body "$(cat <<'EOF'
## Summary

Resolves #452.

- Replaces `SettingsStubSheet` with a real Edit-mode `SettingsSheet` opened from the Profile gear button.
- Sections: header glyph (tap → `GlyphPickerSheet`), Informations (editable `display_name`, read-only email), Providers (read-only Apple/Google for SSO accounts) **or** Password (single new-password field for email-only accounts), Legal (Terms · Privacy).
- Save runs `update_profile` RPC (name + glyph) and `auth.update(user:)` (password) sequentially; errors surface inline, success dismisses the sheet and patches the Profile banner in place.
- Drops the spec's "Current password" field — Supabase doesn't enforce it server-side and client-side re-verify via no-op `signIn` is fragile.
- Drops the spec's "Replay onboarding" affordance — matches the mockup.
- Repurposes the stale `V-settings` arkaik node for the iOS Profile Settings sheet; drops `V-home → V-settings` edge, adds `V-profile → V-settings`.

## Test plan

- [ ] Open Profile, tap gear: sheet presents with current name, glyph (or placeholder), and email.
- [ ] Email-only account: Password section shows; Providers section absent.
- [ ] SSO account: Providers section shows linked Apple/Google; Password section absent.
- [ ] Edit name → Save → sheet dismisses, banner reflects new name.
- [ ] Tap header glyph → `GlyphPickerSheet` opens → pick a glyph → Save → banner reflects new glyph.
- [ ] Email-only: enter new password → Save → next sign-in uses the new password.
- [ ] Force a save error (turn off network) → red inline error appears, sheet stays open.
- [ ] Tap Terms / Privacy → `LegalDocumentSheet` opens as before.
- [ ] French locale: every label translated, no `New`/`Stale` rows in `Localizable.xcstrings`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Expected: PR URL printed.

---

## Self-review checklist (already run)

- **Spec coverage:** every Issue 3 requirement maps to a task. Cancel/Save toolbar (Task 2), header glyph picker (Task 2), Informations (Task 3), Providers (Task 4), Password (Task 5), Save action (Task 6), Legal (Task 3), inline errors (Task 6), Arkaik update (Task 8), localization (Task 9).
- **Placeholder scan:** no TBDs, no "implement later", no "add error handling" without showing it, no "similar to Task N". Every code block is complete.
- **Type consistency:** `pickedGlyph: Glyph?` defined in Task 2 is used in Tasks 6 and 7; `LinkedProvider.label` switches from `LocalizedStringResource` to `String` in Task 9 with both call sites updated.
