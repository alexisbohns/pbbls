import Foundation

/// The Complete flow's screens (record-flow spec D1): three questions, then
/// the end screen. Nothing is optional: souls and the collection live on the
/// life step as extras, not as steps of their own. No glyph step: a pebble's
/// glyph is derived from its collection or domain (D5).
enum CompleteStep: Int, CaseIterable, Identifiable, Hashable {
    case valence
    case emotion
    case life
    case success

    var id: Int { rawValue }

    static var counted: [CompleteStep] { allCases.filter { $0 != .success } }

    var dotIndex: Int? { self == .success ? nil : rawValue }

    var next: CompleteStep? { CompleteStep(rawValue: rawValue + 1) }

    var previous: CompleteStep? { rawValue == 0 ? nil : CompleteStep(rawValue: rawValue - 1) }
}
