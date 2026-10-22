import Foundation

/// How the verso's lockup paints its word (#1146 §Verso): Highlight in the
/// emotion's lit-stone ramp, Moment in `primary`, Lowlight in ink.
enum LockupInk: Equatable {
    case stoneRamp, ink, primary
}

/// The lockup word's size, stack B's `SpineType.lockupWord(_:)` group.
enum LockupRank: Equatable {
    case small, medium, large

    init(_ group: ValenceSizeGroup) {
        switch group {
        case .small: self = .small
        case .medium: self = .medium
        case .large: self = .large
        }
    }

    var sizeGroup: ValenceSizeGroup {
        switch self {
        case .small: return .small
        case .medium: return .medium
        case .large: return .large
        }
    }
}

/// The verso's top as data (#1146 §Pure functions): the record flow's lockup,
/// the word and its span from `Valence.headline`, set in stack B's lockup rank.
/// A day event's word is lowercase (`lowercase`, which the rank's
/// `isLowercase` applies). A port of Android's `ValenceLockups.of`.
struct ValenceLockup: Equatable {
    let word: LocalizedStringResource
    let lowercase: Bool
    let rank: LockupRank
    let span: LocalizedStringResource
    let ink: LockupInk
}

extension ValenceLockup {
    static func of(_ valence: Valence) -> ValenceLockup {
        let headline = valence.headline
        let ink: LockupInk
        switch valence.polarity {
        case .highlight: ink = .stoneRamp
        case .neutral: ink = .primary
        case .lowlight: ink = .ink
        }
        return ValenceLockup(word: headline.word, lowercase: valence.sizeGroup == .small,
                             rank: LockupRank(valence.sizeGroup), span: headline.span, ink: ink)
    }
}
