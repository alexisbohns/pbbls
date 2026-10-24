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
    /// The label's tab: 1 above the numeral, 3 between the numeral and the
    /// weekday, 5 below, its bottom corners rounded 10; a still day clears it by 4.
    static let labelPadTop: CGFloat = 1
    static let labelGap: CGFloat = 3
    static let labelPadBottom: CGFloat = 5
    static let labelRadius: CGFloat = 10
    static let labelClearance: CGFloat = 4
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
    static let foldIndent: CGFloat = 40

    // The list row's grid (`MarkRow`, the web's `30px 1fr auto`) and the day dots (#1226)
    static let rowMin: CGFloat = 44
    /// The row's leading column, and the mark (a glyph, a seal) centred in it.
    static let markColumn: CGFloat = 30
    static let mark: CGFloat = 24
    static let rowGap: CGFloat = 10
    /// A section's rule above or below a row.
    static let rule: CGFloat = 1
    /// A day dot; seven of them with their gaps are 79 pt.
    static let dot: CGFloat = 7
    static let dotGap: CGFloat = 5
    /// An empty dot's inset ring.
    static let dotRing: CGFloat = 1
    /// The month's ring of day dots: its box, its dots and the slots it leaves open at the 1st.
    static let dayCircle: CGFloat = 76
    static let dayCircleDot: CGFloat = 5
    static let dayCircleGapSlots: Double = 1.5

    // Wrap tiles and bars (#1226)
    static let tileWidth: CGFloat = 128
    /// A tile is at least this tall; Dynamic Type grows it, never its width (D11).
    static let tileMinHeight: CGFloat = 112
    static let tilePad: CGFloat = 12
    static let tileCorner: CGFloat = 12
    static let barHeight: CGFloat = 6
    static let barGap: CGFloat = 2

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

    /// The pad under a day's seam when the day has moments (maintainer decision,
    /// 2026-10-22: a day with moments, like a still day, is never shorter than
    /// its label). The spec's 50 (`dayPadTop − seamHeight`), grown only when the
    /// label's text, hung from the seam's top, would come within
    /// `labelClearance` of the first stone's outermost ring.
    ///
    /// - `labelHeight`: the whole tab (`DaySeam.labelHeight`); its text ends
    ///   `labelPadBottom` above the tab's foot, and the tab's foot sits
    ///   `labelHeight − (seamOverhang + seamHeight)` below the seam.
    /// - `ringTop`: the outermost ring's top in the first pebble row's
    ///   coordinates (negative above the row); it sits `pad + ringTop` below the seam.
    ///
    /// At `.large` this is 50 for every stone of the fixture (the tightest, the
    /// picnic's medium gem, clears by 1.7 pt); it grows from `.xxxLarge` up.
    static func seamPad(labelHeight: CGFloat, ringTop: CGFloat) -> CGFloat {
        let text = labelHeight - labelPadBottom
        let reach = max(0, -ringTop)
        return max(dayPadTop - seamHeight, text + labelClearance - (seamOverhang + seamHeight) + reach)
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
