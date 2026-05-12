import SwiftUI
import UIKit

@main
struct PebblesApp: App {
    @State private var supabase: SupabaseService
    @State private var palettes: EmotionPaletteService
    @State private var stats: PathStatsService
    @State private var snapURLs: SnapURLCache

    init() {
        let supabase = SupabaseService()
        self._supabase = State(initialValue: supabase)
        self._palettes = State(initialValue: EmotionPaletteService(client: supabase.client))
        self._stats    = State(initialValue: PathStatsService(supabase: supabase))
        self._snapURLs = State(initialValue: SnapURLCache(client: supabase.client))
        Self.configureSegmentedControlAppearance()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(supabase)
                .environment(palettes)
                .environment(stats)
                .environment(snapURLs)
        }
    }

    /// Restyles the system segmented control to match the Pebbles design tokens.
    /// Applied globally because the app currently has only one segmented Picker
    /// (`PebblesAuthSwitcher`); if a second variant is added later, scope this
    /// via `appearance(whenContainedInInstancesOf:)`.
    private static func configureSegmentedControlAppearance() {
        let muted = UIColor(named: "Muted") ?? .systemGray5
        let mutedForeground = UIColor(named: "MutedForeground") ?? .systemGray

        let proxy = UISegmentedControl.appearance()
        proxy.backgroundColor = muted
        proxy.selectedSegmentTintColor = mutedForeground

        proxy.setTitleTextAttributes([
            .foregroundColor: UIColor.white,
            .font: UIFont.systemFont(ofSize: UIFont.systemFontSize, weight: .medium)
        ], for: .selected)

        proxy.setTitleTextAttributes([
            .foregroundColor: mutedForeground,
            .font: UIFont.systemFont(ofSize: UIFont.systemFontSize, weight: .regular)
        ], for: .normal)
    }
}
