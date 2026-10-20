import SwiftUI

/// The Spine's and the held card's typographic ranks (#1158), one for one with
/// Android's `SpineTypography`
/// (`apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/SpineTypography.kt`):
/// same names, sizes in points (Android's sp), weights on Ysabeau's `wght`
/// axis, tracking in em.
///
/// Feature views never name a size, a weight or a feature. They pick a rank
/// here and apply it with `.spineType(_:)`, which also owns Dynamic Type
/// (spec §B1.5): a view never scales Spine or card text itself. Never put
/// `.bold()` or `.fontWeight()` on a `spineType` text.
///
/// New patterns (raised in the B1 PR): a rank model that owns its own scaling
/// (`Style.scaling`), and `Cut` as the only logic-to-view handoff for a name's
/// weight.
enum SpineType {
    enum Face: Equatable { case ysabeau, caveat }

    enum Scaling: Equatable {
        /// Scales with the text style through `UIFontMetrics`, up to `.accessibility2`.
        case text(Font.TextStyle)
        /// Card-unit ranks: scale with the content size up to `cap`, no further.
        case card(cap: CGFloat)
        /// Sized by its caller (`RingFit`).
        case none
    }

    /// A name's cut. The Spine's `EntryType` port (#1130) builds the valence
    /// cuts; `untitled(size:)` is the untitled italic. No other struct, tuple or
    /// raw weight crosses from logic into a view.
    struct Cut: Equatable {
        var size: CGFloat
        var weight: Int
        var italic: Bool
        var trackingEm: CGFloat

        /// Android writes it inside `SpineEntry.kt` as `UNTITLED_WEIGHT`; on iOS it
        /// lives here, so no view names it.
        static let untitledWeight = 340

        static func untitled(size: CGFloat) -> Cut {
            Cut(size: size, weight: untitledWeight, italic: true, trackingEm: 0)
        }
    }

    struct Style: Equatable {
        var size: CGFloat
        var weight: Int
        var trackingEm: CGFloat = 0
        var italic: Bool = false
        var features: Set<YsabeauFeature> = []
        /// Points at the rank's size; nil is the font's natural line height.
        var lineHeight: CGFloat?
        var scaling: Scaling
        var face: Face = .ysabeau
        var isLowercase: Bool = false
        /// Horizontal room the ink needs past its advance (Caveat only).
        var inkOverhang: CGFloat = 0
    }

    static let cardCap: CGFloat = 1.3

    // MARK: - Size bands

    /// The text style a size scales with: ≤ 11.5 caption2 · 12–12.5 caption ·
    /// 13–14 footnote · 15 subheadline · 16–19 body · 20–21 title3 · 22–26 title2 ·
    /// 27–33 title · ≥ 34 largeTitle.
    static func textStyle(forSize size: CGFloat) -> Font.TextStyle {
        switch size {
        case ..<12: return .caption2
        case ..<13: return .caption
        case ..<15: return .footnote
        case ..<16: return .subheadline
        case ..<20: return .body
        case ..<22: return .title3
        case ..<27: return .title2
        case ..<34: return .title
        default:    return .largeTitle
        }
    }
}

/// The ranks live on `Style`, so a view writes `.spineType(.kicker)`.
extension SpineType.Style {
    private static let card = SpineType.Scaling.card(cap: SpineType.cardCap)

    // MARK: - Fixed ranks (the Spine)

    static let kicker = Self(size: 12, weight: 600, trackingEm: 0.2, features: [.smallCaps], scaling: .text(.caption))
    static let title = Self(size: 40, weight: 300, trackingEm: -0.026, features: [.lining], lineHeight: 40.8,
                             scaling: .text(.largeTitle))
    static let summary = Self(size: 18, weight: 380, italic: true, scaling: .text(.body))
    static let sectionLabel = Self(size: 11.5, weight: 600, trackingEm: 0.2, features: [.smallCaps], scaling: .text(.caption2))
    /// The masthead's way back a week: the body's 400; only the number is bold.
    static let masthead = Self(size: 11.5, weight: 400, trackingEm: 0.18, features: [.smallCaps], scaling: .text(.caption2))
    static let mastheadNumber = Self(size: 11.5, weight: 650, trackingEm: 0.18, features: [.smallCaps],
                                      scaling: .text(.caption2))
    static let personName = Self(size: 15, weight: 600, trackingEm: 0.1, features: [.smallCaps], scaling: .text(.subheadline))
    static let dayLetter = Self(size: 9.5, weight: 600, features: [.smallCaps], lineHeight: 12, scaling: .text(.caption2))
    static let contentsDay = Self(size: 10.5, weight: 600, trackingEm: 0.18, features: [.smallCaps], scaling: .text(.caption2))
    static let contentsTime = Self(size: 12.5, weight: 400, trackingEm: 0.04, features: [.tabular], scaling: .text(.caption))
    /// The persons' fold, "and *N* others".
    static let personsFold = Self(size: 16, weight: 380, italic: true, scaling: .text(.body))
    static let entryTime = Self(size: 13, weight: 560, trackingEm: 0.06, features: [.tabular], scaling: .text(.footnote))
    static let entryEmotion = Self(size: 12, weight: 600, trackingEm: 0.12, features: [.smallCaps], scaling: .text(.caption))
    static let people = Self(size: 15, weight: 400, lineHeight: 22.5, scaling: .text(.subheadline))
    static let soulName = Self(size: 13, weight: 620, trackingEm: 0.1, features: [.smallCaps], scaling: .text(.footnote))
    static let dayNumeral = Self(size: 25, weight: 330, trackingEm: -0.02, features: [.lining], scaling: .text(.title2))
    static let dayWeekday = Self(size: 10, weight: 600, trackingEm: 0.2, features: [.smallCaps], scaling: .text(.caption2))
    static let mouthLabel = Self(size: 12, weight: 600, trackingEm: 0.18, features: [.smallCaps], scaling: .text(.caption))
    static let mouthLine = Self(size: 16, weight: 380, italic: true, scaling: .text(.body))
    static let rollNumeral = Self(size: 14, weight: 600, trackingEm: 0.1, features: [.smallCaps, .lining],
                                   scaling: .text(.footnote))
    static let still = Self(size: 15, weight: 380, italic: true, scaling: .text(.subheadline))

    // MARK: - Ranks that take a name's cut

    static func contentsName(_ cut: SpineType.Cut) -> Self {
        Self(size: 16.5, weight: cut.weight, italic: cut.italic, lineHeight: 19.8, scaling: .text(.body))
    }

    static func entryName(_ cut: SpineType.Cut) -> Self {
        Self(size: cut.size, weight: cut.weight, trackingEm: cut.trackingEm, italic: cut.italic,
              lineHeight: cut.size * 1.12, scaling: .text(SpineType.textStyle(forSize: cut.size)))
    }

    // MARK: - Card-unit ranks (`u` in points)

    static func cardIndexNumeral(u: CGFloat) -> Self {
        let size = 5.3 * u
        return Self(size: size, weight: 600, trackingEm: -0.045, features: [.lining], lineHeight: size * 0.86, scaling: card)
    }

    static func cardIndexDay(u: CGFloat) -> Self {
        Self(size: 1.8 * u, weight: 650, trackingEm: 0.14, features: [.smallCaps], scaling: card)
    }

    static func cardRail(u: CGFloat) -> Self {
        Self(size: 1.95 * u, weight: 560, trackingEm: 0.07, features: [.tabular], scaling: card)
    }

    static func cardRailEmotion(u: CGFloat) -> Self {
        Self(size: 1.95 * u, weight: 650, trackingEm: 0.15, features: [.smallCaps], scaling: card)
    }

    static func cardTitle(u: CGFloat, cut: SpineType.Cut) -> Self {
        let size = 4.4 * u
        return Self(size: size, weight: cut.weight, trackingEm: -0.012, italic: cut.italic, lineHeight: size * 1.1, scaling: card)
    }

    static func cardSoul(u: CGFloat) -> Self {
        Self(size: 2.5 * u, weight: 650, trackingEm: 0.12, features: [.smallCaps], scaling: card)
    }

    static func cardMore(u: CGFloat) -> Self {
        Self(size: 2.5 * u, weight: 560, features: [.tabular], scaling: card)
    }

    /// The strip's "& twelve others".
    static func cardOthers(u: CGFloat) -> Self {
        Self(size: 2.6 * u, weight: 380, italic: true, scaling: card)
    }

    /// The photo-less recto's slip: the entry's cut at 6.4 card units.
    static func cardSlipName(u: CGFloat, cut: SpineType.Cut) -> Self {
        let size = 6.4 * u
        return Self(size: size, weight: cut.weight, trackingEm: -0.014, italic: cut.italic, lineHeight: size * 1.08, scaling: card)
    }

    // MARK: - The held card's fixed ranks

    static let cardFooterGrade = Self(size: 10.5, weight: 650, trackingEm: 0.16, features: [.smallCaps], scaling: .text(.caption2))
    static let cardFooterNote = Self(size: 12.5, weight: 380, italic: true, scaling: .text(.caption))
    static let cardTurnTarget = Self(size: 13, weight: 380, italic: true, scaling: .text(.footnote))
    static let cardLeafDateline = Self(size: 10.5, weight: 600, trackingEm: 0.18, features: [.smallCaps, .lining],
                                        scaling: .text(.caption2))
    static let cardLeafBody = Self(size: 17, weight: 380, lineHeight: 27, scaling: .text(.body))
    static let cardLockupSpan = Self(size: 10, weight: 650, trackingEm: 0.22, features: [.smallCaps], scaling: .text(.caption2))
    static let cardEmotion = Self(size: 20, weight: 400, scaling: .text(.title3))
    static let cardBandName = Self(size: 14, weight: 400, italic: true, scaling: .text(.footnote))
    static let bareTitle = Self(size: 34, weight: 300, trackingEm: -0.02, lineHeight: 38, scaling: .text(.largeTitle))
    static let placeBody = Self(size: 16, weight: 380, lineHeight: 24, scaling: .text(.body))

    /// The valence word at the top of the card's verso: Caveat Bold at 34 / 44 / 56
    /// by size group, lowercase for a day event, with `PebblesFont.valenceWord*`'s
    /// ink overhang. The one non-Ysabeau rank.
    static func lockupWord(_ group: ValenceSizeGroup) -> Self {
        switch group {
        case .small:
            return Self(size: 34, weight: 700, scaling: card, face: .caveat, isLowercase: true, inkOverhang: 6)
        case .medium:
            return Self(size: 44, weight: 700, scaling: card, face: .caveat, inkOverhang: 8)
        case .large:
            return Self(size: 56, weight: 700, scaling: card, face: .caveat, inkOverhang: 10)
        }
    }

    /// The postmark ring: capitals (small caps are too small at ring size), sized by `RingFit`.
    static func postmarkRing(_ size: CGFloat) -> Self {
        Self(size: size, weight: 650, features: [.lining], scaling: .none)
    }
}
