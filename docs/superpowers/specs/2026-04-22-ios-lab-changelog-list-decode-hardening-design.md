# iOS Lab — Changelog / Backlog list decode hardening

**Issue:** [#294](https://github.com/pbbls/pebbles/issues/294) — `[Bug] Can't see the changelog list`
**Milestone:** M24 · Introduce the Lab
**Date:** 2026-04-22

## Context

The iOS Lab tab (PR #293, polished in #296) renders four Log-backed feeds — announcements, changelog (top 5), initiatives, backlog (top 5) — plus two "See all" destinations that re-fetch the same data unbounded (`LogListView` with `.changelog` or `.backlog` mode).

Users report that tapping **"See all" under Changelog** on the Lab tab fails with:

> "Couldn't load the list."

With the Logger line

> `list fetch failed: The data couldn't be read because it isn't in the correct format.`

visible in Xcode Console (redacted as `<private>` on release builds).

## Root cause

The Lab tab itself renders fine, which means the concurrent `announcements()` / `changelog(limit: 5)` / `initiatives()` / `backlog(limit: 5)` / `myReactions()` fetches all succeed in `LabView.load()`. In particular, the **unlimited** `announcements()` and `initiatives()` fetches succeed — so the `Log` decoder is not systemically broken against `v_logs_with_counts`.

The only call that fails is `service.changelog()` (no limit) from `LogListView`. The same query as `changelog(limit: 5)`, just without the `.limit(5)` clause. Therefore one or more rows beyond position 5 (ordered by `published_at desc`, filtered to `species='feature' AND status='shipped' AND published=true`) is returning JSON the Swift `Log` decoder can't parse.

Without access to the remote DB from the session, the exact field/row is not yet identifiable. Two additional factors make diagnosis harder than it should be:

1. The error is logged with `privacy: .private`, redacting the detail on-device.
2. Swift's default `JSONDecoder` behavior fails the **whole response** on the first row error — one bad row breaks the entire list.

## Goals

1. **Resilience:** one un-decodable row must not prevent the rest of the list from rendering.
2. **Diagnosability:** the next time a row fails to decode, the log line must name the row index and the `DecodingError` coding path (which field broke, why).
3. **Centralized:** the protection must apply to all four Log-returning feed methods, not just the changelog "See all" path.
4. **Zero UI impact on the happy path:** when all rows decode, behavior is identical to today.

## Non-goals

- Fixing whatever data defect is causing the current `[Bug]`. Once logging reveals the culprit, that's a separate follow-up (it could be a data fix, a `Log` model gap, or a Supabase-swift quirk).
- Touching web, DB migrations, or RPC surfaces. The data contract of `v_logs_with_counts` is fine.
- Any UI-level notice when rows are skipped. Silent skip + log is the deliberate choice (these are changelog entries, not user data).
- Protecting `myReactions()` (returns `[ReactionRow]`, not `[Log]`) or any other non-Log list fetch.

## Design

### Scope of changes

One file: `apps/ios/Pebbles/Features/Lab/Services/LogsService.swift`.

- **No changes** to `Log.swift` — the model stays strict so any future write/mutation path remains type-safe.
- **No changes** to `LabView.swift` or `LogListView.swift` — the service methods keep returning `[Log]`, so all call sites are unaffected.
- **No changes** to migrations, RPCs, or any other platform/feature.

### New private type: `LossyLogArray`

Added as a file-private `Decodable` inside `LogsService.swift`:

```swift
private struct LossyLogArray: Decodable {
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
                // UnkeyedDecodingContainer does not advance on throw; swallow
                // the element with AnyDecodable so the loop can make progress.
                _ = try? container.decode(AnyDecodable.self)
            }
            index += 1
        }
        self.logs = accumulated
    }

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "logs-service")
}

private struct AnyDecodable: Decodable {
    init(from decoder: Decoder) throws {
        // Touch the decoder without interpreting its value, so the parent
        // UnkeyedDecodingContainer advances past this element. A single
        // value container works for any JSON value (object, array,
        // primitive, null), which is why we prefer it over keyed or
        // compiler-synthesized init.
        _ = try decoder.singleValueContainer()
    }
}
```

The `AnyDecodable` swallow is critical: Swift's `UnkeyedDecodingContainer` does not advance `currentIndex` when `decode(_:)` throws, so without it the `while !container.isAtEnd` loop would spin forever on the first bad row.

Logging uses `String(reflecting: error)` to expose the full `DecodingError` — including the coding path (`codingPath: [_JSONKey(stringValue: "1", intValue: 1), CodingKeys(stringValue: "published_at", …)]`) and the underlying `debugDescription`. Both pieces together name the row index **and** the failing field. `privacy: .public` on the error string is acceptable because it is schema/shape metadata, not user PII.

### Wiring into feed methods

Each of the four feed methods currently ends in `try await base.execute().value` with return type `[Log]` inferred. Change each to decode as `LossyLogArray` and return `.logs`. Example (`changelog`):

```swift
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

Apply the same pattern to `announcements(limit:)`, `initiatives()`, and `backlog(limit:)`. `myReactions()`, `react(logId:)`, and `unreact(logId:)` are untouched.

The method signatures stay `-> [Log]`; no call-site changes in `LabView` or `LogListView`.

### Error-path behavior

- **All rows decode:** identical to today. `LossyLogArray.logs` is the full list, log category `logs-service` emits nothing new.
- **Some rows fail:** those rows are skipped; successful rows still render; each skip emits one `error`-level log line naming the index + coding path. User sees a list containing fewer items than the total matching rows in the DB — no UI indicator that rows were skipped.
- **Whole response fails** (network, auth, PostgREST error, non-array payload): still throws from `.execute()`, still caught by `LogListView.load()` / `LabView.load()`, still surfaces as "Couldn't load the list." / "Couldn't load the Lab." Behavior unchanged.

## Testing

No automated iOS tests yet (project convention, `CLAUDE.md` under `apps/ios/`). Verification is manual:

1. Build and run on simulator or device.
2. Open the Lab tab — announcements, changelog (top 5), initiatives, backlog (top 5) render as before. Nothing in `os_log` under `logs-service`.
3. Tap **"See all"** under Changelog — list now loads (previously failed). If data in the DB actually has a bad row, fewer rows appear than the total shipped count, and Xcode Console shows at least one `skipped log row N: DecodingError…` line. Copy that line into the follow-up issue for data/model remediation.
4. Tap **"See all"** under Backlog — behavior analogous to (3).
5. Regression sanity: reactions toggle on the backlog list still work (the skipped-row path does not touch reaction handling).

## Risks and mitigations

- **Risk:** silent skipping masks a data bug that should fail loudly.
  **Mitigation:** every skip writes to `os.Logger` at `error` level with `privacy: .public` on the diagnostic payload. The follow-up issue for the actual bad-row fix captures the decoded error.
- **Risk:** `LossyLogArray` diverges from `[Log]` in some edge case (e.g., non-array top-level JSON).
  **Mitigation:** the initial `try decoder.unkeyedContainer()` still throws on a non-array payload — that error path is preserved (bubbles up to the view's catch, matching today's behavior).
- **Risk:** `AnyDecodable` swallowing the bad element could itself fail on exotic inputs.
  **Mitigation:** `_ = try? container.decode(AnyDecodable.self)` uses `try?` so even that path is best-effort. In practice a JSON object or array always decodes into `AnyDecodable: Decodable`.

## Follow-ups (not in this PR)

- Once the first `skipped log row N: …` line appears in the field, open a targeted issue for the underlying data or model defect.
- Consider whether the same lossy pattern belongs on any other list read path in the app (out of scope here — apply only where needed).

## Files touched

- `apps/ios/Pebbles/Features/Lab/Services/LogsService.swift` — add `LossyLogArray` + `AnyDecodable`; rewire four feed methods to decode through the wrapper.

No other files are modified.
