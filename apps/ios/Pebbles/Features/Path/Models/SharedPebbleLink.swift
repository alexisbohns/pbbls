import Foundation

/// The public share-by-link URL for a pebble (M51). Canonical host, matching
/// the invite-link convention (Connection.swift) — the uuid is the capability,
/// lowercased to match how the web app prints ids.
enum SharedPebbleLink {
    static func url(for pebbleId: UUID) -> URL {
        URL(string: "https://www.pbbls.app/p/\(pebbleId.uuidString.lowercased())")!
    }
}
