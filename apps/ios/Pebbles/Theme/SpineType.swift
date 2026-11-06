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
    enum Face: Equatable {
        case ysabeau, caveat, inclusive

        /// The heaviest weight Bold Text may reach: the spec's 800 for Ysabeau
        /// (its axis runs to 1000), Caveat's and Inclusive Sans's axis maximum, 700.
        var maxWeight: Int {
            switch self {
            case .ysabeau:   return 800
            case .caveat:    return 700
            case .inclusive: return 700
            }
        }
    }

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
        /// Capitals, set by the modifier (the Inclusive caps tokens, #1382 D3).
        var isUppercase: Bool = false
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

// The rank table keeps one rank per line, so it reads as the spec's table.
// swiftlint:disable line_length
/// The ranks live on `Style`, so a view writes `.spineType(.kicker)`.
extension SpineType.Style {
    private static let card = SpineType.Scaling.card(cap: SpineType.cardCap)

    // MARK: - Fixed ranks (the Spine)

    static let kicker = Self(size: 12, weight: 600, trackingEm: 0.2, features: [.smallCaps], scaling: .text(.caption))
    static let title = Self(size: 40, weight: 300, trackingEm: -0.026, features: [.lining], lineHeight: 40.8,
                            scaling: .text(.largeTitle))
    static let summary = Self(size: 18, weight: 380, italic: true, scaling: .text(.body))
    static let sectionLabel = Self(size: 11.5, weight: 600, trackingEm: 0.2, features: [.smallCaps], scaling: .text(.caption2))
    /// A section's italic hint beside its label (#1226), "from the 1st, clockwise".
    static let sectionHint = Self(size: 13, weight: 380, italic: true, scaling: .text(.footnote))
    /// A wrap tile's caption (#1226): the board's `.tile .sm`, and its italic.
    static let tileCaption = Self(size: 12.5, weight: 400, lineHeight: 15.6, scaling: .text(.caption))
    static let tileCaptionItalic = Self(size: 12.5, weight: 400, italic: true, lineHeight: 15.6, scaling: .text(.caption))
    /// The month's name inside the garland (#1226): the round-2 board's `.mo-name`.
    static let monthName = Self(size: 36, weight: 380, italic: true, lineHeight: 40, scaling: .text(.largeTitle))
    /// A sheet list's opening sentence (#1226): the board's `.lead`.
    static let lead = Self(size: 17, weight: 380, italic: true, lineHeight: 23, scaling: .text(.body))
    /// The masthead's way back a week: the body's 400; only the number is bold.
    static let masthead = Self(size: 11.5, weight: 400, trackingEm: 0.18, features: [.smallCaps], scaling: .text(.caption2))
    static let mastheadNumber = Self(size: 11.5, weight: 650, trackingEm: 0.18, features: [.smallCaps],
                                     scaling: .text(.caption2))
    /// A Home checklist row's title and the claim label: the 17 pt semibold the
    /// headline carried, now scaling with Dynamic Type like the italic lines beside it.
    static let checklistTitle = Self(size: 17, weight: 600, scaling: .text(.body))
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

    // MARK: - Home and You (#1382)

    // Ysabeau: the editorial voice (D22). Figures are always Ysabeau, lining
    // where a number stands alone; the dates keep the face's old-style default.
    static let homeDateline = Self(size: 17, weight: 540, scaling: .text(.body))
    static let greeting = Self(size: 40, weight: 270, trackingEm: -0.024, lineHeight: 41.6, scaling: .text(.largeTitle))
    static let greetingLine = Self(size: 21, weight: 360, italic: true, scaling: .text(.title3))
    static let sectionTitle = Self(size: 30, weight: 300, trackingEm: -0.018, scaling: .text(.title))
    static let sectionFigure = Self(size: 30, weight: 300, features: [.lining], scaling: .text(.title))
    static let sectionFigureWord = Self(size: 19, weight: 380, italic: true, scaling: .text(.body))
    static let wrapCount = Self(size: 34, weight: 280, features: [.lining], scaling: .text(.largeTitle))
    static let wrapMonth = Self(size: 23, weight: 400, scaling: .text(.title2))
    static let wrapYear = Self(size: 16, weight: 400, italic: true, scaling: .text(.body))
    static let counterFigure = Self(size: 36, weight: 280, trackingEm: -0.025, features: [.lining], scaling: .text(.largeTitle))
    static let memberSince = Self(size: 19, weight: 380, italic: true, scaling: .text(.body))
    static let labKicker = Self(size: 15, weight: 400, italic: true, scaling: .text(.subheadline))
    static let labTitle = Self(size: 26, weight: 340, scaling: .text(.title2))
    static let labDate = Self(size: 15, weight: 500, scaling: .text(.subheadline))
    static let communityTitle = Self(size: 22, weight: 420, scaling: .text(.title2))

    // Inclusive Sans: every row you read (D22). Labels at 12 or more, body at 15 or more.
    static let actionLabel = Self(size: 15, weight: 600, lineHeight: 17.25, scaling: .text(.subheadline), face: .inclusive)
    static let rowTitle = Self.inclusive(17, 600)
    static let rowTitleDone = Self.inclusive(17, 500)
    static let rowLine = Self(size: 15, weight: 400, lineHeight: 21, scaling: .text(.subheadline), face: .inclusive)
    static let rowLabel = Self.inclusive(15, 600)
    static let badgeLabel = Self.inclusive(12.5, 500)
    static let labEntry = Self.inclusive(16, 600)
    static let labEntryLine = Self.inclusive(14, 400)
    static let pillLabel = Self.inclusive(15, 600)

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
// swiftlint:enable line_length
