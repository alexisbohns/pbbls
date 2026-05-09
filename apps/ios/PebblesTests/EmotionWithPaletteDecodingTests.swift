import Foundation
import Testing
@testable import Pebbles

@Suite("EmotionWithPalette decoding")
struct EmotionWithPaletteDecodingTests {
    private func decode(_ json: String) throws -> EmotionWithPalette {
        let data = Data(json.utf8)
        return try JSONDecoder().decode(EmotionWithPalette.self, from: data)
    }

    private let validJson = """
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "slug": "anxiety",
      "name": "Anxiety",
      "color": "#7B5E99",
      "emoji": "😰",
      "category_id": "22222222-2222-2222-2222-222222222222",
      "category_slug": "fear",
      "category_name": "Fear",
      "primary_color": "#7B5E99FF",
      "secondary_color": "#AE91CCFF",
      "light_color": "#F2EFF5FF",
      "surface_color": "#7B5E991A"
    }
    """

    @Test("decodes a well-formed row")
    func decodesValid() throws {
        let row = try decode(validJson)
        #expect(row.slug == "anxiety")
        #expect(row.categorySlug == "fear")
        #expect(row.palette.primaryHex == "#7B5E99FF")
        #expect(row.palette.strokeHex(for: .dark) == "#AE91CC")
    }

    @Test("rejects null id")
    func rejectsNullId() {
        let json = validJson.replacingOccurrences(
            of: "\"id\": \"11111111-1111-1111-1111-111111111111\"",
            with: "\"id\": null"
        )
        #expect(throws: DecodingError.self) { try decode(json) }
    }

    @Test("rejects null primary_color")
    func rejectsNullPrimaryColor() {
        let json = validJson.replacingOccurrences(
            of: "\"primary_color\": \"#7B5E99FF\"",
            with: "\"primary_color\": null"
        )
        #expect(throws: DecodingError.self) { try decode(json) }
    }

    @Test("rejects malformed primary_color hex")
    func rejectsMalformedHex() {
        let json = validJson.replacingOccurrences(
            of: "\"primary_color\": \"#7B5E99FF\"",
            with: "\"primary_color\": \"not-hex\""
        )
        #expect(throws: DecodingError.self) { try decode(json) }
    }

    @Test("decodes the emoji field")
    func decodesEmoji() throws {
        let row = try decode(validJson)
        #expect(row.emoji == "😰")
    }

    @Test("rejects null emoji")
    func rejectsNullEmoji() {
        let json = validJson.replacingOccurrences(
            of: "\"emoji\": \"😰\"",
            with: "\"emoji\": null"
        )
        #expect(throws: DecodingError.self) { try decode(json) }
    }

    @Test("rejects missing emoji")
    func rejectsMissingEmoji() {
        let json = validJson.replacingOccurrences(
            of: "\"emoji\": \"😰\",\n",
            with: ""
        )
        #expect(throws: DecodingError.self) { try decode(json) }
    }
}
