import Foundation
import os

/// `people_prompts` (spec §4.4, D10): the server's totals and the pebbles it
/// asks about — the newest ones with nobody on them. The server decides what
/// is askable; the client only renders.
struct PeoplePrompts: Hashable {
    struct Prompt: Hashable, Identifiable {
        let pebbleId: UUID
        let momentId: UUID
        let name: String?
        let happenedAt: Date
        let intensity: Int
        let positiveness: Int
        let emotionId: UUID?

        var id: UUID { pebbleId }
        var valence: Valence { Valence.resolved(positiveness: positiveness, intensity: intensity) }
    }

    let pebbles: Int
    let souls: Int
    let prompts: [Prompt]
    /// Names of the pebbles the server left out, newest first (shown as "skipped").
    let skipped: [String]
}

/// `people_prompts`, as the server sends it. Lossy on `prompts` as
/// `HomeWrapsPayload` is on months: a malformed row fails that one prompt,
/// never the tab. `skipped` names arrive as `[{name}]`.
struct PeoplePromptsPayload: Decodable {
    let model: PeoplePrompts
    /// How many prompts the client could not read, so a shape drift is noticed.
    let dropped: Int

    private enum CodingKeys: String, CodingKey { case pebbles, souls, prompts, skipped }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let rows: LossyPromptArray = try c.decode(.prompts, or: LossyPromptArray(prompts: [], dropped: 0))
        let skipped: [SkippedName] = try c.decode(.skipped, or: [])
        model = PeoplePrompts(
            pebbles: try c.decode(.pebbles, or: 0),
            souls: try c.decode(.souls, or: 0),
            prompts: rows.prompts,
            skipped: skipped.map(\.name)
        )
        dropped = rows.dropped
    }
}

private struct SkippedName: Decodable {
    let name: String
}

private struct PromptPayload: Decodable {
    let model: PeoplePrompts.Prompt

    private enum CodingKeys: String, CodingKey {
        case name, intensity, positiveness
        case pebbleId = "pebble_id"
        case momentId = "moment_id"
        case emotionId = "emotion_id"
        case happenedAt = "happened_at"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        model = PeoplePrompts.Prompt(
            pebbleId: try c.decode(UUID.self, forKey: .pebbleId),
            momentId: try c.decode(UUID.self, forKey: .momentId),
            name: try c.decodeIfPresent(String.self, forKey: .name),
            // Tolerant: any ISO-8601 precision (standing cross-surface rule).
            happenedAt: try c.decodeFlexibleDate(forKey: .happenedAt),
            intensity: try c.decode(Int.self, forKey: .intensity),
            positiveness: try c.decode(Int.self, forKey: .positiveness),
            emotionId: try c.decodeIfPresent(UUID.self, forKey: .emotionId)
        )
    }
}

/// One bad row must not hide the prompts: skipped rows log once each and are
/// counted, as `LossyGuideArray` does.
private struct LossyPromptArray: Decodable {
    let prompts: [PeoplePrompts.Prompt]
    let dropped: Int

    init(prompts: [PeoplePrompts.Prompt], dropped: Int) {
        self.prompts = prompts
        self.dropped = dropped
    }

    init(from decoder: Decoder) throws {
        var container = try decoder.unkeyedContainer()
        var accumulated: [PeoplePrompts.Prompt] = []
        var dropped = 0
        if let count = container.count { accumulated.reserveCapacity(count) }
        var index = 0
        while !container.isAtEnd {
            do {
                accumulated.append(try container.decode(PromptPayload.self).model)
            } catch {
                Self.logger.error(
                    "skipped prompt row \(index, privacy: .public): \(String(reflecting: error), privacy: .public)"
                )
                // A throwing decode does not advance the container; swallow the element.
                _ = try? container.decode(SkippedElement.self)
                dropped += 1
            }
            index += 1
        }
        self.prompts = accumulated
        self.dropped = dropped
    }

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "people.prompts")
}

private struct SkippedElement: Decodable {
    init(from decoder: Decoder) throws {
        _ = try decoder.singleValueContainer()
    }
}
