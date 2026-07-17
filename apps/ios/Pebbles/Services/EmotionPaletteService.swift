import Foundation
import Observation
import Supabase
import os

/// Caches the contents of `public.v_emotions_with_palette` for the session.
///
/// Loaded once from `RootView.task` during the handcrafted logo loader; the
/// loader gates on `didFinishLoading`. Render surfaces look up by `emotion.id`
/// via `palette(for:)`. A miss (cache not warm yet, or a bad row that the
/// decoder rejected) returns `nil` — callers fall back to
/// `Color.accent.primary` / `Color.accent.primaryHex`. No retry on failure;
/// state recovers on next app launch.
@Observable
@MainActor
final class EmotionPaletteService {
    private(set) var byEmotionId: [UUID: EmotionWithPalette] = [:]
    private(set) var hasLoaded: Bool = false
    /// True once a load attempt has settled — success OR failure. The launch
    /// loader gates on this (not `hasLoaded`) so a failed reference fetch still
    /// lets the app open with an empty cache instead of boiling forever.
    private(set) var didFinishLoading: Bool = false

    private let client: SupabaseClient
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "emotion-palette")

    init(client: SupabaseClient) {
        self.client = client
    }

    /// Fetch the view and populate the cache. Idempotent — safe to call
    /// more than once, though the splash-driven call site only fires once.
    func load() async {
        defer { didFinishLoading = true }
        do {
            let rows: [EmotionWithPalette] = try await client
                .from("v_emotions_with_palette")
                .select()
                .execute()
                .value
            self.byEmotionId = Dictionary(uniqueKeysWithValues: rows.map { ($0.id, $0) })
            self.hasLoaded = true
            logger.info("loaded \(rows.count, privacy: .public) palette rows")
        } catch {
            logger.error("palette load failed: \(error.localizedDescription, privacy: .private)")
        }
    }

    /// Look up the palette for an emotion id. Returns nil if the cache
    /// hasn't loaded yet or the row was rejected by the decoder.
    func palette(for emotionId: UUID) -> EmotionPalette? {
        byEmotionId[emotionId]?.palette
    }
}
