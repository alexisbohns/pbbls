import CoreGraphics

/// Where a pebble row puts its stone (#1130 R2), in the row's own coordinates
/// (origin at the row's top-left, x 0 on the page's left edge). The stone sits
/// on the line at the anchor whatever that puts above the row's top: an entry's
/// stone overhangs by up to 13.6 pt, and stays pressable because no view in a
/// day column clips (D4).
struct StoneRow: Equatable {
    let stone: CGRect
    /// The stone plus 10 all round, grown to 44 × 44 about its centre.
    let hit: CGRect
    /// The row is at least this tall, so the next pebble clears the stone.
    let minHeight: CGFloat
}

/// The Spine's phone geometry in points, from the web rule set (§Page geometry)
/// and Android's `SpineLayout`, in one place so the ground, the seams, the stones
/// and the cards agree. Text sizes are `SpineType`'s; these never scale (D11).
enum SpineLayout {
    // Page (R1)
    static let spineX: CGFloat = 40
    static let colX: CGFloat = 82
    static let padRight: CGFloat = 18
    static let dayPadTop: CGFloat = 58
    static let dayPadBottom: CGFloat = 34
    static let pebbleGap: CGFloat = 38
    static let line: CGFloat = 1
    static let feedTop: CGFloat = 48
    static let feedBottom: CGFloat = 96

    // Stones (R2)
    static let cardAnchor: CGFloat = 31
    static let stoneHitInset: CGFloat = 10
    static let minTarget: CGFloat = 44
    static let stoneClearance: CGFloat = 14

    // Seams and labels (R8–R10)
    static let seamHeight: CGFloat = 8
    /// The day label hangs this far above the hairline; `DaySeam` is overhang + seam tall.
    static let seamOverhang: CGFloat = 14
    static let labelWidth: CGFloat = 46
    static let stillPadTop: CGFloat = 14
    static let stillMinHeight: CGFloat = 48
    static let rakeTile = CGSize(width: 36, height: 240)

    // Opening (R18) and persons (R15)
    static let versoPadX: CGFloat = 20
    static let openingTop: CGFloat = 46
    static let openingTitleGap: CGFloat = 16
    static let openingSummaryGap: CGFloat = 12
    static let sectionTop: CGFloat = 40
    static let sectionHeaderGap: CGFloat = 10
    static let personRowMin: CGFloat = 44
    static let personGlyphColumn: CGFloat = 30
    static let personGlyph: CGFloat = 24
    static let personGap: CGFloat = 10
    static let dot: CGFloat = 7
    static let dotGap: CGFloat = 5
    static let foldIndent: CGFloat = 40

    // Contents (R16)
    static let contentsRowMin: CGFloat = 42
    static let contentsDayColumn: CGFloat = 40
    static let contentsSealColumn: CGFloat = 36
    static let readingDot: CGFloat = 5
    static let readingDotOffset: CGFloat = 12

    // Mouth and empty week
    static let mouthLine: CGFloat = 26
    static let emptyPadY: CGFloat = 48

    static func stoneWidth(_ size: ValenceSizeGroup) -> CGFloat {
        switch size {
        case .small: return 34
        case .medium: return 44
        case .large: return 54
        }
    }

    /// The width by size; the height from the outline's own aspect (27.2 / 44 / 64.5).
    static func stoneSize(_ size: ValenceSizeGroup) -> CGSize {
        let width = stoneWidth(size)
        return CGSize(width: width, height: width / CGFloat(PebbleOutlineGeometry.aspectRatio(for: size)))
    }

    static func sealWidth(_ size: ValenceSizeGroup) -> CGFloat {
        switch size {
        case .small: return 20
        case .medium: return 24
        case .large: return 27
        }
    }

    /// The content column: from `colX` to the width less `padRight`.
    static func column(width: CGFloat) -> CGFloat {
        width - colX - padRight
    }

    static func stoneRow(anchor: CGFloat, stone: CGSize) -> StoneRow {
        let hitWidth = max(stone.width + 2 * stoneHitInset, minTarget)
        let hitHeight = max(stone.height + 2 * stoneHitInset, minTarget)
        return StoneRow(
            stone: CGRect(x: spineX - stone.width / 2, y: anchor - stone.height / 2,
                          width: stone.width, height: stone.height),
            hit: CGRect(x: spineX - hitWidth / 2, y: anchor - hitHeight / 2, width: hitWidth, height: hitHeight),
            minHeight: anchor + stone.height / 2 + stoneClearance
        )
    }
}
