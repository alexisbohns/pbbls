import Foundation

/// A consent act the gate can ask for, by its `user_consents.kind` wire value.
/// Mirrors Android's `core/model/Consent.kt`.
enum ConsentKind: String, CaseIterable, Sendable {
    case terms
    case privacy
    case healthData = "health_data"
    case ageAssurance = "age_assurance"
}

/// One active (neither withdrawn nor superseded) `user_consents` row, projected
/// to the two columns the gate reads. `kind` stays a String: the ledger holds
/// kinds this app does not ask for (`public_profile`), and those must decode.
/// No timestamp is selected, so no cross-surface date decoding is involved.
struct ActiveConsent: Decodable, Equatable, Sendable {
    let kind: String
    let documentVersion: String

    enum CodingKeys: String, CodingKey {
        case kind
        case documentVersion = "document_version"
    }
}
