import Foundation

/// Timestamps from PostgREST arrive at whatever precision the column holds
/// (`…T08:30:00+00:00`, `…T08:31:12.517203+00:00`, `….9+00:00`), and moments
/// backfilled from pre-split pebbles carry fractional `happened_at`. Decoding
/// them as strings through `ISO8601Flexible` keeps the moment models
/// independent of whatever date strategy the ambient decoder carries
/// (standing cross-surface rule).
extension KeyedDecodingContainer {
    func decodeFlexibleDate(forKey key: Key) throws -> Date {
        let raw = try decode(String.self, forKey: key)
        guard let date = ISO8601Flexible.parse(raw) else {
            throw DecodingError.dataCorruptedError(
                forKey: key, in: self, debugDescription: "Not ISO-8601: \(raw)"
            )
        }
        return date
    }

    func decodeFlexibleDateIfPresent(forKey key: Key) throws -> Date? {
        guard let raw = try decodeIfPresent(String.self, forKey: key) else { return nil }
        guard let date = ISO8601Flexible.parse(raw) else {
            throw DecodingError.dataCorruptedError(
                forKey: key, in: self, debugDescription: "Not ISO-8601: \(raw)"
            )
        }
        return date
    }
}
