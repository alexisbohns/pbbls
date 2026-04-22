# iOS Lab — Changelog / Backlog list decode hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the iOS Lab tab's Log-list fetches tolerant of a single bad row so "See all" under Changelog / Backlog stops failing with "Couldn't load the list.", and make the next decode failure self-diagnosing via `os.Logger`.

**Architecture:** Introduce a module-internal `LossyLogArray` Decodable wrapper that iterates an unkeyed container, decodes each element as `Log`, and on per-element failure logs a `DecodingError` with its coding path then skips the row. `LogsService` decodes feed responses through the wrapper; method signatures stay `-> [Log]` so call sites in `LabView` / `LogListView` are unchanged.

**Tech Stack:** Swift 5, iOS 17, SwiftUI, supabase-swift 2.43, Swift Testing (`@Suite`, `@Test`, `#expect`), xcodegen.

**Spec:** [`docs/superpowers/specs/2026-04-22-ios-lab-changelog-list-decode-hardening-design.md`](../specs/2026-04-22-ios-lab-changelog-list-decode-hardening-design.md)

**Issue:** [#294](https://github.com/pbbls/pebbles/issues/294) — `[Bug] Can't see the changelog list`. Milestone **M24 · Introduce the Lab**. Labels will mirror the issue's scope (`ios`) plus species `fix`.

**Branch:** `fix/294-changelog-list-decode-hardening` (already created; the design spec commit is the first commit on this branch).

---

## File Structure

One new production file, one new test file, one modified production file. No other layers of the stack are touched — no migrations, no web, no DB types regeneration.

- **Create** `apps/ios/Pebbles/Features/Lab/Services/LossyLogArray.swift`
  *Module-internal* `Decodable` array wrapper. Holds an extracted helper because `@testable import Pebbles` reaches `internal` but not `private` symbols, so co-locating it inside `LogsService.swift` as `private` would defeat tests. File also hosts a `fileprivate` `AnyDecodable` used to advance past bad elements in the unkeyed container.
- **Modify** `apps/ios/Pebbles/Features/Lab/Services/LogsService.swift`
  Rewire `announcements(limit:)`, `changelog(limit:)`, `initiatives()`, `backlog(limit:)` to decode responses as `LossyLogArray` and return `.logs`. Zero changes to `myReactions()`, `react(logId:)`, `unreact(logId:)`, `ReactionRow`, or `LogsServiceError`.
- **Create** `apps/ios/PebblesTests/Features/Lab/LossyLogArrayTests.swift`
  Swift Testing suite covering happy path, single bad row skip, multiple bad rows skip, entirely empty array, and non-array top-level JSON. The existing `PebblesTests/` target auto-picks up `.swift` files under its folder tree per `project.yml` — no `project.yml` edit required.

### Why extract `LossyLogArray` out of `LogsService.swift`?

The spec described `LossyLogArray` as `private` inside `LogsService.swift`. During planning we realized keeping it `private` blocks `@testable import` from reaching it, which would force every test to go through a fake `SupabaseClient` — large machinery for a small behavior. Moving it to its own file at `internal` access keeps the abstraction local to the `Lab` feature folder, keeps it out of any public API, and lets Swift Testing exercise the wrapper directly on Data literals. All other aspects of the spec's design are preserved verbatim.

---

## Task 1: Scaffold the test suite with a failing happy-path test

Establishes the test file + import path and proves the wrapper type is reachable from tests. Drives the shape of `LossyLogArray` before we write it.

**Files:**
- Create: `apps/ios/PebblesTests/Features/Lab/LossyLogArrayTests.swift`

- [ ] **Step 1: Verify the branch and starting state**

Run from `/Users/alexis/code/pbbls`:

```bash
git status && git branch --show-current
```

Expected output (on the branch that already holds the spec commit):

```
On branch fix/294-changelog-list-decode-hardening
Your branch is up to date with 'origin/fix/294-...' (or: ahead of 'origin/main' by 1)
nothing to commit, working tree clean
fix/294-changelog-list-decode-hardening
```

If `git branch --show-current` does not print `fix/294-changelog-list-decode-hardening`, stop and recover — do not create a second branch. See the spec's "Branch" section.

- [ ] **Step 2: Create the test folder if missing**

```bash
mkdir -p apps/ios/PebblesTests/Features/Lab
```

- [ ] **Step 3: Write the failing happy-path test**

Create `apps/ios/PebblesTests/Features/Lab/LossyLogArrayTests.swift` with the following exact contents. The file intentionally references `LossyLogArray` which does not exist yet — this test must **fail to compile** until Task 2.

```swift
import Foundation
import Testing
@testable import Pebbles

/// Tests the lossy Decodable wrapper that `LogsService` uses so one bad row
/// in a `v_logs_with_counts` response can never break an entire feed.
@Suite("LossyLogArray")
struct LossyLogArrayTests {

    // MARK: - Fixtures

    /// A PostgREST decoder configured the same way supabase-swift configures
    /// its internal one — custom date strategy that accepts ISO8601 with or
    /// without fractional seconds. Keeps these tests decoupled from the SDK
    /// while matching what production decodes against.
    private func makeDecoder() -> JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let string = try container.decode(String.self)
            let withFractional: Set<Character> = [".", ","]
            let formatterWith = ISO8601DateFormatter()
            formatterWith.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            let formatterWithout = ISO8601DateFormatter()
            formatterWithout.formatOptions = [.withInternetDateTime]
            if string.contains(where: { withFractional.contains($0) }),
               let date = formatterWith.date(from: string) {
                return date
            }
            if let date = formatterWithout.date(from: string) { return date }
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Invalid ISO8601: \(string)"
            )
        }
        return decoder
    }

    /// A well-formed `v_logs_with_counts` row. All required Log fields present.
    private func validRow(
        id: String = "11111111-1111-1111-1111-111111111111",
        titleEn: String = "Shipped it"
    ) -> String {
        """
        {
          "id": "\(id)",
          "species": "feature",
          "platform": "ios",
          "status": "shipped",
          "title_en": "\(titleEn)",
          "title_fr": null,
          "summary_en": "One line.",
          "summary_fr": null,
          "body_md_en": null,
          "body_md_fr": null,
          "cover_image_path": null,
          "external_url": null,
          "published": true,
          "published_at": "2026-04-20T12:00:00Z",
          "created_at": "2026-04-20T12:00:00Z",
          "reaction_count": 0
        }
        """
    }

    // MARK: - Tests

    @Test("decodes an all-valid array unchanged")
    func decodesAllValid() throws {
        let json = Data("[\(validRow(id: "11111111-1111-1111-1111-111111111111", titleEn: "A")),\(validRow(id: "22222222-2222-2222-2222-222222222222", titleEn: "B"))]".utf8)

        let wrapper = try makeDecoder().decode(LossyLogArray.self, from: json)

        #expect(wrapper.logs.count == 2)
        #expect(wrapper.logs.map(\.titleEn) == ["A", "B"])
    }
}
```

- [ ] **Step 4: Run the new test and confirm the build fails**

```bash
npm run test --workspace=@pbbls/ios 2>&1 | tail -40
```

Expected: the build fails with a Swift compiler error mentioning `LossyLogArray` is unknown (e.g. `cannot find 'LossyLogArray' in scope`). This is the desired red state — the test references a symbol we have not written yet.

If the build succeeds, the test will have been silently compiled against something else — stop and investigate before continuing.

- [ ] **Step 5: Do not commit**

We commit once the test is green in Task 2. Leaving it red in a standalone commit would break `main` for anyone cherry-picking.

---

## Task 2: Implement `LossyLogArray` to make the happy-path test green

**Files:**
- Create: `apps/ios/Pebbles/Features/Lab/Services/LossyLogArray.swift`
- Test: `apps/ios/PebblesTests/Features/Lab/LossyLogArrayTests.swift` (from Task 1)

- [ ] **Step 1: Write `LossyLogArray.swift` with the minimal implementation**

Create `apps/ios/Pebbles/Features/Lab/Services/LossyLogArray.swift`:

```swift
import Foundation
import os

/// A lossy Decodable wrapper around `[Log]`.
///
/// `LogsService` decodes every `v_logs_with_counts` response through this
/// wrapper so that one un-decodable row cannot break an entire feed. Each
/// skipped row writes one `error`-level log line naming its index and the
/// underlying `DecodingError` coding path, so the next occurrence is
/// self-diagnosing from Xcode Console or an `os_log` export.
///
/// The happy path — all rows decode — produces no new log output and yields
/// the full list exactly as a plain `[Log]` decode would.
struct LossyLogArray: Decodable {
    let logs: [Log]

    init(from decoder: Decoder) throws {
        var container = try decoder.unkeyedContainer()
        var accumulated: [Log] = []
        if let count = container.count { accumulated.reserveCapacity(count) }
        var index = 0
        while !container.isAtEnd {
            do {
                accumulated.append(try container.decode(Log.self))
            } catch {
                Self.logger.error(
                    "skipped log row \(index, privacy: .public): \(String(reflecting: error), privacy: .public)"
                )
                // UnkeyedDecodingContainer does not advance `currentIndex`
                // when decode(_:) throws — without this swallow, the while
                // loop would spin forever on the first bad row.
                _ = try? container.decode(AnyDecodable.self)
            }
            index += 1
        }
        self.logs = accumulated
    }

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "logs-service")
}

/// Consumes any JSON value (object, array, primitive, null) without
/// inspecting it, so the parent UnkeyedDecodingContainer advances past
/// the bad element.
private struct AnyDecodable: Decodable {
    init(from decoder: Decoder) throws {
        _ = try decoder.singleValueContainer()
    }
}
```

- [ ] **Step 2: Run the test and confirm it now passes**

```bash
npm run test --workspace=@pbbls/ios 2>&1 | tail -40
```

Expected: the `LossyLogArrayTests/decodesAllValid` test passes. All other existing tests in `PebblesTests` still pass.

If there are compile errors, re-check the import list in `LossyLogArray.swift` (should be `Foundation` + `os`) and ensure the file path matches the `apps/ios/Pebbles/Features/Lab/Services/` location — `project.yml` globs that tree automatically, so placing it elsewhere will silently exclude it from the build.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Lab/Services/LossyLogArray.swift apps/ios/PebblesTests/Features/Lab/LossyLogArrayTests.swift
git commit -m "$(cat <<'EOF'
fix(ios): add LossyLogArray wrapper for resilient feed decoding (#294)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Lock in resilience and diagnostics with the remaining tests

Four more tests verify the wrapper actually skips bad rows, advances the container, and preserves end-of-stream behavior. These are the tests that encode the *contract* of the fix (not just "does it compile and happy-path decode").

**Files:**
- Modify: `apps/ios/PebblesTests/Features/Lab/LossyLogArrayTests.swift`

- [ ] **Step 1: Add the four additional tests**

Edit `apps/ios/PebblesTests/Features/Lab/LossyLogArrayTests.swift` and append inside the `@Suite("LossyLogArray") struct LossyLogArrayTests { ... }` body, after `decodesAllValid()`:

```swift
    @Test("empty array decodes to empty logs")
    func decodesEmptyArray() throws {
        let json = Data("[]".utf8)
        let wrapper = try makeDecoder().decode(LossyLogArray.self, from: json)
        #expect(wrapper.logs.isEmpty)
    }

    @Test("single bad row is skipped; surrounding rows still decode")
    func skipsSingleBadRow() throws {
        // Middle row has `status: "retired"` which is not in LogStatus's
        // allowed raw values, so it fails Log's decode. Wrapper must skip
        // it and decode the siblings.
        let bad = """
        {
          "id": "33333333-3333-3333-3333-333333333333",
          "species": "feature",
          "platform": "ios",
          "status": "retired",
          "title_en": "Bad",
          "title_fr": null,
          "summary_en": "…",
          "summary_fr": null,
          "body_md_en": null,
          "body_md_fr": null,
          "cover_image_path": null,
          "external_url": null,
          "published": true,
          "published_at": "2026-04-20T12:00:00Z",
          "created_at": "2026-04-20T12:00:00Z",
          "reaction_count": 0
        }
        """

        let json = Data("""
        [\(validRow(id: "11111111-1111-1111-1111-111111111111", titleEn: "A")),
         \(bad),
         \(validRow(id: "22222222-2222-2222-2222-222222222222", titleEn: "B"))]
        """.utf8)

        let wrapper = try makeDecoder().decode(LossyLogArray.self, from: json)

        #expect(wrapper.logs.count == 2)
        #expect(wrapper.logs.map(\.titleEn) == ["A", "B"])
    }

    @Test("multiple bad rows in a row are all skipped")
    func skipsConsecutiveBadRows() throws {
        // Two adjacent bad rows exercise the AnyDecodable swallow — if the
        // container didn't advance between bad elements, the second bad
        // row would be re-read and the loop would spin.
        let bad1 = """
        { "id": "not-a-uuid", "species": "feature", "platform": "ios", "status": "shipped", "title_en": "x", "summary_en": "x", "published": true, "created_at": "2026-04-20T12:00:00Z", "reaction_count": 0 }
        """
        let bad2 = """
        { "id": "55555555-5555-5555-5555-555555555555", "species": "mystery", "platform": "ios", "status": "shipped", "title_en": "x", "summary_en": "x", "published": true, "created_at": "2026-04-20T12:00:00Z", "reaction_count": 0 }
        """

        let json = Data("""
        [\(bad1),
         \(bad2),
         \(validRow(id: "66666666-6666-6666-6666-666666666666", titleEn: "last"))]
        """.utf8)

        let wrapper = try makeDecoder().decode(LossyLogArray.self, from: json)

        #expect(wrapper.logs.count == 1)
        #expect(wrapper.logs.first?.titleEn == "last")
    }

    @Test("all rows bad yields empty logs, not a thrown error")
    func allBadRowsYieldEmpty() throws {
        let bad = """
        { "id": "not-a-uuid", "species": "feature", "platform": "ios", "status": "shipped", "title_en": "x", "summary_en": "x", "published": true, "created_at": "2026-04-20T12:00:00Z", "reaction_count": 0 }
        """
        let json = Data("[\(bad),\(bad)]".utf8)

        let wrapper = try makeDecoder().decode(LossyLogArray.self, from: json)

        #expect(wrapper.logs.isEmpty)
    }

    @Test("non-array top-level JSON still throws")
    func nonArrayThrows() {
        // A bare object at the top is malformed for this endpoint. The
        // wrapper intentionally does NOT swallow this — whole-response
        // failures should still propagate so LogListView can show its
        // "Couldn't load the list." error.
        let json = Data("""
        { "error": "oops" }
        """.utf8)

        #expect(throws: DecodingError.self) {
            try self.makeDecoder().decode(LossyLogArray.self, from: json)
        }
    }
```

- [ ] **Step 2: Run all tests**

```bash
npm run test --workspace=@pbbls/ios 2>&1 | tail -40
```

Expected: all five `LossyLogArray` tests pass plus every pre-existing test in `PebblesTests`. Watch for the existing tests in `PebbleDetailDecodingTests`, `LocalizationTests`, etc. — a regression there means your change to `LossyLogArray.swift` accidentally affects module-wide decode behavior (it should not, since the type is a plain `struct`).

- [ ] **Step 3: Commit**

```bash
git add apps/ios/PebblesTests/Features/Lab/LossyLogArrayTests.swift
git commit -m "$(cat <<'EOF'
test(ios): cover LossyLogArray skip / advance / throw paths (#294)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Wire `LossyLogArray` into the four `LogsService` feed methods

Switches production callers to the resilient wrapper. `myReactions`, `react`, `unreact` stay as-is (they return `[ReactionRow]` / `Void`).

**Files:**
- Modify: `apps/ios/Pebbles/Features/Lab/Services/LogsService.swift:17-72`

- [ ] **Step 1: Rewrite `announcements(limit:)`**

In `apps/ios/Pebbles/Features/Lab/Services/LogsService.swift`, replace the `announcements(limit:)` body. Before:

```swift
    /// Published announcements, most recent first.
    func announcements(limit: Int? = nil) async throws -> [Log] {
        let base = supabase.client
            .from("v_logs_with_counts")
            .select()
            .eq("species", value: LogSpecies.announcement.rawValue)
            .eq("published", value: true)
            .order("published_at", ascending: false)
        if let limit {
            return try await base.limit(limit).execute().value
        }
        return try await base.execute().value
    }
```

After:

```swift
    /// Published announcements, most recent first.
    func announcements(limit: Int? = nil) async throws -> [Log] {
        let base = supabase.client
            .from("v_logs_with_counts")
            .select()
            .eq("species", value: LogSpecies.announcement.rawValue)
            .eq("published", value: true)
            .order("published_at", ascending: false)
        if let limit {
            let wrapper: LossyLogArray = try await base.limit(limit).execute().value
            return wrapper.logs
        }
        let wrapper: LossyLogArray = try await base.execute().value
        return wrapper.logs
    }
```

- [ ] **Step 2: Rewrite `changelog(limit:)`**

Replace the `changelog(limit:)` body. Before:

```swift
    /// Shipped features, most recent first.
    func changelog(limit: Int? = nil) async throws -> [Log] {
        let base = supabase.client
            .from("v_logs_with_counts")
            .select()
            .eq("species", value: LogSpecies.feature.rawValue)
            .eq("status", value: LogStatus.shipped.rawValue)
            .eq("published", value: true)
            .order("published_at", ascending: false)
        if let limit {
            return try await base.limit(limit).execute().value
        }
        return try await base.execute().value
    }
```

After:

```swift
    /// Shipped features, most recent first.
    func changelog(limit: Int? = nil) async throws -> [Log] {
        let base = supabase.client
            .from("v_logs_with_counts")
            .select()
            .eq("species", value: LogSpecies.feature.rawValue)
            .eq("status", value: LogStatus.shipped.rawValue)
            .eq("published", value: true)
            .order("published_at", ascending: false)
        if let limit {
            let wrapper: LossyLogArray = try await base.limit(limit).execute().value
            return wrapper.logs
        }
        let wrapper: LossyLogArray = try await base.execute().value
        return wrapper.logs
    }
```

- [ ] **Step 3: Rewrite `initiatives()`**

Replace. Before:

```swift
    /// Features currently in progress.
    func initiatives() async throws -> [Log] {
        try await supabase.client
            .from("v_logs_with_counts")
            .select()
            .eq("species", value: LogSpecies.feature.rawValue)
            .eq("status", value: LogStatus.inProgress.rawValue)
            .eq("published", value: true)
            .order("published_at", ascending: false)
            .execute()
            .value
    }
```

After:

```swift
    /// Features currently in progress.
    func initiatives() async throws -> [Log] {
        let wrapper: LossyLogArray = try await supabase.client
            .from("v_logs_with_counts")
            .select()
            .eq("species", value: LogSpecies.feature.rawValue)
            .eq("status", value: LogStatus.inProgress.rawValue)
            .eq("published", value: true)
            .order("published_at", ascending: false)
            .execute()
            .value
        return wrapper.logs
    }
```

- [ ] **Step 4: Rewrite `backlog(limit:)`**

Replace. Before:

```swift
    /// Backlog features, most upvoted first. Ties broken by recency.
    func backlog(limit: Int? = nil) async throws -> [Log] {
        let base = supabase.client
            .from("v_logs_with_counts")
            .select()
            .eq("species", value: LogSpecies.feature.rawValue)
            .eq("status", value: LogStatus.backlog.rawValue)
            .eq("published", value: true)
            .order("reaction_count", ascending: false)
            .order("created_at", ascending: false)
        if let limit {
            return try await base.limit(limit).execute().value
        }
        return try await base.execute().value
    }
```

After:

```swift
    /// Backlog features, most upvoted first. Ties broken by recency.
    func backlog(limit: Int? = nil) async throws -> [Log] {
        let base = supabase.client
            .from("v_logs_with_counts")
            .select()
            .eq("species", value: LogSpecies.feature.rawValue)
            .eq("status", value: LogStatus.backlog.rawValue)
            .eq("published", value: true)
            .order("reaction_count", ascending: false)
            .order("created_at", ascending: false)
        if let limit {
            let wrapper: LossyLogArray = try await base.limit(limit).execute().value
            return wrapper.logs
        }
        let wrapper: LossyLogArray = try await base.execute().value
        return wrapper.logs
    }
```

- [ ] **Step 5: Sanity-check nothing else in the file moved**

Run:

```bash
git diff apps/ios/Pebbles/Features/Lab/Services/LogsService.swift | head -120
```

Expected: only the four feed methods' bodies changed. `myReactions()`, `react(logId:)`, `unreact(logId:)`, `ReactionRow`, `ReactionInsert`, and `LogsServiceError` should be untouched. The `@MainActor struct LogsService` declaration, its `supabase` property, and the `static let logger` should also be untouched.

- [ ] **Step 6: Run the build and full test suite**

```bash
npm run build --workspace=@pbbls/ios 2>&1 | tail -20
npm run test --workspace=@pbbls/ios 2>&1 | tail -20
```

Expected: build succeeds, all tests pass (both the new `LossyLogArray` suite and all pre-existing tests).

- [ ] **Step 7: Run SwiftLint**

```bash
npm run lint --workspace=@pbbls/ios 2>&1 | tail -20
```

Expected: no lint errors. If `swiftlint` reports style issues on the new file, address them before committing (usually trailing whitespace or trailing newline).

- [ ] **Step 8: Commit**

```bash
git add apps/ios/Pebbles/Features/Lab/Services/LogsService.swift
git commit -m "$(cat <<'EOF'
fix(ios): decode LogsService feeds through LossyLogArray (#294)

Routes announcements(), changelog(), initiatives(), backlog() through the
resilient wrapper so a single bad v_logs_with_counts row can no longer
break the Lab "See all" lists. Method signatures unchanged.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Manual verification against a live Supabase and open the PR

Automated tests can't prove the original production symptom goes away — that requires the real data that triggers the bug. This task runs the app in the simulator, reproduces the original steps from the issue, and captures any `skipped log row N: …` log lines for the follow-up.

**Files:** none modified in this task.

- [ ] **Step 1: Run the app in the simulator**

```bash
cd apps/ios && npm run generate
```

Then open `apps/ios/Pebbles.xcodeproj` in Xcode and run on **iPhone 17** (matching the test destination) or any iOS 17+ simulator. Sign in with an existing account that can see the Lab tab.

- [ ] **Step 2: Reproduce the original steps from issue #294**

1. Tap the **Lab** tab.
2. Scroll to the **Changelog** section.
3. Tap **See all**.

Expected before fix: error view with "Couldn't load the list."
Expected after fix: the changelog list renders rows. If the DB contains row(s) that fail to decode, fewer rows appear than the total number of shipped features — and Xcode Console shows at least one line matching:

```
skipped log row N: DecodingError... codingPath: [ ..., CodingKeys(stringValue: "<field>", ...) ] ...
```

- [ ] **Step 3: Capture the diagnostic log lines (if any)**

In Xcode Console, filter by subsystem `app.pbbls.ios` and category `logs-service`. Copy any `skipped log row` lines to a scratch note — they'll go into the follow-up issue.

If zero `skipped log row` lines appear but the list now loads, the bug was likely a transient upstream issue resolved by the defensive layer — still a valid fix.

- [ ] **Step 4: Regression-check the backlog path**

1. Tap the **Lab** tab.
2. Scroll to the **Backlog** section.
3. Tap **See all**.
4. Toggle a reaction on one row.

Expected: list loads, reaction toggles optimistically, unit `logs-service` shows no unexpected error lines (`skipped log row` is acceptable; anything else is a bug).

- [ ] **Step 5: Regression-check the Lab home**

Return to the Lab tab root. All four sections (Announcements, Changelog top-5, In progress, Backlog top-5) must render as they did before the fix. No visual difference is expected on the happy path.

- [ ] **Step 6: Push the branch**

```bash
git push -u origin fix/294-changelog-list-decode-hardening
```

- [ ] **Step 7: Open the PR inheriting issue #294's labels and milestone**

Issue #294 is labeled `bug` with milestone `M24 · Introduce the Lab`. Per project convention (`CLAUDE.md` > PR Workflow > step 4), a PR resolving a `bug` issue is labeled `fix`, plus any scope labels from the issue. Scope: `core` is not quite right; this is iOS-only. Look at how prior iOS PRs on this branch family (#293, #289, #296) were labeled — they used `feat` and iOS-related scopes. Use `fix` + whatever scope label matches iOS work in the project's label set (check `gh label list` if unsure).

Before opening the PR, **ask the user to confirm the proposed labels and milestone**:

> PR will resolve issue #294. Proposed labels: `fix` + the iOS scope label(s) you used on #293/#296. Proposed milestone: `M24 · Introduce the Lab` (inherited from the issue). Confirm or override?

Then open the PR:

```bash
gh pr create --title "fix(ios): harden Lab log feed decoding (#294)" --body "$(cat <<'EOF'
Resolves #294.

## Summary
- Adds a module-internal `LossyLogArray` Decodable wrapper that iterates `v_logs_with_counts` responses, decodes each element as `Log`, and skips any row that fails to decode with an `os.Logger` line naming the row index and the full `DecodingError` coding path.
- Routes `LogsService.announcements(limit:)`, `changelog(limit:)`, `initiatives()`, and `backlog(limit:)` through the wrapper. Method signatures stay `-> [Log]`, so `LabView` and `LogListView` are untouched.

## Root cause
The full `changelog()` fetch in `LogListView` was failing with "The data couldn't be read because it isn't in the correct format." because a single row in `v_logs_with_counts` was failing the `Log` decode and, with Swift's default strict decoding, taking the whole list down with it. The top-5 fetch in `LabView` happened to succeed because the bad row(s) sat beyond position 5 in `published_at desc` order.

## Key files changed
- `apps/ios/Pebbles/Features/Lab/Services/LossyLogArray.swift` (new)
- `apps/ios/Pebbles/Features/Lab/Services/LogsService.swift` (rewired)
- `apps/ios/PebblesTests/Features/Lab/LossyLogArrayTests.swift` (new — 5 tests)
- `docs/superpowers/specs/2026-04-22-ios-lab-changelog-list-decode-hardening-design.md` (spec)
- `docs/superpowers/plans/2026-04-22-ios-lab-changelog-list-decode-hardening.md` (plan)

## Test plan
- [ ] `npm run test --workspace=@pbbls/ios` — all tests pass (new suite + pre-existing)
- [ ] `npm run lint --workspace=@pbbls/ios` — clean
- [ ] `npm run build --workspace=@pbbls/ios` — clean
- [ ] Simulator: Lab tab > Changelog > See all — list loads (previously failed)
- [ ] Simulator: Lab tab > Backlog > See all — list loads and reaction toggle still works
- [ ] Simulator: Lab home — four sections render identically to pre-fix

## Follow-up
If any `skipped log row N: ...` lines appear in Xcode Console during manual verification, open a targeted issue to fix the underlying data or model defect. That is intentionally out of scope for this bug (the goal here is resilience + diagnosability).

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

After `gh pr create` returns, add the labels and milestone (replace `<scope-label>` with whatever the user confirmed in the prompt above):

```bash
gh pr edit --add-label "fix" --add-label "<scope-label>" --milestone "M24 · Introduce the Lab"
```

Print the PR URL so the user can verify.

- [ ] **Step 8: Return to the user**

Report: PR URL, tests passing, lint passing, any `skipped log row` findings from manual verification (so we can spin up the follow-up issue).

---

## Self-Review

**Spec coverage:**
- Goal 1 (resilience, one bad row doesn't break the list): Task 2 introduces `LossyLogArray`; Task 3 test `skipsSingleBadRow` and `skipsConsecutiveBadRows` encode the contract; Task 4 wires it into all four feed methods. ✅
- Goal 2 (diagnosability via `os.Logger` with coding path): Task 2 embeds the logger call using `String(reflecting: error)` and `privacy: .public`, matching the spec verbatim. ✅
- Goal 3 (centralized across all four feeds): Task 4 rewires each of `announcements`, `changelog`, `initiatives`, `backlog` — no call site gets left behind. `myReactions` (returns `[ReactionRow]`) is explicitly excluded as per spec non-goals. ✅
- Goal 4 (zero UI impact on happy path): Method signatures are preserved, no changes to `LabView` / `LogListView`, Task 3 test `decodesAllValid` proves the wrapper yields the identical list on the happy path. ✅
- Non-goal (don't fix the underlying data defect): Task 5 step 3 captures the diagnostic, Task 5 PR body surfaces it as a follow-up — no data / migration changes anywhere. ✅
- Non-goal (don't touch DB / web): No tasks in this plan modify those layers. ✅

**Placeholder scan:**
- No "TODO", "TBD", "fill in", or "similar to Task N" references.
- Every code step shows the exact code.
- Every command step shows the exact command and the expected output.
- The one parameterized element — the scope label in the `gh pr edit` call — is gated by an explicit user-confirmation prompt in the same step, which is the project's mandated workflow per `CLAUDE.md` PR checklist item 4.

**Type / name consistency:**
- `LossyLogArray` referenced by that exact name in Task 1 test, Task 2 implementation, Task 3 tests, Task 4 service rewires.
- `AnyDecodable` is `fileprivate` inside `LossyLogArray.swift` — never referenced by any test or call site (correct — it's an internal helper).
- `Log`, `LogSpecies`, `LogStatus`, `LogsService`, `v_logs_with_counts`, `LabView`, `LogListView` names match what's actually in the codebase as of the branch base (verified during spec writing).
- `logs-service` is the `os.Logger` category used both by the existing `LogsService` (for reaction errors) and the new `LossyLogArray` (for skipped rows) — deliberate, so both streams filter together in Console.

No gaps found; no inline fixes needed.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-22-ios-lab-changelog-list-decode-hardening.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
