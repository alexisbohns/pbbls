import SwiftUI
import os

/// How many recent badges the shelf shows before "view all" takes over.
private let shelfSize = 6

/// The Profile achievements shelf (D14): the most recently unlocked badges, the
/// total count, and a way into the full grid. It reads the same two queries the
/// grid does — no new endpoint, no new RLS — and M50's public-profile projection
/// reuses exactly this shape.
///
/// The shelf shows what you have EARNED; the grid shows the whole ladder. So
/// locked badges never appear here, and a profile with nothing unlocked yet
/// falls back to a plain invitation rather than an empty row.
struct ProfileAchievementsCard: View {
    @Environment(AchievementsService.self) private var achievements
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(ReferenceDataService.self) private var refs

    @State private var recent: [AchievementRecord] = []
    @State private var unlockedCount = 0
    @State private var hasLoaded = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.achievements-shelf")

    var body: some View {
        NavigationLink {
            AchievementsView()
        } label: {
            HStack(spacing: Spacing.lg) {
                Image(systemName: "trophy")
                    .pebblesIcon(.large)
                    .foregroundStyle(Color.accent.primary)
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text("Achievements")
                        .pebblesFont(.headline)
                        .foregroundStyle(Color.system.foreground)
                    Text(subtitle)
                        .pebblesFont(.subhead)
                        .foregroundStyle(Color.system.secondary)
                    if !recent.isEmpty {
                        HStack(spacing: Spacing.sm) {
                            ForEach(recent) { record in
                                badge(record)
                            }
                        }
                        .padding(.top, Spacing.xs)
                    }
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .pebblesIcon(.medium)
                    .foregroundStyle(Color.system.muted)
            }
            .contentShape(Rectangle())
            .profileCard()
        }
        .buttonStyle(.plain)
        .task { await load() }
    }

    private var subtitle: String {
        guard hasLoaded else { return String(localized: "Badges along your path") }
        guard unlockedCount > 0 else { return String(localized: "Badges along your path") }
        return String(format: NSLocalizedString(
            "achievement.shelf.unlockedCount",
            value: "%lld earned", comment: ""
        ), unlockedCount)
    }

    @ViewBuilder
    private func badge(_ record: AchievementRecord) -> some View {
        Image(systemName: record.familySymbol)
            .pebblesIcon(.medium)
            .foregroundStyle(Color.accent.primary)
            .frame(width: 32, height: 32)
            .background(Circle().fill(Color.accent.primary.opacity(0.12)))
            .accessibilityLabel(
                record.localizedTitle(
                    emotionName: emotionName(for: record),
                    domainName: domainName(for: record)
                )
            )
    }

    /// Reads only — the shelf never fires an evaluation. The grid's screen-open
    /// call is the retroactive grant; doing it here too would double the work on
    /// every profile visit.
    private func load() async {
        guard !hasLoaded else { return }
        do {
            async let catalogRows = achievements.loadCatalog()
            async let unlockRows = achievements.loadUnlocks()
            let catalog = try await catalogRows
            let unlocks = try await unlockRows

            let byId = Dictionary(catalog.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
            let earned = unlocks
                .sorted { $0.unlockedAt > $1.unlockedAt }
                .compactMap { byId[$0.achievementId] }

            unlockedCount = earned.count
            recent = Array(earned.prefix(shelfSize))
            hasLoaded = true
        } catch {
            // A failed shelf is not worth an error state on the profile: the
            // card still navigates, and the grid surfaces real failures.
            logger.error("achievements shelf fetch failed: \(error.localizedDescription, privacy: .private)")
            hasLoaded = true
        }
    }

    private func emotionName(for record: AchievementRecord) -> String? {
        guard let emotionId = record.emotionId else { return nil }
        return palettes.byEmotionId[emotionId]?.localizedName
    }

    private func domainName(for record: AchievementRecord) -> String? {
        guard let domainId = record.domainId else { return nil }
        return refs.domains.first(where: { $0.id == domainId })?.localizedName
    }
}

#Preview {
    let supabase = SupabaseService()
    return NavigationStack {
        ProfileAchievementsCard()
            .environment(AchievementsService(client: supabase.client))
            .environment(EmotionPaletteService(client: supabase.client))
            .environment(ReferenceDataService(client: supabase.client))
            .padding()
    }
}
