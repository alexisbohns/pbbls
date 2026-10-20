import CoreText
import Foundation
import Testing
import UIKit
@testable import Pebbles

/// Proves the axis and the features were *applied*, not only stored: a
/// descriptor can carry a variation CoreText then ignores, so every check here
/// measures shaped output (advances, glyph ids) as well as reading attributes.
@Suite("YsabeauFace")
struct YsabeauFaceTests {

    @Test("300, 480 and 650 resolve to three distinct weights on the wght axis")
    func weightsApply() throws {
        let weights = [300, 480, 650]
        let fonts = weights.map { YsabeauFace.uiFont(size: 17, weight: $0, italic: false, features: []) }
        #expect(fonts.map(CTProbe.wght) == [300, 480, 650])
        // Ysabeau's "m" narrows as it gains weight, so the advance only proves
        // the three are distinct; the stem's ink width is what grows.
        let advances = fonts.map { CTProbe.advance("mmmm", font: $0) }
        #expect(Set(advances).count == 3, "advances did not move: \(advances)")
        let stems = try fonts.map { try CTProbe.inkWidth("l", font: $0) }
        #expect(stems[0] < stems[1], "300 → 480 did not thicken: \(stems)")
        #expect(stems[1] < stems[2], "480 → 650 did not thicken: \(stems)")
    }

    @Test("small caps substitute both lower case (smcp) and capitals (c2sc)")
    func smallCapsApply() {
        let plain = YsabeauFace.uiFont(size: 17, weight: 600, italic: false, features: [])
        let caps = YsabeauFace.uiFont(size: 17, weight: 600, italic: false, features: [.smallCaps])
        #expect(CTProbe.shapedGlyphs("abc", font: plain) != CTProbe.shapedGlyphs("abc", font: caps))
        #expect(CTProbe.shapedGlyphs("ABC", font: plain) != CTProbe.shapedGlyphs("ABC", font: caps))
    }

    @Test("tabular figures share one advance and carry lnum + tnum")
    func tabularApplies() {
        let font = YsabeauFace.uiFont(size: 17, weight: 400, italic: false, features: [.tabular])
        #expect(CTProbe.advance("1", font: font) == CTProbe.advance("0", font: font))
        let tags = CTProbe.featureTags(font)
        #expect(tags.contains("lnum"), "feature settings: \(tags)")
        #expect(tags.contains("tnum"), "feature settings: \(tags)")
    }

    @Test("lining proportional figures keep their own advances")
    func liningIsProportional() {
        let font = YsabeauFace.uiFont(size: 17, weight: 400, italic: false, features: [.lining])
        #expect(CTProbe.advance("1", font: font) != CTProbe.advance("0", font: font))
    }

    @Test("a missing face falls back to the system font without crashing")
    func missingFaceFallsBack() {
        let font = YsabeauFace.uiFont(size: 17, weight: 600, italic: false, features: [.lining],
                                      faceName: "Ysabeau-DoesNotExist")
        #expect(font.familyName == UIFont.systemFont(ofSize: 17).familyName)
        #expect(font.pointSize == 17)
    }

    @Test("one UIFont per (size, weight, italic, features)")
    func memoised() {
        let first = YsabeauFace.uiFont(size: 13, weight: 560, italic: false, features: [.tabular])
        let second = YsabeauFace.uiFont(size: 13, weight: 560, italic: false, features: [.tabular])
        #expect(first === second)
    }

    @Test("700, the axis default, reads 700 and matches the default instance")
    func defaultWeightReads() throws {
        let font = YsabeauFace.uiFont(size: 17, weight: 700, italic: false, features: [])
        #expect(CTProbe.wght(font) == 700)
        let defaultInstance = try #require(UIFont(name: YsabeauFace.romanName, size: 17))
        let ink = try CTProbe.inkWidth("l", font: font)
        #expect(abs(ink - (try CTProbe.inkWidth("l", font: defaultInstance))) < 0.01)
        let caps = YsabeauFace.uiFont(size: 17, weight: 700, italic: false, features: [.smallCaps])
        #expect(CTProbe.wght(caps) == 700)
    }

    @Test("the shared Caveat descriptor sets 700 on the axis")
    func caveatBoldDescriptor() {
        let font = UIFont.pebblesCaveat(size: 34, weight: 700)
        #expect(font.fontName.hasPrefix("Caveat"))
        #expect(CTProbe.wght(font) == 700)
    }

    @Test("one Caveat UIFont per (size, weight)")
    func caveatMemoised() {
        #expect(UIFont.pebblesCaveat(size: 44, weight: 700) === UIFont.pebblesCaveat(size: 44, weight: 700))
    }
}

// MARK: - CoreText probes

/// Measurements on shaped CoreText output, shared by the font suites.
enum CTProbe {

    /// The font's `wght`. CoreText leaves an axis at its default value (700 for
    /// Ysabeau) out of `CTFontCopyVariation`, so a missing entry on a font that
    /// has the axis reads as the axis default. Nil when the font has no `wght`.
    static func wght(_ font: UIFont) -> Double? {
        let axisID = NSNumber(value: YsabeauFace.weightAxis)
        let variation = CTFontCopyVariation(font as CTFont) as? [NSNumber: NSNumber]
        if let value = variation?[axisID] { return value.doubleValue }
        let axes = CTFontCopyVariationAxes(font as CTFont) as? [[String: Any]] ?? []
        let axis = axes.first { ($0[kCTFontVariationAxisIdentifierKey as String] as? NSNumber) == axisID }
        return (axis?[kCTFontVariationAxisDefaultValueKey as String] as? NSNumber)?.doubleValue
    }

    static func advance(_ string: String, font: UIFont) -> Double {
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: string, attributes: [.font: font]))
        return CTLineGetTypographicBounds(line, nil, nil, nil)
    }

    /// The ink width of a single glyph: grows with the stem as `wght` rises.
    static func inkWidth(_ string: String, font: UIFont) throws -> Double {
        var glyph = try #require(shapedGlyphs(string, font: font).first)
        var rect = CGRect.zero
        CTFontGetBoundingRectsForGlyphs(font as CTFont, .horizontal, &glyph, &rect, 1)
        return rect.width
    }

    static func shapedGlyphs(_ string: String, font: UIFont) -> [CGGlyph] {
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: string, attributes: [.font: font]))
        let runs = CTLineGetGlyphRuns(line) as? [CTRun] ?? []
        return runs.flatMap { run -> [CGGlyph] in
            var glyphs = [CGGlyph](repeating: 0, count: CTRunGetGlyphCount(run))
            CTRunGetGlyphs(run, CFRange(location: 0, length: 0), &glyphs)
            return glyphs
        }
    }

    static func featureTags(_ font: UIFont) -> [String] {
        let settings = CTFontCopyFeatureSettings(font as CTFont) as? [[String: Any]] ?? []
        return settings.compactMap { $0[kCTFontOpenTypeFeatureTag as String] as? String }
    }
}
