import Foundation
import os

/// A lossy Decodable wrapper around `[Log]`.
///
/// `LogsService` decodes every `v_logs_with_counts` response through this
/// wrapper so that one un-decodable row cannot break an entire feed. Each
/// skipped row writes one `error`-level log line naming its index and the
/// underlying `DecodingError` coding path, so the next occurrence is
/// self-diagnosing from Xcode Console or an `os_log` export.
///
/// The happy path — all rows decode — produces no new log output and yields
/// the full list exactly as a plain `[Log]` decode would.
struct LossyLogArray: Decodable {
    let logs: [Log]

    init(from decoder: Decoder) throws {
        var container = try decoder.unkeyedContainer()
        var accumulated: [Log] = []
        if let count = container.count { accumulated.reserveCapacity(count) }
        var index = 0
        while !container.isAtEnd {
            do {
                accumulated.append(try container.decode(Log.self))
            } catch {
                Self.logger.error(
                    "skipped log row \(index, privacy: .public): \(String(reflecting: error), privacy: .public)"
                )
                // UnkeyedDecodingContainer does not advance `currentIndex`
                // when decode(_:) throws — without this swallow, the while
                // loop would spin forever on the first bad row.
                _ = try? container.decode(AnyDecodable.self)
            }
            index += 1
        }
        self.logs = accumulated
    }

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "logs-service")
}

/// Consumes any JSON value (object, array, primitive, null) without
/// inspecting it, so the parent UnkeyedDecodingContainer advances past
/// the bad element.
private struct AnyDecodable: Decodable {
    init(from decoder: Decoder) throws {
        _ = try decoder.singleValueContainer()
    }
}
