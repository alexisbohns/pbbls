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
    @State private var achievementNotify: AchievementNotificationService
    @State private var achievements: AchievementsService
    @State private var drafts: PebbleDraftsService
    @State private var snapshots: ComposerSnapshotStore
    @State private var connections: ConnectionsService
    @State private var karmaOverlay = KarmaOverlayWindowController()

    init() {
        let supabase = SupabaseService()
        let palettes = EmotionPaletteService(client: supabase.client)
        let refs = ReferenceDataService(client: supabase.client)
        let achievementNotify = AchievementNotificationService()
        let achievements = AchievementsService(client: supabase.client)
        achievements.bind(palettes: palettes, refs: refs, notify: achievementNotify)
        self._supabase = State(initialValue: supabase)
        self._palettes = State(initialValue: palettes)
        self._refs     = State(initialValue: refs)
        self._stats    = State(initialValue: PathStatsService(supabase: supabase))
        self._snapURLs = State(initialValue: SnapURLCache(client: supabase.client))
        self._karma    = State(initialValue: KarmaNotificationService())
        self._achievementNotify = State(initialValue: achievementNotify)
        self._achievements = State(initialValue: achievements)
        self._drafts   = State(initialValue: PebbleDraftsService(client: supabase.client))
        self._snapshots = State(initialValue: ComposerSnapshotStore())
        self._connections = State(initialValue: ConnectionsService(supabase: supabase))
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
                .environment(achievementNotify)
                .environment(achievements)
                .environment(drafts)
                .environment(snapshots)
                .environment(connections)
                .onAppear { karmaOverlay.attachIfNeeded(service: karma, achievements: achievementNotify) }
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
