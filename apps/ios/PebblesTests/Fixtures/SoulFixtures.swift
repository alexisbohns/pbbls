import Foundation
@testable import Pebbles

/// Sixteen people with the counts of the souls canvas (page Converged, screen 01).
enum SoulFixtures {
    static let glyph = Glyph(
        id: SystemGlyph.default, name: nil,
        strokes: [GlyphStroke(d: "M30,30 C60,10 140,10 170,30 S190,140 170,170 S60,190 30,170 S10,60 30,30", width: 6)],
        viewBox: "0 0 200 200", userId: nil
    )

    static func soul(_ name: String, _ count: Int, _ id: Int) -> SoulWithGlyph {
        SoulWithGlyph(id: UUID(uuidString: String(format: "00000000-0000-4000-8000-%012d", id))!,
                      name: name, glyphId: glyph.id, glyph: glyph, pebblesCount: count)
    }

    static let sixteen: [SoulWithGlyph] = [
        soul("Maman", 41, 1), soul("Théo", 33, 2), soul("Léa", 30, 3), soul("Papa", 28, 4),
        soul("Inès", 19, 5), soul("Sam", 12, 6), soul("Mamie Jo", 9, 7), soul("Nour", 8, 8),
        soul("Adèle", 2, 9), soul("Alex", 24, 10), soul("Alex", 22, 11), soul("Antoine", 6, 12),
        soul("Baptiste", 2, 13), soul("Ben", 3, 14), soul("Camille", 29, 15), soul("Chloé", 2, 16)
    ]
}
