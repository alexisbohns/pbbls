import SwiftUI
import UIKit

@main
struct PebblesApp: App {
    @State private var supabase: SupabaseService
    @State private var palettes: EmotionPaletteService
    @State private var refs: ReferenceDataService
    @State private var stats: PathStatsService
    @State private var snapURLs: SnapURLCache
    @State private var karma: KarmaNotificationService

    init() {
        let supabase = SupabaseService()
        self._supabase = State(initialValue: supabase)
        self._palettes = State(initialValue: EmotionPaletteService(client: supabase.client))
        self._refs     = State(initialValue: ReferenceDataService(client: supabase.client))
        self._stats    = State(initialValue: PathStatsService(supabase: supabase))
        self._snapURLs = State(initialValue: SnapURLCache(client: supabase.client))
        let karma = KarmaNotificationService(haptics: HapticsService())
        karma.liveActivityPresenter = KarmaLiveActivityController()
        self._karma    = State(initialValue: karma)
        Self.configureSegmentedControlAppearance()
        Self.configureNavigationBarAppearance()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(supabase)
                .environment(palettes)
                .environment(refs)
                .environment(stats)
                .environment(snapURLs)
                .environment(karma)
        }
    }

    /// Restyles the system segmented control to match the Pebbles design tokens.
    /// Applied globally because the app currently has only one segmented Picker
    /// (`PebblesAuthSwitcher`); if a second variant is added later, scope this
    /// via `appearance(whenContainedInInstancesOf:)`.
    private static func configureSegmentedControlAppearance() {
        let muted = UIColor(named: "SystemMuted") ?? .systemGray5
        let secondary = UIColor(named: "SystemSecondary") ?? .systemGray

        let proxy = UISegmentedControl.appearance()
        proxy.backgroundColor = muted
        proxy.selectedSegmentTintColor = secondary

        proxy.setTitleTextAttributes([
            .foregroundColor: UIColor.white,
            .font: UIFont.systemFont(ofSize: UIFont.systemFontSize, weight: .medium)
        ], for: .selected)

        proxy.setTitleTextAttributes([
            .foregroundColor: secondary,
            .font: UIFont.systemFont(ofSize: UIFont.systemFontSize, weight: .regular)
        ], for: .normal)
    }

    /// Recolors the navigation bar title so it picks up `system.foreground`
    /// instead of SwiftUI's default `UIColor.label`. Asset-catalog can't
    /// override `Color.primary` directly — only `AccentColor` has that
    /// magic — so titles must be re-tinted via the UIKit appearance proxy.
    /// Applied to both inline and large-title display modes.
    private static func configureNavigationBarAppearance() {
        guard let foreground = UIColor(named: "SystemForeground") else { return }

        let appearance = UINavigationBarAppearance()
        appearance.configureWithDefaultBackground()
        appearance.titleTextAttributes = [.foregroundColor: foreground]
        appearance.largeTitleTextAttributes = [.foregroundColor: foreground]

        let proxy = UINavigationBar.appearance()
        proxy.standardAppearance = appearance
        proxy.scrollEdgeAppearance = appearance
        proxy.compactAppearance = appearance
    }
}
