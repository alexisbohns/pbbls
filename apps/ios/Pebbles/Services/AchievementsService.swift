import Foundation
import Observation
import Supabase
import os

/// One `achievements` catalog row. The catalog is public-read reference data
/// (M48); copy columns are nullable admin overrides — `Achievement+Localized`
/// resolves display copy from them or from the family-keyed catalog strings.
struct AchievementRecord: Identifiable, Decodable, Equatable {
    let id: UUID
    let slug: String
    let family: String
    let threshold: Int?
    let emotionId: UUID?
    let domainId: UUID?
    let sortOrder: Int
    let glyphId: UUID?
    let karmaReward: Int
    let isActive: Bool
    let titleEn: String?
    let titleFr: String?
    let descriptionEn: String?
    let descriptionFr: String?

    enum CodingKeys: String, CodingKey {
        case id
        case slug
        case family
        case threshold
        case emotionId = "emotion_id"
        case domainId = "domain_id"
        case sortOrder = "sort_order"
        case glyphId = "glyph_id"
        case karmaReward = "karma_reward"
        case isActive = "is_active"
        case titleEn = "title_en"
        case titleFr = "title_fr"
        case descriptionEn = "description_en"
        case descriptionFr = "description_fr"
    }
}

/// One of the caller's `achievement_unlocks` rows (owner-scoped by RLS).
///
/// `unlocked_at` is decoded from its **string** form through `ISO8601Flexible`
/// rather than left to the ambient decoder's date strategy (#651): Postgres
/// returns microsecond precision and a fixed-precision decoder fails the whole
/// row, which would silently empty the achievements grid.
struct AchievementUnlockRecord: Decodable, Equatable {
    let achievementId: UUID
    let unlockedAt: Date

    enum CodingKeys: String, CodingKey {
        case achievementId = "achievement_id"
        case unlockedAt = "unlocked_at"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        achievementId = try container.decode(UUID.self, forKey: .achievementId)
        let raw = try container.decode(String.self, forKey: .unlockedAt)
        guard let parsed = ISO8601Flexible.parse(raw) else {
            throw DecodingError.dataCorruptedError(
                forKey: .unlockedAt, in: container,
                debugDescription: "unparseable unlocked_at: \(raw)"
            )
        }
        unlockedAt = parsed
    }

    /// Memberwise init, for tests and previews.
    init(achievementId: UUID, unlockedAt: Date) {
        self.achievementId = achievementId
        self.unlockedAt = unlockedAt
    }
}

/// One newly unlocked badge as `check_achievements()` reports it: the karma is
/// what the server actually emitted at unlock time, not the catalog's current
/// value — the capsule can never show a number the ledger doesn't hold.
struct AchievementCheckResult: Decodable, Equatable {
    let slug: String
    let karmaGranted: Int

    enum CodingKeys: String, CodingKey {
        case slug
        case karmaGranted = "karma_granted"
    }
}

/// Achievements data access + the fire-and-forget evaluation call (M48).
///
/// `check_achievements()` is the design's single evaluation path: idempotent,
/// client-called after count-changing mutations and on screen open — the
/// screen-open call is the retroactive grant, so a failed fire here self-heals
/// on the next call and must never block or fail the mutation that triggered it.
///
/// Production wiring lives in `PebblesApp`; `bind(...)` connects the services
/// the unlock moment needs (they are all constructed there together).
@Observable
@MainActor
final class AchievementsService {

    private let client: SupabaseClient
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "achievements")

    private weak var palettes: EmotionPaletteService?
    private weak var refs: ReferenceDataService?
    private weak var notify: AchievementNotificationService?

    init(client: SupabaseClient) {
        self.client = client
    }

    /// Connects the collaborators the unlock capsule needs. Called once from
    /// `PebblesApp.init`; weak because the app owns all of them for its lifetime.
    func bind(palettes: EmotionPaletteService,
              refs: ReferenceDataService,
              notify: AchievementNotificationService) {
        self.palettes = palettes
        self.refs = refs
        self.notify = notify
    }

    /// Full catalog, inactive rows included — the view filters (an inactive
    /// badge stays visible when already unlocked, hidden while locked).
    func loadCatalog() async throws -> [AchievementRecord] {
        let rows: [AchievementRecord] = try await client
            .from("achievements")
            .select("id, slug, family, threshold, emotion_id, domain_id, sort_order, glyph_id, karma_reward, is_active, title_en, title_fr, description_en, description_fr")
            .order("sort_order", ascending: true)
            .execute()
            .value
        return rows
    }

    /// The caller's unlocks; RLS scopes to `auth.uid()`.
    func loadUnlocks() async throws -> [AchievementUnlockRecord] {
        let rows: [AchievementUnlockRecord] = try await client
            .from("achievement_unlocks")
            .select("achievement_id, unlocked_at")
            .execute()
            .value
        return rows
    }

    /// Runs the evaluation. SETOF-returning RPC, so PostgREST yields an array
    /// (empty = nothing newly unlocked).
    func check() async throws -> [AchievementCheckResult] {
        let rows: [AchievementCheckResult] = try await client
            .rpc("check_achievements")
            .execute()
            .value
        return rows
    }

    /// Screen-open variant: the retroactive grant must never surface as an
    /// error — the grid renders whatever `loadUnlocks()` then returns.
    func checkIgnoringFailure() async {
        do {
            _ = try await check()
        } catch {
            logger.warning("achievement check on screen open failed: \(error.localizedDescription, privacy: .private)")
        }
    }

    /// Mutation-path variant: fire-and-forget from a success handler. Never
    /// throws, never blocks the caller; new unlocks collapse into one capsule
    /// (mirrors web's `fireAchievementCheck`). Karma notifies stay untouched —
    /// the capsule carries its own "+N karma" line.
    func fireCheck() {
        Task { [weak self] in
            guard let self else { return }
            do {
                let results = try await self.check()
                guard !results.isEmpty else { return }
                self.presentUnlockMoment(for: results)
            } catch {
                self.logger.warning("achievement check failed (self-heals on next call): \(error.localizedDescription, privacy: .private)")
            }
        }
    }

    /// Builds one card per newly unlocked badge and hands the queue to the
    /// moment (D13). One extra catalog read per actual unlock (rare) buys the
    /// localized copy without threading i18n through five call sites — the same
    /// trade the web moment makes.
    private func presentUnlockMoment(for results: [AchievementCheckResult]) {
        guard let notify else {
            logger.error("achievement notify service not bound; dropping unlock moment")
            return
        }
        Task { [weak self] in
            guard let self else { return }
            var catalog: [AchievementRecord] = []
            do {
                catalog = try await self.loadCatalog()
            } catch {
                self.logger.warning("catalog fetch for the unlock moment failed: \(error.localizedDescription, privacy: .private)")
            }
            let bySlug = Dictionary(catalog.map { ($0.slug, $0) }, uniquingKeysWith: { first, _ in first })

            let cards = results
                .map { result -> (card: AchievementMomentCard, sortOrder: Int) in
                    guard let record = bySlug[result.slug] else {
                        // A check racing a catalog change: still celebrate,
                        // headlined by the slug, and sort last.
                        return (
                            AchievementMomentCard(
                                id: result.slug, title: result.slug,
                                description: nil, karmaGranted: result.karmaGranted
                            ),
                            Int.max
                        )
                    }
                    let emotionName = self.emotionName(for: record)
                    let domainName = self.domainName(for: record)
                    return (
                        AchievementMomentCard(
                            id: record.slug,
                            title: record.localizedTitle(emotionName: emotionName, domainName: domainName),
                            description: record.localizedDescription(emotionName: emotionName, domainName: domainName),
                            karmaGranted: result.karmaGranted
                        ),
                        record.sortOrder
                    )
                }
                // Chain in the order the ladder reads, so a multi-tier unlock
                // walks up rather than arriving shuffled.
                .sorted { $0.sortOrder < $1.sortOrder }
                .map(\.card)

            notify.present(cards: cards)
        }
    }

    private func emotionName(for record: AchievementRecord) -> String? {
        guard let emotionId = record.emotionId else { return nil }
        return palettes?.byEmotionId[emotionId]?.localizedName
    }

    private func domainName(for record: AchievementRecord) -> String? {
        guard let domainId = record.domainId else { return nil }
        return refs?.domains.first(where: { $0.id == domainId })?.localizedName
    }
}
