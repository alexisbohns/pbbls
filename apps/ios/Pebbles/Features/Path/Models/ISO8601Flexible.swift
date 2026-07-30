import Foundation

/// Parses the ISO-8601 timestamps that reach this app from surfaces that do not
/// agree on precision, and emits the one form they all accept.
///
/// Why this exists (#651): `pebble_drafts.payload` is written by three clients
/// and read back by all three, plus `updated_at` comes straight from Postgres.
/// The precisions in play are all different:
///
/// - web `new Date().toISOString()` → `2026-07-30T01:23:45.123Z` (milliseconds)
/// - Postgres `timestamptz` via PostgREST → `…T01:23:45.123456+00:00` (microseconds)
/// - iOS `.withInternetDateTime` → `2026-07-30T01:23:45Z` (whole seconds)
///
/// A single `ISO8601DateFormatter` handles exactly one of those: with
/// `.withFractionalSeconds` it rejects whole seconds, and without it, it rejects
/// both fractional forms. The original M47 code used the non-fractional formatter
/// for decoding, so every web- and Postgres-written timestamp silently became
/// `nil` and hydration fell back to "now" — quietly breaking the "publish a draft
/// verbatim" rule (design D6).
///
/// `parse` therefore tries fractional first, then whole-second. `string` emits
/// whole seconds, matching `PebbleCreatePayload` byte-for-byte so the publish
/// path is unchanged, and both other surfaces parse it happily
/// (`new Date(…)` / `OffsetDateTime.parse`).
enum ISO8601Flexible {

    private static let fractional: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private static let wholeSeconds: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    /// Returns nil only when the string is not ISO-8601 at all — never merely
    /// because of its sub-second precision.
    static func parse(_ value: String) -> Date? {
        fractional.date(from: value) ?? wholeSeconds.date(from: value)
    }

    /// Whole seconds, the form every surface reads.
    static func string(from date: Date) -> String {
        wholeSeconds.string(from: date)
    }
}
