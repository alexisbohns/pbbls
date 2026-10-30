import SwiftUI
import Testing
@testable import Pebbles

/// The figures' atoms (#1306, NOTES "Seals", "Ghosts"): the flat seal's prints
/// and sizes, and the inks that make Paper and Night.
@Suite("Achievements atoms")
struct AchievementAtomsTests {
    typealias Kind = AchievementsPage.StoneKind

    @Test("a pebble prints as the Spine prints it: solid bright, tinted even, washed heavy")
    func prints() {
        #expect(FlatSeal(valence: .highlightLarge, width: 10).print == .solid)
        #expect(FlatSeal(valence: .neutralSmall, width: 10).print == .tint)
        let heavy = FlatSeal(valence: .lowlightMedium, width: 10)
        #expect(heavy.print == .wash && heavy.outline == .lowlight && heavy.size == .medium)
    }

    @Test("a seat on the emotions ring grows 1.6 pt per step of intensity from 7.4")
    func seatWidths() {
        #expect(abs(FlatSeal.seat(.neutralSmall).width - 9) < 0.0001)
        #expect(abs(FlatSeal.seat(.highlightLarge).width - 12.2) < 0.0001)
    }

    @Test("the six firsts: three sizes of the solid seal, then the three prints at medium")
    func sixFirsts() {
        #expect(Kind.allCases.map { FlatSeal.sixFirst($0).width } == [9, 12, 14, 12.5, 12.5, 12.5])
        #expect(Kind.allCases.map { FlatSeal.sixFirst($0).print } == [.solid, .solid, .solid, .wash, .tint, .solid])
        #expect(Kind.allCases.map { FlatSeal.sixFirst($0).size } == [.small, .medium, .large, .medium, .medium, .medium])
    }

    @Test("a seal's height follows its outline's aspect, and its hatch its width")
    func proportions() {
        let seal = FlatSeal(valence: .neutralMedium, width: 13)
        #expect(abs(seal.height - 13 / CGFloat(PebbleOutlineGeometry.aspectRatio(for: .medium))) < 0.0001)
        #expect(abs(seal.hatchStep - 1.5) < 0.0001)
        let hatch = FlatSeal.hatch(in: CGRect(x: 0, y: 0, width: 12, height: 12), step: 3)
        #expect(!hatch.isEmpty && hatch.boundingRect.minX <= -11)
    }

    @Test("inks: a heavy pebble takes its family's deep ink; an unknown family draws in ink")
    func inks() throws {
        let palette = try #require(SpineFixtureWeek.families.first?.palette)
        let stone = StonePalette(emotion: palette)
        let paper = AchievementInks(scheme: .light)
        let night = AchievementInks(scheme: .dark)
        #expect(paper.sealInk(palette, polarity: .lowlight) == SpineColors.deep(stone, scheme: .light))
        #expect(night.sealInk(palette, polarity: .highlight) == SpineColors.spot(stone, scheme: .dark))
        #expect(paper.sealInk(nil, polarity: .highlight) == Color.pebbles.onSurface)
        #expect(paper.hue(nil) == nil)
        #expect(paper.hue(palette) == SpineColors.spot(stone, scheme: .light))
        #expect(night.labelInk(palette, family: "joy") == palette.secondary)
        #expect(paper.labelInk(palette, family: "shame") == palette.shaded)
        #expect(paper.labelInk(palette, family: "joy") == palette.primary)
        #expect(paper.tintK == 0.15 && night.tintK == 0.18 && paper.washK == 0.09 && night.washK == 0.12)
    }

    @Test("a polygon closes on its first point")
    func polygon() {
        let triangle = Path.polygon([.zero, CGPoint(x: 4, y: 0), CGPoint(x: 0, y: 3)])
        #expect(triangle.boundingRect == CGRect(x: 0, y: 0, width: 4, height: 3))
    }
}
