import SwiftUI

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

    /// "3 earned", "1 décroché", "3 décrochés": the count, plural by the catalog.
    static func earned(_ count: Int, locale: Locale) -> String {
        SpineStrings.format("achievement.shelf.unlockedCount", locale: locale, count)
    }
}

/// You's Achievements (#1382 D20): the head with the served count ("12
/// earned"), then the first four served badges as cards (two columns at
/// accessibility sizes). The head and every card open the page; with nothing
/// served, one card invites. The shelf's load lives in `YouModel`.
struct YouAchievementsSection: View {
    let badges: [AchievementRecord]
    let count: Int?
    var onOpen: () -> Void = {}

    @Environment(\.dynamicTypeSize) private var typeSize
    private var locale: Locale { SpineStrings.appLocale }

    /// The cards shown: the first four served.
    static func shown(_ badges: [AchievementRecord]) -> [AchievementRecord] { Array(badges.prefix(4)) }

    /// "earned", "décroché", "décrochés": the word beside the count. Two keys,
    /// not a plural: a plural variation must show its number, and this word stands
    /// beside it. The count is never zero here (none earned shows the invitation).
    static func earnedWord(_ count: Int, locale: Locale) -> String {
        SpineStrings.string(count == 1 ? "achievement.shelf.earnedWord.one" : "achievement.shelf.earnedWord.other",
                            locale: locale)
    }

    /// Room under the cards for their shadow, given back to the page below.
    private static let shadowRoom: CGFloat = 22

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            PebblesSectionHead(String(localized: "Achievements"),
                               figure: count.map { .counted($0, word: Self.earnedWord($0, locale: locale)) },
                               action: onOpen)
            let shown = Self.shown(badges)
            Group {
                if shown.isEmpty {
                    NavigationLink(value: YouRoute.achievements) {
                        Text("Badges along your path")
                            .spineType(.rowLine)
                            .foregroundStyle(Color.pebbles.onSurfaceVariant)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(18)
                            .pebblesCard()
                    }
                    .buttonStyle(.plain)
                } else {
                    let columns = Array(repeating: GridItem(.flexible(), spacing: 10),
                                        count: typeSize.isAccessibilitySize ? 2 : 4)
                    LazyVGrid(columns: columns, spacing: 10) {
                        ForEach(shown, id: \.slug) { badge($0) }
                    }
                }
            }
            .padding(.bottom, Self.shadowRoom)
        }
        .padding(.bottom, -Self.shadowRoom)
    }

    /// The private families never reach the served shelf, so no emotion or domain name is needed.
    private func badge(_ record: AchievementRecord) -> some View {
        NavigationLink(value: YouRoute.achievements) {
            VStack(spacing: 8) {
                PbblsIconImage(record.familySymbol, size: 30, style: AnyShapeStyle(Color.pebbles.primary))
                Text(verbatim: record.localizedTitle(emotionName: nil, domainName: nil))
                    .spineType(.badgeLabel)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
            }
            .padding(EdgeInsets(top: 16, leading: 4, bottom: 12, trailing: 4))
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .pebblesCard()
            .accessibilityElement(children: .combine)
        }
        .buttonStyle(.plain)
    }
}
