import Foundation
import os
import Supabase

/// Settings' links (#1489): the last good payload on disk, else the bundled seed.
struct SettingsLinksStore {
    private static let key = "settingsLinks.payload"
    let defaults: UserDefaults

    func current() -> [SettingsLink] {
        guard let data = defaults.data(forKey: Self.key), let links = try? SettingsLink.decodeList(data) else {
            return SettingsLink.builtIn
        }
        return links
    }

    /// Keeps `data` only if this build can read it, so a bad payload never replaces a good one.
    func save(_ data: Data) throws {
        _ = try SettingsLink.decodeList(data)
        defaults.set(data, forKey: Self.key)
    }
}

/// Refreshes the kept links from `settings_links` each time Settings opens:
/// a single-table select of the published rows, so no RPC.
struct SettingsLinksService {
    let client: SupabaseClient
    let store: SettingsLinksStore
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "settings-links")

    init(client: SupabaseClient, store: SettingsLinksStore = SettingsLinksStore(defaults: .standard)) {
        self.client = client
        self.store = store
    }

    /// Fetches, keeps and returns the published links; on failure returns what is kept.
    func refresh() async -> [SettingsLink] {
        do {
            let data = try await client
                .from("settings_links")
                .select("key,section,sort_order,title_en,title_fr,url")
                .eq("published", value: true)
                .order("section")
                .order("sort_order")
                .execute()
                .data
            try store.save(data)
        } catch {
            logger.error("settings links refresh failed: \(error.localizedDescription, privacy: .public)")
        }
        return store.current()
    }
}
