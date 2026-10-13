import Foundation

/// A pebble's derived glyph (spec D5) as every reader returns it:
/// `{id, name, strokes, view_box, source}`. Pebbles no longer own a glyph or
/// a server render (D6); the client carves these strokes into the stone.
struct PebbleGlyph: Decodable, Hashable, Identifiable {
    enum Source: String, Hashable {
        case collection
        case domain
    }

    let id: UUID
    let name: String?
    let strokes: [GlyphStroke]
    let viewBox: String
    /// Nil when the server sends a source this build does not know.
    let source: Source?

    private enum CodingKeys: String, CodingKey {
        case id, name, strokes, source
        case viewBox = "view_box"
    }

    init(id: UUID, name: String?, strokes: [GlyphStroke], viewBox: String, source: Source?) {
        self.id = id
        self.name = name
        self.strokes = strokes
        self.viewBox = viewBox
        self.source = source
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(UUID.self, forKey: .id)
        name = try container.decodeIfPresent(String.self, forKey: .name)
        strokes = try container.decodeIfPresent([GlyphStroke].self, forKey: .strokes) ?? []
        viewBox = try container.decodeIfPresent(String.self, forKey: .viewBox) ?? "0 0 200 200"
        source = try container.decodeIfPresent(String.self, forKey: .source).flatMap(Source.init(rawValue:))
    }

    /// The square side the strokes are drawn in: the third number of
    /// `view_box`. iOS carves at 200; imported web glyphs may differ.
    var viewBoxSide: Double {
        let numbers = viewBox
            .split(whereSeparator: { $0 == " " || $0 == "," })
            .compactMap { Double($0) }
        guard numbers.count == 4, numbers[2] > 0 else { return 200 }
        return numbers[2]
    }
}
