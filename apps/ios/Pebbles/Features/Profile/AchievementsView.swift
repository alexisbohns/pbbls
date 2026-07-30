import SwiftUI
import os

/// Achievements grid (M48, D8): badges grouped by family in catalog
/// `sort_order`, locked ones greyed alongside the unlocked. Opening the screen
/// fires `check_achievements()` first — that call *is* the retroactive grant
/// for existing users — then reads catalog + unlocks, so history appears
/// unlocked on first visit with no celebration chain (the capsule is for the
/// mutation path only, D13 territory).
struct AchievementsView: View {
    @Environment(AchievementsService.self) private var achievements
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(ReferenceDataService.self) private var refs

    @State private var catalog: [AchievementRecord] = []
    @State private var unlockedAt: [UUID: Date] = [:]
    @State private var isLoading = true
    @State private var loadError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.achievements")

    private let columns = [GridItem(.adaptive(minimum: 148), spacing: Spacing.lg)]

    var body: some View {
        content
            .pebblesToolbarTitle("Achievements")
            .task { await load() }
            .pebblesScreen()
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError {
            ContentUnavailableView(
                "Couldn't load achievements",
                systemImage: "exclamationmark.triangle",
                description: Text(loadError)
            )
        } else {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: Spacing.xl) {
                    ForEach(familyGroups, id: \.family) { group in
                        VStack(alignment: .leading, spacing: Spacing.md) {
                            Text(AchievementRecord.localizedGroupName(family: group.family))
                                .pebblesFont(.headline)
                                .foregroundStyle(Color.system.foreground)
                            LazyVGrid(columns: columns, spacing: Spacing.lg) {
                                ForEach(group.records) { record in
                                    AchievementBadgeCell(
                                        record: record,
                                        unlockedAt: unlockedAt[record.id],
                                        emotionName: emotionName(for: record),
                                        domainName: domainName(for: record)
                                    )
                                }
                            }
                        }
                    }
                }
                .padding(Spacing.lg)
            }
        }
    }

    /// Families in first-appearance order (the catalog is already sorted by
    /// `sort_order`, which the migration blocks per family). Inactive badges
    /// only show once earned — retiring one hides it from the ladder without
    /// revoking anyone's unlock.
    private var familyGroups: [(family: String, records: [AchievementRecord])] {
        let visible = catalog.filter { $0.isActive || unlockedAt[$0.id] != nil }
        var order: [String] = []
        var byFamily: [String: [AchievementRecord]] = [:]
        for record in visible {
            if byFamily[record.family] == nil { order.append(record.family) }
            byFamily[record.family, default: []].append(record)
        }
        return order.map { (family: $0, records: byFamily[$0] ?? []) }
    }

    private func emotionName(for record: AchievementRecord) -> String? {
        guard let emotionId = record.emotionId else { return nil }
        return palettes.byEmotionId[emotionId]?.localizedName
    }

    private func domainName(for record: AchievementRecord) -> String? {
        guard let domainId = record.domainId else { return nil }
        return refs.domains.first(where: { $0.id == domainId })?.localizedName
    }

    private func load() async {
        isLoading = true
        loadError = nil
        // The retroactive grant precedes the read so a veteran's history is
        // already unlocked by the time the grid renders; its errors are
        // deliberately non-fatal (the next call self-heals).
        await achievements.checkIgnoringFailure()
        do {
            async let catalogRows = achievements.loadCatalog()
            async let unlockRows = achievements.loadUnlocks()
            let loadedCatalog = try await catalogRows
            let loadedUnlocks = try await unlockRows
            self.catalog = loadedCatalog
            self.unlockedAt = Dictionary(
                loadedUnlocks.map { ($0.achievementId, $0.unlockedAt) },
                uniquingKeysWith: { first, _ in first }
            )
        } catch {
            logger.error("achievements fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Something went wrong. Please try again."
        }
        self.isLoading = false
    }
}

/// One badge tile. Locked state reads through more than colour: muted styling
/// plus a lock mark and an explicit caption, mirroring the web grid.
struct AchievementBadgeCell: View {
    let record: AchievementRecord
    let unlockedAt: Date?
    let emotionName: String?
    let domainName: String?

    private var isUnlocked: Bool { unlockedAt != nil }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            HStack {
                Image(systemName: record.familySymbol)
                    .pebblesIcon(.large)
                    .foregroundStyle(isUnlocked ? Color.accent.primary : Color.system.muted)
                Spacer()
                if !isUnlocked {
                    Image(systemName: "lock")
                        .pebblesIcon(.medium)
                        .foregroundStyle(Color.system.muted)
                }
            }
            Text(record.localizedTitle(emotionName: emotionName, domainName: domainName))
                .pebblesFont(.headline)
                .foregroundStyle(isUnlocked ? Color.system.foreground : Color.system.secondary)
                .multilineTextAlignment(.leading)
            if let description = record.localizedDescription(emotionName: emotionName, domainName: domainName) {
                Text(description)
                    .pebblesFont(.subhead)
                    .foregroundStyle(Color.system.secondary)
                    .multilineTextAlignment(.leading)
            }
            if let unlockedAt {
                HStack(spacing: 4) {
                    Text("Unlocked")
                    Text(unlockedAt, format: .dateTime.day().month().year())
                }
                .pebblesFont(.subhead)
                .foregroundStyle(Color.accent.primary)
            } else {
                Text("Locked")
                    .pebblesFont(.subhead)
                    .foregroundStyle(Color.system.muted)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .profileCard()
        .opacity(isUnlocked ? 1 : 0.72)
        .accessibilityElement(children: .combine)
    }
}

#Preview {
    let supabase = SupabaseService()
    return NavigationStack {
        AchievementsView()
            .environment(AchievementsService(client: supabase.client))
            .environment(EmotionPaletteService(client: supabase.client))
            .environment(ReferenceDataService(client: supabase.client))
    }
}
