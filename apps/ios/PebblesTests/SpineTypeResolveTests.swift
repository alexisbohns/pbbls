import SwiftUI
import Testing
import CoreText
import UIKit
@testable import Pebbles

@Suite("SpineType · Dynamic Type and Bold Text")
struct SpineTypeResolveTests {

    private func size(_ style: SpineType.Style, _ dynamicType: DynamicTypeSize) -> CGFloat {
        SpineType.resolve(style, dynamicTypeSize: dynamicType, legibilityWeight: nil).size
    }

    private let ax2Traits = UITraitCollection(preferredContentSizeCategory: .accessibilityLarge)
    private let cut = SpineType.Cut(size: 19, weight: 480, italic: false, trackingEm: 0)

    @Test(".accessibility2 is UIKit's accessibilityLarge")
    func categoryMapping() {
        #expect(SpineType.contentSizeCategory(.large) == .large)
        #expect(SpineType.contentSizeCategory(.accessibility2) == .accessibilityLarge)
        #expect(SpineType.contentSizeCategory(.accessibility5) == .accessibilityExtraExtraExtraLarge)
    }

    @Test("kicker is 12 pt at .large and follows caption up to .accessibility2")
    func kickerScales() {
        #expect(size(.kicker, .large) == 12)
        let expected = UIFontMetrics(forTextStyle: .caption1).scaledValue(for: 12, compatibleWith: ax2Traits)
        #expect(size(.kicker, .accessibility2) == expected)
        #expect(expected > 12)
    }

    @Test(".accessibility5 resolves to the .accessibility2 size")
    func textCapsAtAx2() {
        #expect(size(.kicker, .accessibility5) == size(.kicker, .accessibility2))
        #expect(size(.cardLeafBody, .accessibility5) == size(.cardLeafBody, .accessibility2))
    }

    @Test("card ranks stop at ×1.3")
    func cardCap() {
        let title = SpineType.Style.cardTitle(u: 4, cut: cut)
        let large = size(title, .large)
        #expect(abs(size(title, .accessibility2) - large * 1.3) < 0.0001)
        #expect(abs(size(title, .accessibility5) - large * 1.3) < 0.0001)
        let lockup = SpineType.Style.lockupWord(.large)
        #expect(abs(size(lockup, .accessibility2) - 56 * 1.3) < 0.0001)
    }

    @Test("postmarkRing never scales", arguments: DynamicTypeSize.allCases)
    func ringIsFixed(dynamicType: DynamicTypeSize) {
        #expect(size(.postmarkRing(8), dynamicType) == 8)
    }

    @Test("Bold Text adds 100 to the axis and caps at 800")
    func boldText() {
        #expect(SpineType.resolve(.kicker, dynamicTypeSize: .large, legibilityWeight: .bold).weight == 700)
        #expect(SpineType.resolve(.kicker, dynamicTypeSize: .large, legibilityWeight: .regular).weight == 600)
        var heavy = SpineType.Style.kicker
        heavy.weight = 750
        #expect(SpineType.resolve(heavy, dynamicTypeSize: .large, legibilityWeight: .bold).weight == 800)
        let resolved = SpineType.resolve(.kicker, dynamicTypeSize: .large, legibilityWeight: .bold)
        #expect(CTProbe.wght(resolved.uiFont) == 700)
    }

    @Test("tracking is em × resolved size; line spacing is the line height less the font's")
    func trackingAndLineSpacing() {
        let kicker = SpineType.resolve(.kicker, dynamicTypeSize: .large, legibilityWeight: nil)
        #expect(abs(kicker.tracking - 2.4) < 0.0001)
        #expect(kicker.lineSpacing == 0)
        let title = SpineType.resolve(.title, dynamicTypeSize: .large, legibilityWeight: nil)
        #expect(abs(title.lineSpacing - (40.8 - title.uiFont.lineHeight)) < 0.0001)
        #expect(title.lineSpacing < 0, "title asks for less than Ysabeau's natural 1.343 em")
    }

    @Test("Bold Text never pushes Caveat past its axis maximum, 700")
    func boldTextCapsCaveat() throws {
        let resolved = SpineType.resolve(.lockupWord(.large), dynamicTypeSize: .large, legibilityWeight: .bold)
        #expect(resolved.weight == 700)
        #expect(CTProbe.wght(resolved.uiFont) == 700)
        let axes = CTFontCopyVariationAxes(resolved.uiFont as CTFont) as? [[String: Any]] ?? []
        let axis = try #require(axes.first {
            ($0[kCTFontVariationAxisIdentifierKey as String] as? NSNumber)?.intValue == YsabeauFace.weightAxis
        })
        let maximum = try #require(axis[kCTFontVariationAxisMaximumValueKey as String] as? NSNumber)
        #expect(SpineType.Face.caveat.maxWeight == maximum.intValue)
    }

    @Test("lockupWord resolves through the Caveat descriptor at 700")
    func lockupFace() {
        let resolved = SpineType.resolve(.lockupWord(.medium), dynamicTypeSize: .large, legibilityWeight: nil)
        #expect(resolved.uiFont.fontName.hasPrefix("Caveat"))
        #expect(CTProbe.wght(resolved.uiFont) == 700)
    }

    /// Spec §Risks: iOS 17 `Text` has no line-height API, so a tight rank relies
    /// on a negative `lineSpacing`. Two lines of `title` must stack at the font's
    /// natural line height plus 40.8 pt (≈ 40 × 1.343 + 40.8 = 94.5 pt).
    @Test("probe: a two-line title stacks at natural + 40.8 pt")
    @MainActor
    func titleLineHeightProbe() throws {
        let view = Text(verbatim: "Mmm\nMmm").spineType(.title).fixedSize()
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let height = try #require(renderer.uiImage).size.height
        let natural = YsabeauFace.uiFont(size: 40, weight: 300, italic: false, features: [.lining]).lineHeight
        // Measured on iOS 26.3 (iPhone 17 simulator): 108.0 pt, two natural lines.
        withKnownIssue("iOS clamps negative lineSpacing; #1130 decides natural spacing or per-line layout") {
            #expect(abs(height - (natural + 40.8)) <= 1, "measured \(height) pt, natural line \(natural) pt")
        }
    }
}
