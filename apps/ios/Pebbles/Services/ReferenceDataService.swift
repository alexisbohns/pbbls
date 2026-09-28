import Foundation
import Observation
import Supabase
import os

/// Caches the three reference lists used by `CreatePebbleSheet` and
/// `EditPebbleSheet` (`domains`, `souls`, `collections`) for the session.
///
/// Loaded once from `RootView.task` alongside `EmotionPaletteService`, during
/// the handcrafted logo loader; the loader gates on `didFinishLoading`.
/// Subsequent sheet opens read directly from the cached arrays — no per-open
/// round-trips. `domains` is seed data and never refreshes at runtime;
/// `souls` and `collections` are refreshed via `refreshSouls()` /
/// `refreshCollections()` after the matching Profile mutations succeed.
///
/// No retry on failure: a failed `load()` leaves the arrays empty and pickers
/// render empty, matching the UX of an offline launch. State recovers on the
/// next app launch.
///
/// `souls` and `collections` belong to the signed-in user (souls are the names
/// of real people in their life), so `reset()` drops them when the session
/// ends and `RootView` reloads on every sign-in.
@Observable
@MainActor
final class ReferenceDataService {
    private(set) var domains: [Domain] = []
    private(set) var souls: [SoulWithGlyph] = []
    private(set) var collections: [PebbleCollection] = []
    private(set) var hasLoaded: Bool = false
    /// True once a load attempt has settled — success OR failure. The launch
    /// loader gates on this (not `hasLoaded`) so a failed reference fetch still
    /// lets the app open with an empty cache instead of boiling forever.
    private(set) var didFinishLoading: Bool = false

    /// Bumped by `reset()` and by each `load()`. A fetch started under an older
    /// generation is stale (its session ended, or a newer load replaced it), so
    /// its rows are dropped when they land.
    private var generation = 0

    private let client: SupabaseClient
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "reference-data")

    init(client: SupabaseClient) {
        self.client = client
    }

    /// Fetch all three lists in parallel and populate the cache. Idempotent —
    /// the splash-driven call site only fires once, but safe to call again
    /// (e.g. for retry after a transient launch-time failure).
    func load() async {
        defer { didFinishLoading = true }
        // Latest wins: a load started for an earlier session (or superseded by
        // the sign-in reload) must never land on top of a newer one.
        generation += 1
        let started = generation
        do {
            // v_domains_with_glyph (security_invoker) flattens the default
            // glyph onto each row so the record flow's domain picker can draw
            // it without a second round-trip (D6).
            async let domainsQuery: [Domain] = client
                .from("v_domains_with_glyph")
                .select("id, slug, name, label, strokes, view_box")
                .order("name")
                .execute()
                .value
            async let soulsQuery: [SoulWithGlyph] = client
                .from("souls")
                .select("id, name, glyph_id, glyphs(id, name, strokes, view_box)")
                .order("name")
                .execute()
                .value
            async let collectionsQuery: [PebbleCollection] = client
                .from("collections")
                .select("id, name")
                .order("name")
                .execute()
                .value

            let (loadedDomains, loadedSouls, loadedCollections) =
                try await (domainsQuery, soulsQuery, collectionsQuery)

            guard generation == started else {
                logger.info("dropping reference data fetched for an ended session")
                return
            }
            replace(domains: loadedDomains, souls: loadedSouls, collections: loadedCollections)
            logger.info("""
                loaded \(loadedDomains.count, privacy: .public) domains, \
                \(loadedSouls.count, privacy: .public) souls, \
                \(loadedCollections.count, privacy: .public) collections
                """)
        } catch {
            // `RootView` cancels and restarts the load when the signed-in user
            // changes. That is not a failure worth logging as one.
            guard !Task.isCancelled else { return }
            logger.error("reference data load failed: \(error.localizedDescription, privacy: .private)")
        }
    }

    /// The one place a full load lands. Internal so tests can stand the cache
    /// up without a network.
    func replace(domains: [Domain], souls: [SoulWithGlyph], collections: [PebbleCollection]) {
        self.domains = domains
        self.souls = souls
        self.collections = collections
        self.hasLoaded = true
    }

    /// Forget the signed-out user's souls and collections. `domains` is shared
    /// seed data, so it stays. `hasLoaded` drops back to false so a composer
    /// opened by the next user waits for their own lists instead of sanitizing
    /// a restored draft against empty ones (#647). `didFinishLoading` is left
    /// alone: it only gates the launch loader, which has already settled.
    func reset() {
        generation += 1
        souls = []
        collections = []
        hasLoaded = false
    }

    /// Re-fetch souls only. Called from `SoulsListView` after create/edit/delete
    /// completes, so the pebble sheets see the new list on next open.
    func refreshSouls() async {
        let started = generation
        do {
            let rows: [SoulWithGlyph] = try await client
                .from("souls")
                .select("id, name, glyph_id, glyphs(id, name, strokes, view_box)")
                .order("name")
                .execute()
                .value
            guard generation == started else { return }
            self.souls = rows
        } catch {
            logger.error("souls refresh failed: \(error.localizedDescription, privacy: .private)")
        }
    }

    /// Re-fetch collections only. Called from `CollectionsListView` after
    /// create/edit/delete completes.
    func refreshCollections() async {
        let started = generation
        do {
            let rows: [PebbleCollection] = try await client
                .from("collections")
                .select("id, name")
                .order("name")
                .execute()
                .value
            guard generation == started else { return }
            self.collections = rows
        } catch {
            logger.error("collections refresh failed: \(error.localizedDescription, privacy: .private)")
        }
    }
}
