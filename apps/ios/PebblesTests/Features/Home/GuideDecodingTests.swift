import Foundation
import Testing
@testable import Pebbles

@Suite("Guide decoding") struct GuideDecodingTests {
    static let row = ##"{"id":"6f0e2c3e-5b57-4b9d-9a3d-6f7f4b8c1d2e","slug":"stones","sort_order":20,"illustration":"stones","title_en":"About the stones","title_fr":"À propos des pierres","summary_en":"How a stone is built.","summary_fr":null,"body_md_en":"# Stones\n\nEvery pebble is drawn as a stone.","body_md_fr":null,"published":true,"created_at":"2026-10-27T09:00:00+00:00","updated_at":"2026-10-27T09:00:00.123456+00:00"}"##

    @Test func aRowDecodesWithNullsAndAnEnumIllustration() throws {
        let g = try JSONDecoder().decode(Guide.self, from: Data(Self.row.utf8))
        #expect(g.slug == "stones" && g.sortOrder == 20 && g.illustration == .stones)
        #expect(g.summaryFr == nil && g.bodyMdFr == nil)
    }

    @Test func frenchFallsBackToEnglishFieldByField() throws {
        let g = try JSONDecoder().decode(Guide.self, from: Data(Self.row.utf8))
        let fr = Locale(identifier: "fr"), en = Locale(identifier: "en")
        #expect(g.title(for: fr) == "À propos des pierres")
        #expect(g.summary(for: fr) == "How a stone is built.")
        #expect(g.body(for: fr) == "# Stones\n\nEvery pebble is drawn as a stone.")
        #expect(g.title(for: en) == "About the stones")
    }

    @Test func anUnknownIllustrationDecodesAsNil() throws {
        let json = Self.row.replacingOccurrences(of: "\"illustration\":\"stones\"", with: "\"illustration\":\"photo\"")
        let g = try JSONDecoder().decode(Guide.self, from: Data(json.utf8))
        #expect(g.illustration == nil)
    }

    @Test func aNullIllustrationDecodesAsNil() throws {
        let json = Self.row.replacingOccurrences(of: "\"illustration\":\"stones\"", with: "\"illustration\":null")
        let g = try JSONDecoder().decode(Guide.self, from: Data(json.utf8))
        #expect(g.illustration == nil)
    }

    @Test func aBadRowIsSkippedNotFatal() throws {
        let json = "[\(Self.row.replacingOccurrences(of: "\"slug\":\"stones\"", with: "\"slug\":42")),\(Self.row)]"
        let rows = try JSONDecoder().decode(LossyGuideArray.self, from: Data(json.utf8)).guides
        #expect(rows.count == 1)
    }
}
