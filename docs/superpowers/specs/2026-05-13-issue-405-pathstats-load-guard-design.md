# PathStatsService load-once guard — design

**Issue:** [#405 — PathStatsService.load() has no guard and fires on every ProfileView appearance](https://github.com/pebbles-design/pebbles/issues/405)
**Scope:** iOS — `apps/ios/Pebbles/Features/Path/Services/PathStatsService.swift`
**Size:** Small (single file, ~15 LOC added)

## Problem

`PathStatsService.load()` has no concurrency or "already loaded" guard. It is called from two places:

- `PathView.swift:41` — `.task { await stats.load() }` (fires once on PathView mount — correct).
- `ProfileView.swift:100` — `.task { await loadStats() }` which calls `stats.load()`. Because `ProfileView` is pushed onto a `NavigationStack`, this `.task` re-fires **every time the user navigates to Profile**.

Each call fires two PostgREST queries against `v_karma_summary` and `v_bounce`. Repeated profile visits produce redundant requests; if `PathView` and `ProfileView` both fire `load()` concurrently at a transition, two pairs of queries are in flight with no coordination.

## Goals

- Stop redundant network traffic on repeat `ProfileView` appearances.
- Prevent concurrent in-flight `load()` calls.
- Provide a forward-looking API so that callers that genuinely need fresh stats (after a mutation) can request one explicitly.

## Non-goals

- Wiring `refresh()` to mutation sites (create / update / delete pebble). The bug is "fires on every appearance"; deciding *when* stats are dirty enough to warrant a refresh is a separate design question and out of scope for this PR.
- Resetting `hasLoaded` on sign-out. If that's an actual bug, it gets its own issue.
- Adding tests. There is no test infrastructure for iOS services yet (Swift Testing exists but the `PathStatsService` module has no test target wired in). Verification is manual.

## Design

Update `PathStatsService` only. No call-site changes required.

```swift
@Observable
@MainActor
final class PathStatsService {
    var karma: Int?
    var bounce: Int?

    private var isLoading = false
    private(set) var hasLoaded = false

    private let supabase: SupabaseService
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "path-stats")

    init(supabase: SupabaseService) {
        self.supabase = supabase
    }

    /// Idempotent: returns immediately if already loaded or currently loading.
    /// Safe to call from every view's `.task` modifier.
    func load() async {
        guard !hasLoaded, !isLoading else { return }
        await performLoad()
    }

    /// Forces a network reload, bypassing the `hasLoaded` cache.
    /// Still guards against concurrent calls so spam-tapping cannot fan out parallel queries.
    func refresh() async {
        guard !isLoading else { return }
        await performLoad()
    }

    private func performLoad() async {
        isLoading = true
        defer { isLoading = false }

        async let karmaResult: KarmaSummary = supabase.client
            .from("v_karma_summary").select("total_karma, pebbles_count")
            .single().execute().value
        async let bounceResult: BounceSummary = supabase.client
            .from("v_bounce").select("bounce_level, active_days")
            .single().execute().value

        do {
            self.karma = try await karmaResult.totalKarma
        } catch {
            logger.error("karma fetch failed: \(error.localizedDescription, privacy: .private)")
        }

        do {
            self.bounce = try await bounceResult.bounceLevel
        } catch {
            logger.error("bounce fetch failed: \(error.localizedDescription, privacy: .private)")
        }

        hasLoaded = true
    }
}
```

### Why this shape

- `load()` is the cached entry point. Both view `.task`s keep calling it, and second-and-later calls become free no-ops.
- `refresh()` is the explicit cache-bust for future callers. It still guards against concurrent calls.
- `hasLoaded` is set inside `performLoad()` **after** the fetch completes, not at the start. If both `karmaResult` and `bounceResult` throw, `karma` and `bounce` remain `nil` but `hasLoaded` flips to `true` — meaning subsequent `load()` calls will not retry. This matches the current failure model (the service silently stays at `nil` on error; only an explicit `refresh()` retries). If we wanted automatic retry on error, that would be a behavior change beyond the bug fix and is out of scope.
- `hasLoaded` is `private(set)` so future views can read it (e.g., to decide whether to show a spinner only on first load).
- `@MainActor` already serializes access to `isLoading` / `hasLoaded`, so no additional locking is required.

### Call sites — no changes

- `PathView.swift:41` — `.task { await stats.load() }` — unchanged. Fires once on mount and is now self-protecting.
- `ProfileView.swift:100,118–120` — `.task { await loadStats() }` calling `stats.load()` — unchanged. Becomes a no-op after the first call.

## Verification

Manual: launch app, sign in, navigate Path → Profile → back to Path → Profile (multiple times). With network logging on, expect exactly one pair of queries against `v_karma_summary` and `v_bounce` for the session.

## Out of scope / follow-ups

- Wiring `refresh()` to pebble-mutation callbacks if/when product wants stats to update mid-session without a full app restart.
- Test target for `PathStatsService` (and other iOS services).
- Resetting `hasLoaded` on sign-out, if that surfaces as a real-world issue.
