import SwiftUI
import Testing
@testable import Pebbles

@Suite("SpineType · rank table")
struct SpineTypeTests {

    struct Row {
        let name: String
        let actual: SpineType.Style
        let expected: SpineType.Style
    }

    typealias S = SpineType.Style
    static let u: CGFloat = 4
    static let cut = SpineType.Cut(size: 19, weight: 480, italic: false, trackingEm: 0.01)
    static let italicCut = SpineType.Cut(size: 15, weight: 360, italic: true, trackingEm: 0)
    static let sc: Set<YsabeauFeature> = [.smallCaps]
    static let lin: Set<YsabeauFeature> = [.lining]
    static let tab: Set<YsabeauFeature> = [.tabular]
    static let card = SpineType.Scaling.card(cap: 1.3)

    // swiftlint:disable line_length
    static let rows: [Row] = [
        Row(name: "kicker", actual: SpineType.kicker, expected: S(size: 12, weight: 600, trackingEm: 0.2, features: sc, scaling: .text(.caption))),
        Row(name: "title", actual: SpineType.title, expected: S(size: 40, weight: 300, trackingEm: -0.026, features: lin, lineHeight: 40.8, scaling: .text(.largeTitle))),
        Row(name: "summary", actual: SpineType.summary, expected: S(size: 18, weight: 380, italic: true, scaling: .text(.body))),
        Row(name: "sectionLabel", actual: SpineType.sectionLabel, expected: S(size: 11.5, weight: 600, trackingEm: 0.2, features: sc, scaling: .text(.caption2))),
        Row(name: "masthead", actual: SpineType.masthead, expected: S(size: 11.5, weight: 400, trackingEm: 0.18, features: sc, scaling: .text(.caption2))),
        Row(name: "mastheadNumber", actual: SpineType.mastheadNumber, expected: S(size: 11.5, weight: 650, trackingEm: 0.18, features: sc, scaling: .text(.caption2))),
        Row(name: "personName", actual: SpineType.personName, expected: S(size: 15, weight: 600, trackingEm: 0.1, features: sc, scaling: .text(.subheadline))),
        Row(name: "dayLetter", actual: SpineType.dayLetter, expected: S(size: 9.5, weight: 600, features: sc, lineHeight: 12, scaling: .text(.caption2))),
        Row(name: "contentsDay", actual: SpineType.contentsDay, expected: S(size: 10.5, weight: 600, trackingEm: 0.18, features: sc, scaling: .text(.caption2))),
        Row(name: "contentsTime", actual: SpineType.contentsTime, expected: S(size: 12.5, weight: 400, trackingEm: 0.04, features: tab, scaling: .text(.caption))),
        Row(name: "personsFold", actual: SpineType.personsFold, expected: S(size: 16, weight: 380, italic: true, scaling: .text(.body))),
        Row(name: "entryTime", actual: SpineType.entryTime, expected: S(size: 13, weight: 560, trackingEm: 0.06, features: tab, scaling: .text(.footnote))),
        Row(name: "entryEmotion", actual: SpineType.entryEmotion, expected: S(size: 12, weight: 600, trackingEm: 0.12, features: sc, scaling: .text(.caption))),
        Row(name: "people", actual: SpineType.people, expected: S(size: 15, weight: 400, lineHeight: 22.5, scaling: .text(.subheadline))),
        Row(name: "soulName", actual: SpineType.soulName, expected: S(size: 13, weight: 620, trackingEm: 0.1, features: sc, scaling: .text(.footnote))),
        Row(name: "dayNumeral", actual: SpineType.dayNumeral, expected: S(size: 25, weight: 330, trackingEm: -0.02, features: lin, scaling: .text(.title2))),
        Row(name: "dayWeekday", actual: SpineType.dayWeekday, expected: S(size: 10, weight: 600, trackingEm: 0.2, features: sc, scaling: .text(.caption2))),
        Row(name: "mouthLabel", actual: SpineType.mouthLabel, expected: S(size: 12, weight: 600, trackingEm: 0.18, features: sc, scaling: .text(.caption))),
        Row(name: "mouthLine", actual: SpineType.mouthLine, expected: S(size: 16, weight: 380, italic: true, scaling: .text(.body))),
        Row(name: "rollNumeral", actual: SpineType.rollNumeral, expected: S(size: 14, weight: 600, trackingEm: 0.1, features: [.smallCaps, .lining], scaling: .text(.footnote))),
        Row(name: "still", actual: SpineType.still, expected: S(size: 15, weight: 380, italic: true, scaling: .text(.subheadline))),
        Row(name: "contentsName", actual: SpineType.contentsName(cut), expected: S(size: 16.5, weight: 480, lineHeight: 19.8, scaling: .text(.body))),
        Row(name: "contentsName·italic", actual: SpineType.contentsName(italicCut), expected: S(size: 16.5, weight: 360, italic: true, lineHeight: 19.8, scaling: .text(.body))),
        Row(name: "entryName", actual: SpineType.entryName(cut), expected: S(size: 19, weight: 480, trackingEm: 0.01, lineHeight: 19 * 1.12, scaling: .text(.body))),
        Row(name: "entryName·italic", actual: SpineType.entryName(italicCut), expected: S(size: 15, weight: 360, italic: true, lineHeight: 15 * 1.12, scaling: .text(.subheadline))),
        Row(name: "entryName·untitled", actual: SpineType.entryName(.untitled(size: 22)), expected: S(size: 22, weight: 340, italic: true, lineHeight: 22 * 1.12, scaling: .text(.title2))),
        Row(name: "cardIndexNumeral", actual: SpineType.cardIndexNumeral(u: u), expected: S(size: 5.3 * u, weight: 600, trackingEm: -0.045, features: lin, lineHeight: 5.3 * u * 0.86, scaling: card)),
        Row(name: "cardIndexDay", actual: SpineType.cardIndexDay(u: u), expected: S(size: 1.8 * u, weight: 650, trackingEm: 0.14, features: sc, scaling: card)),
        Row(name: "cardRail", actual: SpineType.cardRail(u: u), expected: S(size: 1.95 * u, weight: 560, trackingEm: 0.07, features: tab, scaling: card)),
        Row(name: "cardRailEmotion", actual: SpineType.cardRailEmotion(u: u), expected: S(size: 1.95 * u, weight: 650, trackingEm: 0.15, features: sc, scaling: card)),
        Row(name: "cardTitle", actual: SpineType.cardTitle(u: u, cut: cut), expected: S(size: 4.4 * u, weight: 480, trackingEm: -0.012, lineHeight: 4.4 * u * 1.1, scaling: card)),
        Row(name: "cardTitle·italic", actual: SpineType.cardTitle(u: u, cut: italicCut), expected: S(size: 4.4 * u, weight: 360, trackingEm: -0.012, italic: true, lineHeight: 4.4 * u * 1.1, scaling: card)),
        Row(name: "cardSoul", actual: SpineType.cardSoul(u: u), expected: S(size: 2.5 * u, weight: 650, trackingEm: 0.12, features: sc, scaling: card)),
        Row(name: "cardMore", actual: SpineType.cardMore(u: u), expected: S(size: 2.5 * u, weight: 560, features: tab, scaling: card)),
        Row(name: "cardOthers", actual: SpineType.cardOthers(u: u), expected: S(size: 2.6 * u, weight: 380, italic: true, scaling: card)),
        Row(name: "cardSlipName", actual: SpineType.cardSlipName(u: u, cut: cut), expected: S(size: 6.4 * u, weight: 480, trackingEm: -0.014, lineHeight: 6.4 * u * 1.08, scaling: card)),
        Row(name: "cardSlipName·italic", actual: SpineType.cardSlipName(u: u, cut: italicCut), expected: S(size: 6.4 * u, weight: 360, trackingEm: -0.014, italic: true, lineHeight: 6.4 * u * 1.08, scaling: card)),
        Row(name: "cardFooterGrade", actual: SpineType.cardFooterGrade, expected: S(size: 10.5, weight: 650, trackingEm: 0.16, features: sc, scaling: .text(.caption2))),
        Row(name: "cardFooterNote", actual: SpineType.cardFooterNote, expected: S(size: 12.5, weight: 380, italic: true, scaling: .text(.caption))),
        Row(name: "cardTurnTarget", actual: SpineType.cardTurnTarget, expected: S(size: 13, weight: 380, italic: true, scaling: .text(.footnote))),
        Row(name: "cardLeafDateline", actual: SpineType.cardLeafDateline, expected: S(size: 10.5, weight: 600, trackingEm: 0.18, features: [.smallCaps, .lining], scaling: .text(.caption2))),
        Row(name: "cardLeafBody", actual: SpineType.cardLeafBody, expected: S(size: 17, weight: 380, lineHeight: 27, scaling: .text(.body))),
        Row(name: "cardLockupSpan", actual: SpineType.cardLockupSpan, expected: S(size: 10, weight: 650, trackingEm: 0.22, features: sc, scaling: .text(.caption2))),
        Row(name: "cardEmotion", actual: SpineType.cardEmotion, expected: S(size: 20, weight: 400, scaling: .text(.title3))),
        Row(name: "cardBandName", actual: SpineType.cardBandName, expected: S(size: 14, weight: 400, italic: true, scaling: .text(.footnote))),
        Row(name: "bareTitle", actual: SpineType.bareTitle, expected: S(size: 34, weight: 300, trackingEm: -0.02, lineHeight: 38, scaling: .text(.largeTitle))),
        Row(name: "placeBody", actual: SpineType.placeBody, expected: S(size: 16, weight: 380, lineHeight: 24, scaling: .text(.body))),
        Row(name: "lockupWord·small", actual: SpineType.lockupWord(.small), expected: S(size: 34, weight: 700, scaling: card, face: .caveat, isLowercase: true, inkOverhang: 6)),
        Row(name: "lockupWord·medium", actual: SpineType.lockupWord(.medium), expected: S(size: 44, weight: 700, scaling: card, face: .caveat, inkOverhang: 8)),
        Row(name: "lockupWord·large", actual: SpineType.lockupWord(.large), expected: S(size: 56, weight: 700, scaling: card, face: .caveat, inkOverhang: 10)),
        Row(name: "postmarkRing", actual: SpineType.postmarkRing(8), expected: S(size: 8, weight: 650, features: lin, scaling: .none))
    ]
    // swiftlint:enable line_length

    @Test("every rank matches the spec table", arguments: rows.map(\.name))
    func rankMatches(name: String) throws {
        let row = try #require(Self.rows.first { $0.name == name })
        #expect(row.actual == row.expected, "\(name): \(row.actual) ≠ \(row.expected)")
    }

    @Test("no italic rank carries small caps (the italic has no smcp)")
    func noItalicSmallCaps() {
        for row in Self.rows where row.actual.italic {
            #expect(!row.actual.features.contains(.smallCaps), "\(row.name) pairs italic with small caps")
        }
    }

    @Test("lockupWord is the only Caveat rank; postmarkRing the only unscaled one")
    func singletons() {
        let caveat = Set(Self.rows.filter { $0.actual.face == .caveat }.map { String($0.name.split(separator: "·")[0]) })
        let unscaled = Set(Self.rows.filter { $0.actual.scaling == .none }.map { $0.name })
        #expect(caveat == ["lockupWord"])
        #expect(unscaled == ["postmarkRing"])
    }

    @Test("every fixed text rank's style is its size band")
    func fixedRanksFollowTheBands() {
        for row in Self.rows {
            guard case .text(let style) = row.actual.scaling else { continue }
            #expect(style == SpineType.textStyle(forSize: row.actual.size), "\(row.name)")
        }
    }

    @Test("size bands hold their edges", arguments: [
        (9.5, Font.TextStyle.caption2), (11.5, .caption2), (12, .caption), (12.5, .caption),
        (13, .footnote), (14, .footnote), (15, .subheadline), (16, .body), (19, .body),
        (20, .title3), (21, .title3), (22, .title2), (26, .title2), (27, .title), (33, .title),
        (34, .largeTitle), (56, .largeTitle)
    ] as [(CGFloat, Font.TextStyle)])
    func bands(size: CGFloat, style: Font.TextStyle) {
        #expect(SpineType.textStyle(forSize: size) == style, "\(size) pt")
    }

    @Test("the untitled cut is the 340 italic, untracked")
    func untitledCut() {
        #expect(SpineType.Cut.untitled(size: 18) == SpineType.Cut(size: 18, weight: 340, italic: true, trackingEm: 0))
    }
}
