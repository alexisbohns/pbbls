import Foundation

/// The seed of a sample stone, one that belongs to no pebble (#1690 D4): the
/// valence picker's fan. A real pebble passes `StableHash.seed(pebble.id)`; a
/// sample passes a fixed constant per valence, so it never changes between
/// launches and matches the same sample on web and Android.
enum LitStoneSeed {
    static func sample(_ valence: Valence) -> UInt32 {
        StableHash.fnv1a("sample-\(valence.rawValue)")
    }
}

/// The glyph the sample stones carve: `stone-glyph.json`, the same drawing the
/// Metal sample carved. Decoded once, with a fixed id so the cache keys on it.
enum LitStoneSampleGlyph {
    private struct File: Decodable {
        let viewBox: Double
        let strokes: [GlyphStroke]
    }

    /// Nil when the file is missing: the sample stone then draws uncarved.
    static let glyph: PebbleGlyph? = {
        guard let url = Bundle.main.url(forResource: "stone-glyph", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let file = try? JSONDecoder().decode(File.self, from: data) else { return nil }
        let side = file.viewBox.rounded() == file.viewBox ? String(Int(file.viewBox)) : String(file.viewBox)
        return PebbleGlyph(id: UUID(uuid: (0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)),
                           name: nil, strokes: file.strokes, viewBox: "0 0 \(side) \(side)", source: nil)
    }()
}
