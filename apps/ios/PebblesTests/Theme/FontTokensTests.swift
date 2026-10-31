import SwiftUI
import Testing
import UIKit
@testable import Pebbles

/// #1382 D3–D4: every token resolves to a bundled family, never the system
/// rounded design, and the former SF Pro Rounded tokens are Inclusive Sans
/// ranks that scale with Dynamic Type. Theme files sit outside the literal scan
/// (`ThemeLiteralsTests`), so this suite covers the token file itself.
@Suite("Font tokens")
struct FontTokensTests {

    static let families: Set<String> = ["Inclusive Sans", "Ysabeau", "Reenie Beanie", "Caveat"]

    @Test("every token resolves to a bundled family", arguments: PebblesFont.allCases)
    func family(token: PebblesFont) {
        let family = token.resolvedUIFont(dynamicTypeSize: .large).familyName
        #expect(Self.families.contains(family), "\(token) resolved to \(family)")
    }

    struct Row: CustomTestStringConvertible, Sendable {
        let token: PebblesFont
        let size: CGFloat
        let weight: Int
        let trackingEm: CGFloat
        let uppercase: Bool
        var testDescription: String { "\(token)" }
    }

    /// Spec D3, one row per Inclusive token.
    static let table: [Row] = [
        Row(token: .body, size: 17, weight: 400, trackingEm: 0, uppercase: false),
        Row(token: .bodyEmphasized, size: 17, weight: 600, trackingEm: 0, uppercase: false),
        Row(token: .subhead, size: 15, weight: 400, trackingEm: 0, uppercase: false),
        Row(token: .subheadEmphasized, size: 15, weight: 600, trackingEm: 0, uppercase: false),
        Row(token: .headline, size: 17, weight: 600, trackingEm: 0, uppercase: false),
        Row(token: .headlineEmphasized, size: 17, weight: 700, trackingEm: 0, uppercase: false),
        Row(token: .callout, size: 16, weight: 500, trackingEm: 0, uppercase: false),
        Row(token: .calloutEmphasized, size: 16, weight: 600, trackingEm: 0, uppercase: false),
        Row(token: .meta, size: 12, weight: 500, trackingEm: 0.08, uppercase: true),
        Row(token: .metaEmphasized, size: 12, weight: 700, trackingEm: 0.08, uppercase: true),
        Row(token: .cardHeading, size: 15, weight: 600, trackingEm: 0.08, uppercase: true),
        Row(token: .cardHeadingEmphasized, size: 15, weight: 700, trackingEm: 0.08, uppercase: true),
        Row(token: .captionEmphasized, size: 12, weight: 600, trackingEm: 0, uppercase: false)
    ]

    @Test("each Inclusive token matches the spec's table", arguments: table)
    func row(_ row: Row) throws {
        let style = try #require(row.token.inclusiveStyle)
        #expect(style.face == .inclusive)
        #expect(style.size == row.size)
        #expect(style.weight == row.weight)
        #expect(style.trackingEm == row.trackingEm)
        #expect(style.isUppercase == row.uppercase)
        #expect(style.scaling == .text(SpineType.textStyle(forSize: row.size)))
    }

    @Test("only the former SF tokens are Inclusive")
    func inclusiveSet() {
        let inclusive = PebblesFont.allCases.filter { $0.inclusiveStyle != nil }
        #expect(inclusive.count == Self.table.count)
    }

    @Test("body is 17 pt at .large and stops growing at .accessibility2")
    func bodyScales() {
        #expect(PebblesFont.body.resolvedUIFont(dynamicTypeSize: .large).pointSize == 17)
        let ax2 = PebblesFont.body.resolvedUIFont(dynamicTypeSize: .accessibility2).pointSize
        let ax3 = PebblesFont.body.resolvedUIFont(dynamicTypeSize: .accessibility3).pointSize
        #expect(ax2 > 17)
        #expect(ax3 == ax2)
    }
}
