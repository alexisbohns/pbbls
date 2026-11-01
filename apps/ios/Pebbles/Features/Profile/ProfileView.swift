import SwiftUI

/// The You tab root (#1382 D16): the routes, the Settings sheet and Home's
/// carve intent around `YouContent`. Settings opens from the platform's own
/// toolbar button in the navigation bar, as before the redesign; the header
/// starts below the bar.
struct ProfileView: View {
    @Environment(SupabaseService.self) private var supabase
    @Environment(PathStatsService.self) private var stats
    @Environment(TabNavigation.self) private var nav

    @State private var model = YouModel()
    @State private var isPresentingSettings = false

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                YouContent(model: model, stats: YouStats(stats))
                    .id(TabNavigation.scrollTopAnchor)
            }
            .onChange(of: nav.scrollToTop[.you, default: 0]) {
                withAnimation { proxy.scrollTo(TabNavigation.scrollTopAnchor, anchor: .top) }
            }
        }
        .navigationDestination(for: YouRoute.self) { route in
            switch route {
            case .glyphs: GlyphsListView(initialTab: .mine)
            case .glyphTab(let tab): GlyphsListView(initialTab: tab)
            case .achievements: AchievementsView()
            case .lab: LabView()
            }
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                PebbleToolbarButton(action: { isPresentingSettings = true }) {
                    PbblsIconImage(.slider, .toolbar)
                }
                .accessibilityLabel(Text("Settings"))
            }
        }
        .pebblesScreen()
        // Home's checklist asks for a carve: push Glyphs, which consumes the intent.
        .onAppear {
            if nav.peekIntent(for: .you) == .carveGlyph, nav.you.last != .glyphs { nav.you.append(.glyphs) }
        }
        .task {
            if model.service == nil { model.service = YouService(client: supabase.client) }
            await stats.load()
            await model.loadProfile()
        }
        .sheet(isPresented: $isPresentingSettings) {
            SettingsSheet(
                initialDisplayName: model.profile?.displayName ?? "",
                initialGlyphId: model.profile?.glyphId,
                initialGlyphStrokes: model.glyphStrokes,
                initialHandle: model.profile?.handle,
                initialPublicProfile: model.profile?.publicProfile ?? false,
                email: supabase.session?.user.email,
                onSaved: { newName, newGlyph, newHandle, isPublic in
                    model.applySettings(name: newName, glyph: newGlyph.map { ($0.id, $0.strokes) },
                                        handle: newHandle, isPublic: isPublic)
                }
            )
        }
    }
}

#Preview {
    let supabase = SupabaseService()
    return NavigationStack {
        ProfileView()
            .environment(supabase)
            .environment(PathStatsService(supabase: supabase))
            .environment(TabNavigation())
    }
}
