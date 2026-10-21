import Foundation

/// Why karma was earned. Only the two iOS call sites that exist today —
/// creating and enriching a pebble. Web has more (grant/purchase/refund);
/// add here when a real iOS caller lands (YAGNI).
enum KarmaReason: String, Sendable, Codable, CaseIterable {
    case pebbleCreated
    case pebbleEnriched

    /// User-facing label, localized. French says galet (#1101):
    /// "Galet créé" / "Galet enrichi".
    var label: LocalizedStringResource {
        switch self {
        case .pebbleCreated:  "Pebble created"
        case .pebbleEnriched: "Pebble enriched"
        }
    }
}
