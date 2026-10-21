import CoreGraphics
import Testing
@testable import Pebbles

/// #1130 R1–R2 and §Geometry: the phone geometry, and where a row puts its stone,
/// its hit box and its floor. The web's geometry is kept: an entry's stone
/// overhangs the row's top (Android's StoneRowGeometryTest).
@Suite("SpineLayout")
struct SpineLayoutTests {
    private func near(_ a: CGFloat, _ b: CGFloat) -> Bool { abs(a - b) < 0.01 }

    @Test("stones are 34 / 44 / 54 wide and 27.2 / 44 / 64.5 tall")
    func stones() {
        #expect(SpineLayout.stoneSize(.small).width == 34 && near(SpineLayout.stoneSize(.small).height, 27.24))
        #expect(SpineLayout.stoneSize(.medium) == CGSize(width: 44, height: 44))
        #expect(SpineLayout.stoneSize(.large).width == 54 && near(SpineLayout.stoneSize(.large).height, 64.48))
    }

    @Test("the column runs from 82 to the width less 18: 293 at the 393 pt pin")
    func column() {
        #expect(SpineLayout.column(width: 393) == 293)
        #expect(SpineLayout.column(width: 390) == 290)
    }

    @Test("an entry's medium stone centres on the line at its anchor and overhangs the row")
    func entryRow() {
        let row = SpineLayout.stoneRow(anchor: 14.4, stone: SpineLayout.stoneSize(.medium))
        #expect(near(row.stone.minX, 18) && near(row.stone.minY, -7.6))
        #expect(near(row.hit.width, 64) && near(row.hit.height, 64))
        #expect(near(row.hit.midX, 40) && near(row.hit.midY, 14.4))
        #expect(near(row.minHeight, 14.4 + 22 + 14))
    }

    @Test("a small stone's hit box grows to 44 pt where stone + 10 falls short")
    func smallTarget() {
        let row = SpineLayout.stoneRow(anchor: 31, stone: SpineLayout.stoneSize(.small))
        #expect(near(row.hit.width, 54))
        #expect(near(row.hit.height, 47.24))
        let tiny = SpineLayout.stoneRow(anchor: 31, stone: CGSize(width: 10, height: 10))
        #expect(tiny.hit.width == 44 && tiny.hit.height == 44)
    }

    @Test("seals are 20 / 24 / 27")
    func seals() {
        #expect([ValenceSizeGroup.small, .medium, .large].map(SpineLayout.sealWidth) == [20, 24, 27])
    }
}
