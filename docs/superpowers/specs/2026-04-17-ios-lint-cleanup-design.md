# iOS SwiftLint cleanup

**Issue:** #272 · **Milestone:** M21 · Souls & collections · **Platform:** iOS (SwiftUI, iOS 17+)

## Context

`npm run lint` at the repo root currently fails with **26 error-severity violations and 9 warnings** across 12 files in `apps/ios/`. The violations accumulated across PRs #269 and #271 and never gated those merges because the iOS lint target is run via the workspace `lint` script, not in per-PR CI.

This spec covers a single quality pass to bring the iOS package back to **zero violations** (errors and warnings) so future regressions are visible on the first `npm run lint` of any branch. No behavior changes, no test semantics change, no SwiftLint config changes.

Issue #272 enumerates ~13 of the violations. The current lint surfaces four additional sources not named in the issue body (`PebbleDraftFromDetailTests`, `PebbleCreatePayloadEncodingTests`, `PebbleDetailDecodingTests:139,164`, `AppEnvironment:11,19`). This spec treats them as in scope — the acceptance is zero violations, not "strictly the issue list."

## Scope

**In scope**

- Fix every SwiftLint error and warning currently reported by `npm run lint --workspace=@pbbls/ios`.
- Six rule categories: `identifier_name`, `force_cast`, `force_try`, `non_optional_string_data_conversion`, `line_length`/`nesting`, `implicit_optional_initialization`.
- Preserve existing test semantics (same assertions, same decoded values, same date parsing, same JSON produced).
- Keep diffs minimal — only touch lines the violations point at plus any line changes required to propagate a `throws` signature.

**Out of scope**

- SwiftLint config changes. We fix the code, not the rules.
- Behavior changes in any touched file — no new logging, no new error handling, no new branches.
- New tests. The existing test surface already covers the affected code paths.
- Any file that isn't currently flagged by SwiftLint.

## Fix catalogue

### 1. `identifier_name` — 17 errors

Rule: SwiftLint requires identifiers to be 3+ characters (with exceptions). Offending locals were 1–2 chars.

| File:line | Current | Replacement |
|-----------|---------|-------------|
| `Pebbles/Features/Path/Models/PebbleCreatePayload.swift:34` | `let f = ISO8601DateFormatter()` | `let formatter = ISO8601DateFormatter()` (update 2 subsequent `f.` calls) |
| `Pebbles/Features/Profile/Views/CollectionDetailView.swift:28` | `let f = DateFormatter()` | `let formatter = DateFormatter()` (update 2 subsequent `f.` calls) |
| `PebblesTests/PebbleCreatePayloadEncodingTests.swift:13` | `var c = enc.singleValueContainer()` | `var container = enc.singleValueContainer()` |
| `PebblesTests/PebbleUpdatePayloadEncodingTests.swift:13` | same | same |
| `PebblesTests/GroupPebblesByMonthTests.swift:11` | `var c = Calendar(...)` | `var gregorian = Calendar(...)` (update `c.timeZone = ...; return c`) |
| `PebblesTests/GroupPebblesByMonthTests.swift:17` | `let f = ISO8601DateFormatter()` | `let formatter = ISO8601DateFormatter()` |
| `PebblesTests/GroupPebblesByMonthTests.swift:31-32` | `let c = try dec.singleValueContainer(); let s = try c.decode(String.self)` | `let container = try dec.singleValueContainer(); let iso = try container.decode(String.self)` |
| `PebblesTests/GroupPebblesByMonthTests.swift:46-47` | `let a = pebble(...); let b = pebble(...)` | `let early = try pebble(...); let late = try pebble(...)` (also handles rule 3) |
| `PebblesTests/GroupPebblesByMonthTests.swift:64` | `for (i, expected) in ...` | `for (index, expected) in ...` |
| `PebblesTests/PebbleDraftFromDetailTests.swift:26-28` | `domains.map { d in ... }`, `souls.map { s in ... }`, `collections.map { c in ... }` | `domains.map { domain in ... }`, `souls.map { soul in ... }`, `collections.map { coll in ... }` |
| `PebblesTests/PebbleDraftFromDetailTests.swift:49-51` | `let c = try dec.singleValueContainer(); let s = try c.decode(String.self); guard let d = formatter.date(from: s) else { ... }; return d` | `let container = try dec.singleValueContainer(); let iso = try container.decode(String.self); guard let date = formatter.date(from: iso) else { ... }; return date` |

**Closure parameter names** (`dec`, `enc`) remain — they're 3 chars, which satisfies the rule. No change.

### 2. `force_cast` — 6 errors

Rule: `as!` is unsafe; use typed conditional cast with `#require` to fail the test cleanly.

The four encoding-tests helpers all cast `JSONSerialization.jsonObject(...)` result to `[String: Any]`. Swift Testing's `#require(_:)` macro throws a `RequirementError` (test fails with a clear message) when the expression is nil — the exact semantics we want.

Pattern:

```swift
// Before
private func encode(_ payload: P) throws -> [String: Any] {
    let data = try JSONEncoder().encode(payload)
    return try JSONSerialization.jsonObject(with: data) as! [String: Any]
}

// After
private func encode(_ payload: P) throws -> [String: Any] {
    let data = try JSONEncoder().encode(payload)
    let object = try JSONSerialization.jsonObject(with: data)
    return try #require(object as? [String: Any])
}
```

Also applies to the inline site in each file's `nilModeEncodesAsNull` test (`json` assigned from the same cast at test-body scope). Apply the same `#require(...)` replacement.

Files: `PebblesTests/CollectionInsertPayloadEncodingTests.swift:12,38`, `PebblesTests/CollectionUpdatePayloadEncodingTests.swift:10,27`, `PebblesTests/PebbleCreatePayloadEncodingTests.swift:17`, `PebblesTests/PebbleUpdatePayloadEncodingTests.swift:17`.

### 3. `force_try` — 3 errors

Rule: `try!` crashes on throw. Convert the helper to `throws`, propagate via `try` in callers.

**`PebblesTests/GroupPebblesByMonthTests.swift:35`** — `pebble(_:)` helper currently ends with `return try! decoder.decode(Pebble.self, from: json)`. Change:

```swift
private func pebble(_ happened: String) throws -> Pebble {
    ...
    return try decoder.decode(Pebble.self, from: json)
}
```

This propagates to four test methods (`sameMonth`, `descendingOrder`, `preservesInputOrder`, `monthBoundary`) which now call `try pebble(...)`. Each test signature gains `throws`. `emptyInput` does not call `pebble(_:)` and stays non-throwing.

**`PebblesTests/PebbleDraftFromDetailTests.swift:44,56`** — `makeDetail(...)` helper contains two `try!` calls (`JSONSerialization.data`, `decoder.decode`). Change both to `try` and add `throws` to the helper signature. All 6 test methods call `makeDetail(...)` — each gains `throws` and `try`.

### 4. `non_optional_string_data_conversion` — 4 warnings

Rule: the `String.data(using:)` conversion returns `Data?`; SwiftLint prefers `Data(_:)` which returns non-optional when constructed from `Substring.UTF8View` or `String.UTF8View`.

```swift
// Before
let json = """ ... """.data(using: .utf8)!

// After
let json = Data(""" ... """.utf8)
```

Files: `PebblesTests/ComposePebbleResponseDecodingTests.swift:10,28`, `PebblesTests/PebbleDetailDecodingTests.swift:139,164`.

### 5. `line_length` + `nesting` — 4 warnings

**`Pebbles/Features/Path/CreatePebbleSheet.swift:159`** — one line triggers both rules:

```swift
struct Partial: Decodable { let pebbleId: UUID; enum CodingKeys: String, CodingKey { case pebbleId = "pebble_id" } }
```

Hoist to file scope with a unique name (the file already has a file-private `ComposePebbleRequest` below — follow that pattern):

```swift
private struct PebbleIdPartial: Decodable {
    let pebbleId: UUID
    enum CodingKeys: String, CodingKey {
        case pebbleId = "pebble_id"
    }
}
```

Place it alongside `ComposePebbleRequest` at the bottom of the file (outside `CreatePebbleSheet`). Update the call site at line 160 to `PebbleIdPartial.self`.

**`Pebbles/Services/AppEnvironment.swift:11,19`** — `fatalError` messages exceed 120 chars. Break each across lines:

```swift
fatalError(
    "SupabaseURL missing or invalid in Info.plist. " +
    "Did you copy Config/Secrets.example.xcconfig → Config/Secrets.xcconfig?"
)
```

Same transform on line 19 for `SupabaseAnonKey`.

### 6. `implicit_optional_initialization` — 1 warning

Rule: explicit `= nil` on an Optional property is redundant; Swift initializes Optionals to `nil` by default.

**`Pebbles/Features/Profile/Sheets/CreateCollectionSheet.swift:13`** — drop the `= nil`:

```swift
// Before
@State private var mode: CollectionMode? = nil

// After
@State private var mode: CollectionMode?
```

SwiftUI's `@State` wrapper behaves identically for both forms at iOS 17.

## Implementation strategy

Six commits, one per rule category, in this order:

1. `quality(ios): replace force_cast in encoding tests with try #require` — rule 2 (isolated, no signature changes)
2. `quality(ios): convert try! helpers to throws in tests` — rule 3 (adds `throws` to test signatures)
3. `quality(ios): rename short locals flagged by identifier_name` — rule 1 (depends on rule 3 being done so we're only renaming on final signatures; avoids a double-touch on `PebbleDraftFromDetailTests`)
4. `quality(ios): prefer Data(_:) over String.data(using:)` — rule 4
5. `quality(ios): hoist PebbleIdPartial and wrap long AppEnvironment messages` — rule 5
6. `quality(ios): drop redundant = nil on CreateCollectionSheet mode state` — rule 6

After each commit: `npm run lint --workspace=@pbbls/ios` — verify the count drops and no new violations surface. After the last commit: exit 0, no output.

## Testing

- `xcodebuild build -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.0'` — passes after every commit.
- `xcodebuild test -project apps/ios/Pebbles.xcodeproj -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.0'` — passes after every commit. All 50+ Swift Testing cases stay green with the same assertions.
- `npm run lint` from repo root — exits 0 after commit 6.

No new tests. The rename and signature changes keep the same test surface; the `Data(_:)` and `#require` swaps preserve the decoded output byte-for-byte.

## Acceptance

- [ ] `npm run lint` exits 0 from the repo root with no violations.
- [ ] `xcodebuild build` passes on iPhone 17 / iOS 26.
- [ ] `xcodebuild test` passes on iPhone 17 / iOS 26 with the same test count and all green.
- [ ] No SwiftLint config changes in `apps/ios/.swiftlint.yml` (or wherever the config lives).
- [ ] No behavior changes in touched production files (diff-review shows only rename/hoist/formatting).
- [ ] No new files introduced except the hoisted `PebbleIdPartial` struct which lives in the same file as `CreatePebbleSheet`.
