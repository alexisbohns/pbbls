import SwiftUI
import os

/// The shelf's badges from the served row (#1306, D18): the server picks and
/// orders them (the six most recent outside the private families) and counts
/// them; the catalog, by slug, gives each its title and icon. A slug the catalog
/// does not hold (a stale or failed catalog read) still shows, from the served
/// row's family and threshold.
enum ProfileShelf {
    static func badges(_ page: AchievementsPage?, catalog: [String: AchievementRecord]) -> [AchievementRecord] {
        (page?.shelf ?? []).map { item in
            catalog[item.slug] ?? AchievementRecord(
                id: UUID(), slug: item.slug, family: item.family, threshold: item.threshold,
                emotionId: nil, domainId: nil, sortOrder: 0, glyphId: nil, karmaReward: 0, isActive: true,
                titleEn: nil, titleFr: nil, descriptionEn: nil, descriptionFr: nil)
        }
    }

    /// The served count, or nil when the server sent none (the shelf then just invites).
    static func count(_ page: AchievementsPage?) -> Int? {
        page?.shelfCount.flatMap { $0 > 0 ? $0 : nil }
    }
}

/// The Profile achievements shelf (D14, D18): the most recent badges and their
/// count, as `get_achievements` serves them, and a way into the page. It shares
/// the page's kept copy, so it draws at once, and reads only when that copy is
/// fifteen minutes old or more; a failed read keeps what is shown, and a missing
/// section leaves the plain invitation. It never fires an evaluation: the page's
/// screen-open call is the retroactive grant.
struct ProfileAchievementsCard: View {
    @Environment(SupabaseService.self) private var supabase
    @Environment(AchievementsService.self) private var achievements

    @State private var model = AchievementsModel()
    @State private var catalog: [String: AchievementRecord] = [:]

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.achievements-shelf")

    /// How old the kept copy may be before a visit to You reads again.
    static let maxAge: TimeInterval = 15 * 60

    var body: some View {
        let badges = ProfileShelf.badges(model.page, catalog: catalog)
        NavigationLink(value: YouRoute.achievements) {
            HStack(spacing: Spacing.lg) {
                PbblsIconImage(.pebbleGlowMd, .tile, style: AnyShapeStyle(Color.pebbles.primary))
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text("Achievements")
                        .pebblesFont(.headline)
                        .foregroundStyle(Color.pebbles.onSurface)
                    Text(subtitle)
                        .pebblesFont(.subhead)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    if !badges.isEmpty {
                        HStack(spacing: Spacing.sm) {
                            ForEach(badges, id: \.slug) { record in
                                badge(record)
                            }
                        }
                        .padding(.top, Spacing.xs)
                    }
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 15, weight: .medium, design: .rounded))
                    .foregroundStyle(Color.pebbles.outline)
            }
            .contentShape(Rectangle())
            .profileCard()
        }
        .buttonStyle(.plain)
        .task { await load() }
    }

    private var subtitle: String {
        guard let count = ProfileShelf.count(model.page) else { return String(localized: "Badges along your path") }
        return String(format: NSLocalizedString(
            "achievement.shelf.unlockedCount",
            value: "%lld earned", comment: ""
        ), count)
    }

    /// The private families never reach the served shelf, so no emotion or domain name is needed.
    private func badge(_ record: AchievementRecord) -> some View {
        PbblsIconImage(record.familySymbol, .row, style: AnyShapeStyle(Color.pebbles.primary))
            .frame(width: 32, height: 32)
            .background(Circle().fill(Color.pebbles.primary.opacity(0.12)))
            .accessibilityLabel(record.localizedTitle(emotionName: nil, domainName: nil))
    }

    private func load() async {
        if model.service == nil {
            model.service = AchievementsPageService(client: supabase.client, disk: .standard,
                                                    owner: supabase.session?.user.id.uuidString.lowercased())
        }
        await model.loadIfStale(maxAge: Self.maxAge)
        // The catalog once per shelf, for titles and icons; the served rows stand in until it lands.
        guard catalog.isEmpty, !ProfileShelf.badges(model.page, catalog: [:]).isEmpty,
              let rows = await loadCatalog() else { return }
        catalog = Dictionary(rows.map { ($0.slug, $0) }, uniquingKeysWith: { first, _ in first })
    }

    /// The catalog for titles and icons; a failed read keeps the last one and
    /// the shelf falls back to the served rows.
    private func loadCatalog() async -> [AchievementRecord]? {
        do {
            return try await achievements.loadCatalog()
        } catch {
            logger.error("achievements catalog fetch failed: \(error.localizedDescription, privacy: .private)")
            return nil
        }
    }
}

#Preview {
    let supabase = SupabaseService()
    return NavigationStack {
        ProfileAchievementsCard()
            .environment(supabase)
            .environment(AchievementsService(client: supabase.client))
            .padding()
    }
}
