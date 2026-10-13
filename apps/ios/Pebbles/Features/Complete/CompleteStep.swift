import Foundation

/// The Complete flow's screens (spec §6, A1). No glyph step: a pebble's glyph
/// is derived from its collection or domain (D5).
enum CompleteStep: Int, CaseIterable, Identifiable, Hashable {
    case valence
    case emotion
    case domain
    case souls
    case collection
    case privacy
    case success

    var id: Int { rawValue }

    var isOptional: Bool {
        switch self {
        case .souls, .collection: return true
        case .valence, .emotion, .domain, .privacy, .success: return false
        }
    }

    static var counted: [CompleteStep] { allCases.filter { $0 != .success } }

    var dotIndex: Int? { self == .success ? nil : rawValue }

    var next: CompleteStep? { CompleteStep(rawValue: rawValue + 1) }

    var previous: CompleteStep? { rawValue == 0 ? nil : CompleteStep(rawValue: rawValue - 1) }
}
