import CoreGraphics

/// The achievements page's type ranks (#1306), from the boards' `style.css` and
/// `sheets.css`, on the Spine's Ysabeau so Dynamic Type and Bold Text apply.
/// Ysabeau Office's lining figures are the `.lining` feature. A figure's labels
/// do not scale (`.none`): they sit at fixed places in a drawing.
extension SpineType.Style {
    // MARK: The header

    static let achievementsSince = Self(size: 11.5, weight: 600, trackingEm: 0.2, features: [.smallCaps],
                                        scaling: .text(.caption2))
    static let achievementsDays = Self(size: 78, weight: 230, trackingEm: -0.025, features: [.lining],
                                       scaling: .text(.largeTitle))
    static let achievementsDaysUnit = Self(size: 15, weight: 560, trackingEm: 0.16, features: [.smallCaps],
                                           scaling: .text(.subheadline))

    // MARK: The cards

    static let achievementsMarginTitle = Self(size: 11, weight: 600, trackingEm: 0.26, features: [.smallCaps],
                                              scaling: .text(.caption2))
    static let achievementsBand = Self(size: 11.5, weight: 600, trackingEm: 0.22, features: [.smallCaps],
                                       scaling: .text(.caption2))
    static let achievementsBandHint = Self(size: 14, weight: 380, italic: true, scaling: .text(.footnote))
    static let achievementsLine = Self(size: 16, weight: 380, italic: true, lineHeight: 21.6, scaling: .text(.body))
    static let achievementsSubLabel = Self(size: 11, weight: 600, trackingEm: 0.14, features: [.smallCaps],
                                           scaling: .text(.caption2))
    static let achievementsSubCount = Self(size: 20, weight: 300, features: [.lining], scaling: .text(.title3))

    // MARK: The sheets

    static let achievementsLead = Self(size: 16.5, weight: 380, italic: true, lineHeight: 22.3, scaling: .text(.body))
    static let achievementsSectionTitle = Self(size: 11.5, weight: 600, trackingEm: 0.22, features: [.smallCaps],
                                               scaling: .text(.caption2))
    static let achievementsSectionNote = Self(size: 13.5, weight: 380, italic: true, scaling: .text(.footnote))
    static let achievementsRowName = Self(size: 13, weight: 600, trackingEm: 0.1, features: [.smallCaps],
                                          scaling: .text(.footnote))
    static let achievementsRowSub = Self(size: 14.5, weight: 420, scaling: .text(.subheadline))
    static let achievementsRowNext = Self(size: 14.5, weight: 400, italic: true, scaling: .text(.subheadline))
    static let achievementsRowDate = Self(size: 12.5, weight: 400, features: [.lining], scaling: .text(.caption))
    static let achievementsRowNumber = Self(size: 21, weight: 300, features: [.lining], scaling: .text(.title3))
    static let achievementsRowNumberSmall = Self(size: 17, weight: 300, features: [.lining], scaling: .text(.body))
    static let achievementsRegisterNumber = Self(size: 12.5, weight: 400, trackingEm: 0.03, features: [.lining],
                                                 scaling: .text(.caption))
    static let achievementsGhostName = Self(size: 15, weight: 400, scaling: .text(.subheadline))
    static let achievementsQuiet = Self(size: 14.5, weight: 380, italic: true, scaling: .text(.subheadline))
    static let achievementsColumn = Self(size: 10.5, weight: 600, trackingEm: 0.14, features: [.smallCaps],
                                         scaling: .text(.caption2))
    static let achievementsCell = Self(size: 14, weight: 400, features: [.tabular], scaling: .text(.footnote))
    static let achievementsCellDone = Self(size: 14, weight: 560, features: [.tabular], scaling: .text(.footnote))

    // MARK: The figures

    static let achievementsRosaceFamily = Self(size: 11, weight: 600, trackingEm: 0.14, features: [.smallCaps],
                                               scaling: .none)
    static let achievementsRosaceCount = Self(size: 28, weight: 300, features: [.lining], scaling: .none)
    static let achievementsRosaceOf = Self(size: 11, weight: 600, trackingEm: 0.2, features: [.smallCaps],
                                           scaling: .none)

    /// The year disc's numerals, sized with the disc; a rim month's is emphasised.
    static func achievementsNumeral(_ size: CGFloat, emphasized: Bool) -> Self {
        Self(size: size, weight: emphasized ? 600 : 400, scaling: .none)
    }
}
