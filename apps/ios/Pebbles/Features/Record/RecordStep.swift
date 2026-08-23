import Foundation

/// The eleven screens of the record flow, in order (D2).
///
/// The order carries three deliberate dependencies:
/// - `photo` before `when`, so the date step can arrive pre-filled from the
///   photo's EXIF `DateTimeOriginal` instead of mutating under the user.
/// - `valence` before `emotion`, so `EmotionCategoryOrdering.order(for:)` has
///   a valence to order the categories by. In the old form the two rows sat
///   side by side and the ordering depended on which the user opened first.
/// - `privacy` last, against the publish button, because the grade is the
///   decision most coupled to "am I ready for other people to see this".
///
/// `success` is terminal: no dot, no back, no close — only the exit button.
enum RecordStep: Int, CaseIterable, Identifiable, Hashable {
    case photo
    case when
    case name
    case valence
    case emotion
    case domain
    case souls
    case collection
    case glyph
    case privacy
    case success

    var id: Int { rawValue }

    /// Steps the user may pass without answering. Everything else gates.
    var isOptional: Bool {
        switch self {
        case .photo, .souls, .collection, .glyph:
            return true
        case .when, .name, .valence, .emotion, .domain, .privacy, .success:
            return false
        }
    }

    /// The steps the progress dots represent.
    static var counted: [RecordStep] {
        allCases.filter { $0 != .success }
    }

    /// 0-based dot index, or nil for the uncounted terminal step.
    var dotIndex: Int? {
        self == .success ? nil : rawValue
    }

    var next: RecordStep? { RecordStep(rawValue: rawValue + 1) }

    var previous: RecordStep? {
        rawValue == 0 ? nil : RecordStep(rawValue: rawValue - 1)
    }
}
