# iOS Lab Release-Date Sort + Timeline (#438) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On iOS, sort the Lab changelog by `released_at` (with `published_at` fallback) and render the three feature-status sections (changelog, in-progress, backlog) using a vertical timeline that mirrors the web's `LogTimeline`.

**Architecture:** Add an optional `releasedAt` field to the `Log` decodable, change the changelog ordering in `LogsService`, introduce a new `LogTimeline` SwiftUI view that replaces `LogRow` across `LabView` and `LogListView`, then delete the now-unused `LogRow`. The DB view `v_logs_with_counts` already exposes `released_at` (migration `20260514000001`), so no schema work is required.

**Tech Stack:** Swift 5.9 / iOS 17 / SwiftUI / Swift Testing / supabase-swift / xcodegen.

**Spec:** `docs/superpowers/specs/2026-05-14-issue-438-ios-changelog-release-date-design.md`.

**Branch:** `feat/438-ios-changelog-release-date` (already created; spec committed).

---

## File touch list

- Modify: `apps/ios/Pebbles/Features/Lab/Models/Log.swift` — add `releasedAt: Date?` + coding key, pass through in `withAdjustedCount`.
- Modify: `apps/ios/PebblesTests/Features/Lab/LossyLogArrayTests.swift` — extend fixtures and add round-trip test for `released_at`.
- Modify: `apps/ios/Pebbles/Features/Lab/Services/LogsService.swift` — reorder `changelog(limit:)` by `released_at` then `published_at`.
- Create: `apps/ios/Pebbles/Features/Lab/Components/LogTimeline.swift` — new generic timeline view (changelog / in-progress / backlog modes).
- Modify: `apps/ios/Pebbles/Features/Lab/LabView.swift` — replace three `ForEach { LogRow }` blocks with `LogTimeline`.
- Modify: `apps/ios/Pebbles/Features/Lab/Views/LogListView.swift` — replace `ForEach { LogRow }` with `LogTimeline`.
- Delete: `apps/ios/Pebbles/Features/Lab/Components/LogRow.swift` — no remaining call sites once the swaps land.
- Regenerate (git-ignored, do not commit): `apps/ios/Pebbles.xcodeproj/**` via `xcodegen generate`.

---

## Task 1: Add `releasedAt` to the `Log` model (TDD)

**Files:**
- Modify: `apps/ios/PebblesTests/Features/Lab/LossyLogArrayTests.swift`
- Modify: `apps/ios/Pebbles/Features/Lab/Models/Log.swift`

The existing `LossyLogArrayTests.swift` decodes `Log` rows through the same lossy wrapper and date decoder used in production. We extend its fixture to include `released_at` and add one focused test that asserts the new field round-trips.

- [ ] **Step 1: Add the failing test**

Open `apps/ios/PebblesTests/Features/Lab/LossyLogArrayTests.swift`. After the existing `nonArrayThrows` test, append the following new test inside the `LossyLogArrayTests` suite:

```swift
    @Test("released_at decodes into Log.releasedAt when present")
    func decodesReleasedAt() throws {
        let row = """
        {
          "id": "44444444-4444-4444-4444-444444444444",
          "species": "feature",
          "platform": "ios",
          "status": "shipped",
          "title_en": "Shipped",
          "title_fr": null,
          "summary_en": "One line.",
          "summary_fr": null,
          "body_md_en": null,
          "body_md_fr": null,
          "cover_image_path": null,
          "external_url": null,
          "published": true,
          "published_at": "2026-04-20T12:00:00Z",
          "released_at": "2026-05-14T09:30:00Z",
          "created_at": "2026-04-20T12:00:00Z",
          "reaction_count": 0
        }
        """
        let json = Data("[\(row)]".utf8)

        let wrapper = try makeDecoder().decode(LossyLogArray.self, from: json)

        #expect(wrapper.logs.count == 1)
        let expected = ISO8601DateFormatter().date(from: "2026-05-14T09:30:00Z")
        #expect(wrapper.logs.first?.releasedAt == expected)
    }

    @Test("released_at absent decodes to nil releasedAt")
    func decodesMissingReleasedAt() throws {
        // Uses the existing validRow() fixture which does not emit released_at.
        let json = Data("[\(validRow())]".utf8)

        let wrapper = try makeDecoder().decode(LossyLogArray.self, from: json)

        #expect(wrapper.logs.count == 1)
        #expect(wrapper.logs.first?.releasedAt == nil)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
npm test --workspace=@pbbls/ios -- -only-testing:PebblesTests/LossyLogArrayTests/decodesReleasedAt -only-testing:PebblesTests/LossyLogArrayTests/decodesMissingReleasedAt
```

Expected: build failure — `Value of type 'Log' has no member 'releasedAt'` (the property does not exist yet). This is the failing-test signal.

- [ ] **Step 3: Add `releasedAt` to `Log`**

Open `apps/ios/Pebbles/Features/Lab/Models/Log.swift`.

After the `publishedAt` property, add `releasedAt`:

```swift
    let published: Bool
    let publishedAt: Date?
    let releasedAt: Date?
    let createdAt: Date
```

In `CodingKeys`, add the coding key (between `publishedAt` and `createdAt`):

```swift
        case publishedAt = "published_at"
        case releasedAt = "released_at"
        case createdAt = "created_at"
```

In `withAdjustedCount(by:)`, pass `releasedAt` through. Replace the `Log(...)` initializer call so the parameter list reads:

```swift
        Log(
            id: id,
            species: species,
            platform: platform,
            status: status,
            titleEn: titleEn,
            titleFr: titleFr,
            summaryEn: summaryEn,
            summaryFr: summaryFr,
            bodyMdEn: bodyMdEn,
            bodyMdFr: bodyMdFr,
            coverImagePath: coverImagePath,
            externalUrl: externalUrl,
            published: published,
            publishedAt: publishedAt,
            releasedAt: releasedAt,
            createdAt: createdAt,
            reactionCount: max(0, reactionCount + delta)
        )
```

- [ ] **Step 4: Run the tests to verify they pass**

Run:

```bash
npm test --workspace=@pbbls/ios -- -only-testing:PebblesTests/LossyLogArrayTests
```

Expected: every test in `LossyLogArrayTests` passes, including the two new ones (`decodesReleasedAt`, `decodesMissingReleasedAt`). The pre-existing tests must still pass — they exercise the optional decode path, and `releasedAt: Date?` defaults to `nil` when the key is absent.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Lab/Models/Log.swift \
        apps/ios/PebblesTests/Features/Lab/LossyLogArrayTests.swift
git commit -m "$(cat <<'EOF'
feat(ios): add releasedAt to Log model

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Sort the changelog by `released_at`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Lab/Services/LogsService.swift:32-47`

The web orders by `released_at desc nulls last, published_at desc` (`apps/web/lib/data/logs-api.ts:93-94`). Mirror that on iOS.

- [ ] **Step 1: Update `changelog(limit:)` ordering**

In `apps/ios/Pebbles/Features/Lab/Services/LogsService.swift`, replace the body of `changelog(limit:)` so its `base` chain reads:

```swift
    func changelog(limit: Int? = nil) async throws -> [Log] {
        let base = supabase.client
            .from("v_logs_with_counts")
            .select()
            .eq("species", value: LogSpecies.feature.rawValue)
            .eq("status", value: LogStatus.shipped.rawValue)
            .eq("published", value: true)
            .order("released_at", ascending: false, nullsFirst: false)
            .order("published_at", ascending: false)
        if let limit {
            let wrapper: LossyLogArray = try await base.limit(limit).execute().value
            return wrapper.logs
        }
        let wrapper: LossyLogArray = try await base.execute().value
        return wrapper.logs
    }
```

Leave `announcements`, `initiatives`, `backlog`, and the reaction methods untouched.

- [ ] **Step 2: Build to verify it compiles**

Run:

```bash
npm run build --workspace=@pbbls/ios
```

Expected: build succeeds. The supabase-swift `.order` overload accepts `ascending:` and `nullsFirst:` parameters — a compile error here means the SDK version differs from what the design assumes; in that case adjust the call to the available overload (typically `referencedTable:` is the only other parameter and may be omitted).

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Lab/Services/LogsService.swift
git commit -m "$(cat <<'EOF'
feat(ios): sort lab changelog by release date

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Create the `LogTimeline` view

**Files:**
- Create: `apps/ios/Pebbles/Features/Lab/Components/LogTimeline.swift`

A generic timeline view that takes a mode and a slice of logs, mirroring `apps/web/components/lab/LogTimeline.tsx`. Each entry occupies its own `List` row; the connecting line is drawn inside the leading icon column and stretches between rows.

- [ ] **Step 1: Create the file with the full component**

Create `apps/ios/Pebbles/Features/Lab/Components/LogTimeline.swift` with:

```swift
import SwiftUI

/// Vertical timeline used by the Lab tab's changelog, in-progress and
/// backlog sections. Mirrors `apps/web/components/lab/LogTimeline.tsx`:
/// a leading icon column with a connecting line, and a content column
/// showing the localized title and summary. Changelog rows also display
/// the localized release date above the title. The trailing slot lets
/// the caller attach contextual controls (e.g. a reaction button for
/// backlog items) without coupling the row to any specific action.
///
/// Render inside a `List` section. Each entry sets `.listRowSeparator(.hidden)`
/// so the connecting line stays continuous across rows.
struct LogTimeline<Trailing: View>: View {
    enum Mode {
        case changelog
        case inProgress
        case backlog
    }

    let mode: Mode
    let logs: [Log]
    @ViewBuilder var trailing: (Log) -> Trailing

    @Environment(\.locale) private var locale

    var body: some View {
        ForEach(Array(logs.enumerated()), id: \.element.id) { index, log in
            row(log: log, isLast: index == logs.count - 1)
                .listRowSeparator(.hidden)
                .listRowBackground(Color.pebblesListRow)
        }
    }

    @ViewBuilder
    private func row(log: Log, isLast: Bool) -> some View {
        HStack(alignment: .top, spacing: 12) {
            iconColumn(isLast: isLast)
            VStack(alignment: .leading, spacing: 4) {
                if mode == .changelog, let date = log.releasedAt ?? log.publishedAt {
                    Text(date, format: Date.FormatStyle(date: .long, time: .omitted))
                        .font(.footnote)
                        .foregroundStyle(Color.pebblesMutedForeground)
                }
                Text(log.title(for: locale))
                    .font(.body)
                    .foregroundStyle(Color.pebblesForeground)
                Text(log.summary(for: locale))
                    .font(.footnote)
                    .foregroundStyle(Color.pebblesMutedForeground)
                    .lineLimit(3)
            }
            Spacer(minLength: 0)
            trailing(log)
        }
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private func iconColumn(isLast: Bool) -> some View {
        VStack(spacing: 0) {
            Image(systemName: iconName)
                .font(.system(size: 14, weight: .regular))
                .foregroundStyle(iconColor)
                .padding(.top, 2)
            if !isLast {
                Rectangle()
                    .fill(Color.pebblesBorder)
                    .frame(width: 1)
                    .frame(maxHeight: .infinity)
                    .padding(.top, 4)
            }
        }
        .frame(width: 16)
    }

    private var iconName: String {
        switch mode {
        case .changelog:  return "checkmark.circle"
        case .inProgress: return "circle.dotted"
        case .backlog:    return "circle.dashed"
        }
    }

    private var iconColor: Color {
        switch mode {
        case .changelog:  return Color.pebblesAccent
        case .inProgress, .backlog: return Color.pebblesMutedForeground
        }
    }
}

extension LogTimeline where Trailing == EmptyView {
    init(mode: Mode, logs: [Log]) {
        self.init(mode: mode, logs: logs, trailing: { _ in EmptyView() })
    }
}
```

- [ ] **Step 2: Regenerate the Xcode project**

The new Swift file needs to be picked up by `project.yml`'s `Lab/Components/**` glob.

```bash
npm run generate --workspace=@pbbls/ios
```

Expected: `xcodegen` succeeds with no errors. `Pebbles.xcodeproj` is git-ignored, so nothing to stage.

- [ ] **Step 3: Build to verify it compiles**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: build succeeds. If the build fails on `Color.pebblesAccent`, `Color.pebblesBorder`, `Color.pebblesListRow`, `Color.pebblesForeground`, or `Color.pebblesMutedForeground`, those tokens exist (verified during brainstorming via grep in `Pebbles/`); a failure indicates a stale build cache — clean and retry: `xcodebuild clean -scheme Pebbles && npm run build --workspace=@pbbls/ios`.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Lab/Components/LogTimeline.swift
git commit -m "$(cat <<'EOF'
feat(ios): add LogTimeline component for lab sections

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Swap `LabView` rows for `LogTimeline`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Lab/LabView.swift:67-113`

Replace the three `ForEach { LogRow }` blocks (Changelog, In progress, Backlog) with `LogTimeline`. The Backlog block keeps the `ReactionButton` via the timeline's trailing slot. "See all" `NavigationLink` rows under Changelog and Backlog are unchanged.

- [ ] **Step 1: Replace the Changelog section body**

In `apps/ios/Pebbles/Features/Lab/LabView.swift`, find the Changelog section block (currently lines ~67-81):

```swift
                if !changelog.isEmpty {
                    Section("Changelog") {
                        ForEach(changelog) { log in
                            LogRow(log: log)
                                .listRowBackground(Color.pebblesListRow)
                        }
                        NavigationLink {
                            LogListView(mode: .changelog)
                        } label: {
                            Label("See all", systemImage: "arrow.right")
                                .font(.footnote.weight(.semibold))
                        }
                        .listRowBackground(Color.pebblesListRow)
                    }
                }
```

Replace with:

```swift
                if !changelog.isEmpty {
                    Section("Changelog") {
                        LogTimeline(mode: .changelog, logs: changelog)
                        NavigationLink {
                            LogListView(mode: .changelog)
                        } label: {
                            Label("See all", systemImage: "arrow.right")
                                .font(.footnote.weight(.semibold))
                        }
                        .listRowBackground(Color.pebblesListRow)
                    }
                }
```

- [ ] **Step 2: Replace the In-progress section body**

Find the In-progress section block (currently lines ~83-90):

```swift
                if !initiatives.isEmpty {
                    Section("In progress") {
                        ForEach(initiatives) { log in
                            LogRow(log: log)
                                .listRowBackground(Color.pebblesListRow)
                        }
                    }
                }
```

Replace with:

```swift
                if !initiatives.isEmpty {
                    Section("In progress") {
                        LogTimeline(mode: .inProgress, logs: initiatives)
                    }
                }
```

- [ ] **Step 3: Replace the Backlog section body**

Find the Backlog section block (currently lines ~92-113):

```swift
                if !backlog.isEmpty {
                    Section("Backlog") {
                        ForEach(backlog) { log in
                            LogRow(log: log) {
                                ReactionButton(
                                    count: log.reactionCount,
                                    isReacted: reactedIds.contains(log.id)
                                ) {
                                    Task { await toggle(log) }
                                }
                            }
                            .listRowBackground(Color.pebblesListRow)
                        }
                        NavigationLink {
                            LogListView(mode: .backlog)
                        } label: {
                            Label("See all", systemImage: "arrow.right")
                                .font(.footnote.weight(.semibold))
                        }
                        .listRowBackground(Color.pebblesListRow)
                    }
                }
```

Replace with:

```swift
                if !backlog.isEmpty {
                    Section("Backlog") {
                        LogTimeline(mode: .backlog, logs: backlog) { log in
                            ReactionButton(
                                count: log.reactionCount,
                                isReacted: reactedIds.contains(log.id)
                            ) {
                                Task { await toggle(log) }
                            }
                        }
                        NavigationLink {
                            LogListView(mode: .backlog)
                        } label: {
                            Label("See all", systemImage: "arrow.right")
                                .font(.footnote.weight(.semibold))
                        }
                        .listRowBackground(Color.pebblesListRow)
                    }
                }
```

- [ ] **Step 4: Build to verify it compiles**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: build succeeds. If it fails citing `LogRow`, check that none of the three replacements left a stray `LogRow` reference behind.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Lab/LabView.swift
git commit -m "$(cat <<'EOF'
feat(ios): render lab sections with LogTimeline

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Swap `LogListView` rows for `LogTimeline`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Lab/Views/LogListView.swift:52-68`

The full-page "See all" list mirrors the parent sections — same swap.

- [ ] **Step 1: Replace the `List` content**

In `apps/ios/Pebbles/Features/Lab/Views/LogListView.swift`, find the `List` block in `content`:

```swift
        } else {
            List {
                ForEach(logs) { log in
                    LogRow(log: log) {
                        if mode == .backlog {
                            ReactionButton(
                                count: log.reactionCount,
                                isReacted: reactedIds.contains(log.id)
                            ) {
                                Task { await toggle(log) }
                            }
                        }
                    }
                    .listRowBackground(Color.pebblesListRow)
                }
            }
        }
```

Replace with:

```swift
        } else {
            List {
                switch mode {
                case .changelog:
                    LogTimeline(mode: .changelog, logs: logs)
                case .backlog:
                    LogTimeline(mode: .backlog, logs: logs) { log in
                        ReactionButton(
                            count: log.reactionCount,
                            isReacted: reactedIds.contains(log.id)
                        ) {
                            Task { await toggle(log) }
                        }
                    }
                }
            }
        }
```

- [ ] **Step 2: Build to verify it compiles**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: build succeeds. The `switch` inside `List`'s `@ViewBuilder` is supported (it expands to a `_ConditionalContent`). If the build complains about exhaustiveness, that means a third `Mode` case was added — re-confirm `LogListView.Mode` still has only `.changelog` and `.backlog`.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Lab/Views/LogListView.swift
git commit -m "$(cat <<'EOF'
feat(ios): render lab see-all list with LogTimeline

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Delete the now-unused `LogRow`

**Files:**
- Delete: `apps/ios/Pebbles/Features/Lab/Components/LogRow.swift`

After Tasks 4 and 5, `LogRow` has no remaining call sites in the iOS app.

- [ ] **Step 1: Confirm no remaining references**

Run:

```bash
grep -rn "LogRow" apps/ios/Pebbles apps/ios/PebblesTests
```

Expected: zero matches. If anything matches, stop and remove the remaining reference before deleting the file. (`AnnouncementRow` is a different component — grepping for the full word `LogRow` will not match it.)

- [ ] **Step 2: Delete the file and regenerate the project**

```bash
git rm apps/ios/Pebbles/Features/Lab/Components/LogRow.swift
npm run generate --workspace=@pbbls/ios
```

Expected: `xcodegen` succeeds; the deleted file disappears from `Pebbles.xcodeproj`'s file list (the project file is git-ignored, no diff to stage).

- [ ] **Step 3: Build to verify nothing broke**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: build succeeds.

- [ ] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
chore(ios): remove unused LogRow component

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Final test pass + manual smoke

**Files:** none — verification only.

- [ ] **Step 1: Run the full iOS test suite**

```bash
npm test --workspace=@pbbls/ios
```

Expected: every test passes, including the two new ones added in Task 1.

- [ ] **Step 2: Lint**

```bash
npm run lint --workspace=@pbbls/ios
```

Expected: no new SwiftLint violations. If the suite predates SwiftLint or fails to launch, log the failure mode in the PR description rather than skipping silently.

- [ ] **Step 3: Manual smoke (simulator or device)**

Launch the app on an iPhone 17 simulator (or device). While signed in:

1. Open the **Lab** tab. The Changelog, In progress, and Backlog sections each render with their timeline icon (check / dotted / dashed) and a continuous vertical line.
2. Each changelog row shows a muted date above the title. Confirm it reads `released_at` when present, falling back to `published_at` otherwise (you can verify in the admin which date is set).
3. Tap **See all** under Changelog — full list shows the same timeline + dates and the same ordering.
4. Tap **See all** under Backlog — full list shows the timeline + reaction buttons. Tap one to confirm the optimistic update still works.
5. Change the device language between **English** and **French** in iOS Settings. Reopen the Lab tab: the date format updates ("May 14, 2026" ↔ "14 mai 2026"); log titles stay in their localized form.
6. Open `apps/ios/Pebbles/Resources/Localizable.xcstrings` in Xcode and confirm no entries are in the `New` or `Stale` state. The date label is a SwiftUI-localized `Date`, so no new catalog entries are expected.

- [ ] **Step 4: Open the PR**

Follow the PR workflow from `CLAUDE.md`:

```bash
git push -u origin feat/438-ios-changelog-release-date
gh pr create --title "feat(ios): sort lab changelog by release date" --body "$(cat <<'EOF'
Resolves #438

## Summary
- `Log` model gains an optional `releasedAt: Date?` decoded from `released_at`.
- `LogsService.changelog(limit:)` now orders by `released_at desc nulls last, published_at desc`, mirroring the web's `fetchChangelog`.
- New `LogTimeline` component renders the three feature-status sections (changelog, in-progress, backlog) with a vertical timeline (icon + connecting line), and shows the localized release date above each shipped title. Replaces `LogRow`.

## Key files
- `apps/ios/Pebbles/Features/Lab/Models/Log.swift`
- `apps/ios/Pebbles/Features/Lab/Services/LogsService.swift`
- `apps/ios/Pebbles/Features/Lab/Components/LogTimeline.swift` (new)
- `apps/ios/Pebbles/Features/Lab/LabView.swift`
- `apps/ios/Pebbles/Features/Lab/Views/LogListView.swift`

## Test plan
- [ ] `npm test --workspace=@pbbls/ios` — all green, including new `decodesReleasedAt` and `decodesMissingReleasedAt` tests.
- [ ] Open Lab tab — three sections render with timeline icons and connecting line.
- [ ] Changelog rows show release date (with `published_at` fallback) above title.
- [ ] EN/FR language toggle updates date format.
- [ ] Backlog "See all" reaction toggle still optimistic-updates and reverts on error.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Issue #438 carries `feat` + `ios` labels and milestone `M31 · Lab Improvements` — propose inheriting them on the PR per `CLAUDE.md` PR checklist. Confirm with the user before applying.

---

## Out of scope

- Schema or RPC changes (`released_at` already exposed by `v_logs_with_counts`).
- Web changelog (already shipped — `apps/web/lib/data/logs-api.ts:93`, `apps/web/components/lab/LogTimeline.tsx:38`).
- Announcements / `AnnouncementRow` / `AnnouncementDetailView`.
- Arkaik bundle (no screens, routes, models, or endpoints change — skip per CLAUDE.md task-size triage).
- Adding `Localizable.xcstrings` entries (date is SwiftUI-localized via `Locale.current`).
