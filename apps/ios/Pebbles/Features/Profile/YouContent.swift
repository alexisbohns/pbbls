import SwiftUI

/// You's page without the environment (#1382 D16): `ProfileView` draws it in
/// its ScrollView with the live model and stats, the references draw it from
/// `YouFixture`.
struct YouContent: View {
    let model: YouModel
    let stats: YouStats
    var onSettings: () -> Void = {}

    var body: some View {
        VStack(spacing: YouLayout.sectionGap) {
            YouHeader(name: model.profile?.displayName, memberSince: model.profile?.createdAt,
                      glyphStrokes: model.glyphStrokes, rippleLevel: stats.rippleLevel,
                      activeToday: stats.activeToday, onSettings: onSettings)
            YouCounters(stats: stats)
                .padding(.top, YouLayout.countersRise)
            YouGlyphsSection(carved: stats.glyphsCarved, owned: stats.glyphsOwned)
            ProfileAchievementsCard()
            ProfileLabCard()
        }
        .padding(.horizontal, 16)
        .padding(.top, Spacing.md)
        .padding(.bottom, 40)
    }
}

/// You's vertical rhythm (#1382, the c3 mock).
enum YouLayout {
    /// Between two sections.
    static let sectionGap: CGFloat = 44
    /// The counters sit closer under the header (c3's `closer`): 44 − 10.
    static let countersRise: CGFloat = -10
}
