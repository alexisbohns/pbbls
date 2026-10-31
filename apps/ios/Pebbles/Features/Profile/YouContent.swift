import SwiftUI

/// You's page without the environment (#1382 D16): `ProfileView` draws it in
/// its ScrollView with the live model and stats, the references draw it from
/// `YouFixture`.
struct YouContent: View {
    let model: YouModel
    let stats: YouStats
    var onSettings: () -> Void = {}
    var onLogOut: () -> Void = {}

    var body: some View {
        VStack(spacing: Spacing.xl) {
            YouHeader(name: model.profile?.displayName, memberSince: model.profile?.createdAt,
                      glyphStrokes: model.glyphStrokes, rippleLevel: stats.rippleLevel,
                      activeToday: stats.activeToday, onSettings: onSettings)
            ProfileShortcutsRow()
            ProfileStatsCard(ripple: stats.rippleLevel.map {
                                 RippleSummary(rippleLevel: $0, pebbles28d: 0, activeToday: stats.activeToday)
                             },
                             assiduity: nil, daysPracticed: stats.daysPracticed, pebbles: stats.pebbles,
                             karma: stats.karma)
            ProfileAchievementsCard()
            ProfileLabCard()
            ProfileLogoutButton(action: onLogOut)
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 32)
    }
}
