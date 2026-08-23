import Foundation
import Testing
@testable import Pebbles

@Suite("Domain — v_domains_with_glyph decoding")
struct DomainWithGlyphDecodingTests {

    private func decode(_ json: String) throws -> Domain {
        try JSONDecoder().decode(Domain.self, from: Data(json.utf8))
    }

    @Test("a view row decodes with its glyph strokes")
    func decodesWithGlyph() throws {
        let domain = try decode("""
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "slug": "health",
          "name": "Health",
          "label": "Your body, energy, and physical well-being",
          "strokes": [{"d": "M10 10 L 90 90", "width": 6}],
          "view_box": "0 0 200 200"
        }
        """)

        #expect(domain.slug == "health")
        #expect(domain.strokes?.count == 1)
        #expect(domain.strokes?.first?.d == "M10 10 L 90 90")
        #expect(domain.viewBox == "0 0 200 200")
    }

    @Test("a domain with no default glyph decodes with nil strokes")
    func decodesWithoutGlyph() throws {
        let domain = try decode("""
        {
          "id": "22222222-2222-2222-2222-222222222222",
          "slug": "weather",
          "name": "Weather",
          "label": "Sun or rain, seasons and skies",
          "strokes": null,
          "view_box": null
        }
        """)

        #expect(domain.strokes == nil)
        #expect(domain.viewBox == nil)
    }

    @Test("a plain domains-table row still decodes, keys absent entirely")
    func decodesTableRow() throws {
        let domain = try decode("""
        {
          "id": "33333333-3333-3333-3333-333333333333",
          "slug": "work",
          "name": "Work",
          "label": "Your job, career, and professional life"
        }
        """)

        #expect(domain.strokes == nil)
        #expect(domain.viewBox == nil)
    }

    @Test("localizedLabel falls back to the DB label for an unknown slug")
    func labelFallsBack() {
        let domain = Domain(
            id: UUID(),
            slug: "not-a-real-slug-xyz",
            name: "Fallback Name",
            label: "Fallback Label"
        )
        #expect(domain.localizedLabel == "Fallback Label")
    }
}
