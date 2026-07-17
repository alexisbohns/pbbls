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
        do {
            async let domainsQuery: [Domain] = client
                .from("domains")
                .select()
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

            self.domains = loadedDomains
            self.souls = loadedSouls
            self.collections = loadedCollections
            self.hasLoaded = true
            logger.info("""
                loaded \(loadedDomains.count, privacy: .public) domains, \
                \(loadedSouls.count, privacy: .public) souls, \
                \(loadedCollections.count, privacy: .public) collections
                """)
        } catch {
            logger.error("reference data load failed: \(error.localizedDescription, privacy: .private)")
        }
    }

    /// Re-fetch souls only. Called from `SoulsListView` after create/edit/delete
    /// completes, so the pebble sheets see the new list on next open.
    func refreshSouls() async {
        do {
            let rows: [SoulWithGlyph] = try await client
                .from("souls")
                .select("id, name, glyph_id, glyphs(id, name, strokes, view_box)")
                .order("name")
                .execute()
                .value
            self.souls = rows
        } catch {
            logger.error("souls refresh failed: \(error.localizedDescription, privacy: .private)")
        }
    }

    /// Re-fetch collections only. Called from `CollectionsListView` after
    /// create/edit/delete completes.
    func refreshCollections() async {
        do {
            let rows: [PebbleCollection] = try await client
                .from("collections")
                .select("id, name")
                .order("name")
                .execute()
                .value
            self.collections = rows
        } catch {
            logger.error("collections refresh failed: \(error.localizedDescription, privacy: .private)")
        }
    }
}
