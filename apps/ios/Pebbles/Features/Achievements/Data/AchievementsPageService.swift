import Foundation
import os
import Supabase

/// What a ceremony pull returned (spec §3.4).
enum CeremonyPull: Equatable, Sendable {
    case pulled(karma: Int)
    /// No row: the ceremony was already pulled, perhaps by this client's own
    /// pull whose answer was lost. Never success on its own: the model re-reads
    /// the page and goes by the completion's `pulled_at`.
    case alreadyPulled
}

enum CeremonyPullError: Error, Equatable {
    /// `not_ready`: before `offered_from`, or a set never completed.
    case notReady
}

/// The seam `AchievementsModel`'s tests fake (#1306).
protocol AchievementsPageServicing: Sendable {
    func page(zone: TimeZone) async throws -> AchievementsPage
    /// The device's last copy, or nil: display only, never instead of a fetch.
    func cachedPage(zone: TimeZone) async -> AchievementsPage?
    /// When the device's last copy was kept: the last read that landed, in any
    /// session (each one rewrites it). Nil when nothing is kept.
    func keptAt(zone: TimeZone) async -> Date?
    func markSeen(_ card: AchievementCard) async throws
    func pull(_ stack: AchievementsPage.CeremonyStack, zone: TimeZone) async throws -> CeremonyPull
}

/// The achievements page's reads and writes (#1306): `get_achievements`,
/// `mark_achievements_seen` and `pull_ceremony` (spec §3.3, §3.4).
/// It calls, decodes and maps; the server computes. Each body that decodes is
/// kept for its owner and zone in the wraps' disk cache (`WrapDiskCache`,
/// private journal content in Caches, wiped by `SignedOutPurge`), so the next
/// visit draws at once while the read runs (stale-while-revalidate, spec §4).
/// The purge epoch is taken before the request, so a body landing after a
/// sign-out is not kept. Errors propagate; the model owns the page's state.
struct AchievementsPageService: AchievementsPageServicing {
    /// The kept body's page name in the cache.
    static let cachePage = "achievements"

    let client: SupabaseClient
    var disk: WrapDiskCache?
    /// The signed-in user's id; nothing is kept or read without one.
    var owner: String?

    func page(zone: TimeZone) async throws -> AchievementsPage {
        let epoch = disk?.epoch
        let data = try await client.rpc("get_achievements", params: ["p_tz": zone.identifier]).execute().data
        return try decodeAndKeep(data, zone: zone, epoch: epoch)
    }

    func cachedPage(zone: TimeZone) async -> AchievementsPage? {
        guard let disk, let owner, let data = disk.read(Self.cachePage, owner: owner, zone: zone.identifier) else {
            return nil
        }
        do {
            return try WrapDecoding.decode(AchievementsPayload.self, from: data).model
        } catch {
            Self.logger.error("a kept achievements page no longer decodes: \(error.localizedDescription, privacy: .private)")
            return nil
        }
    }

    func keptAt(zone: TimeZone) async -> Date? {
        guard let disk, let owner else { return nil }
        let url = disk.file(Self.cachePage, owner: owner, zone: zone.identifier)
        return (try? FileManager.default.attributesOfItem(atPath: url.path))?[.modificationDate] as? Date
    }

    func markSeen(_ card: AchievementCard) async throws {
        _ = try await client.rpc("mark_achievements_seen", params: ["p_card": card.rawValue]).execute()
    }

    func pull(_ stack: AchievementsPage.CeremonyStack, zone: TimeZone) async throws -> CeremonyPull {
        do {
            let rows: [PullRow] = try await client
                .rpc("pull_ceremony", params: ["p_stack": stack.rawValue, "p_tz": zone.identifier])
                .execute()
                .value
            return Self.outcome(rows)
        } catch let error as PostgrestError where Self.isNotReady(error.message) {
            throw CeremonyPullError.notReady
        }
    }

    /// Decodes `data`, logs any section it had to drop, and keeps the body once it decoded.
    func decodeAndKeep(_ data: Data, zone: TimeZone, epoch: Int?) throws -> AchievementsPage {
        let payload = try WrapDecoding.decode(AchievementsPayload.self, from: data)
        if !payload.skipped.isEmpty {
            Self.logger.error(
                "get_achievements sections dropped: \(payload.skipped.joined(separator: ","), privacy: .public)")
        }
        if let disk, let owner {
            disk.write(data, page: Self.cachePage, owner: owner, zone: zone.identifier, epoch: epoch)
        }
        return payload.model
    }

    /// One row of `pull_ceremony`.
    struct PullRow: Decodable, Equatable {
        let stack: String
        let karmaGranted: Int

        enum CodingKeys: String, CodingKey {
            case stack
            case karmaGranted = "karma_granted"
        }
    }

    static func outcome(_ rows: [PullRow]) -> CeremonyPull {
        rows.first.map { .pulled(karma: $0.karmaGranted) } ?? .alreadyPulled
    }

    static func isNotReady(_ message: String) -> Bool {
        message.contains("not_ready")
    }

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "achievements-page")
}
