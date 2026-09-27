import Testing
import Foundation
@testable import Pebbles

@Suite("StoneLabPalettes")
struct StoneLabPalettesTests {

    /// The seed migration is the source of truth for the seven sextets. The
    /// simulator shares the host filesystem, so the test walks up from its
    /// own path to the repo and reads the SQL.
    private static func migrationLines() throws -> [String] {
        var url = URL(fileURLWithPath: #filePath)
        while url.lastPathComponent != "apps" { url.deleteLastPathComponent() }
        url.deleteLastPathComponent()
        url.appendPathComponent("packages/supabase/supabase/migrations/20260912120000_seed_emotion_reference_data.sql")
        return try String(contentsOf: url, encoding: .utf8).components(separatedBy: "\n")
    }

    @Test("every palette matches the migration literals", arguments: StoneLabPalettes.all)
    func matchesMigration(palette: StoneLabPalette) throws {
        let line = try #require(Self.migrationLines().first { $0.contains("'\(palette.slug)', '") })
        let hexes = line.split(separator: "'").map(String.init).filter { $0.hasPrefix("#") }
        // The migration's column order is …, surface_color, shaded_color, dark_color.
        #expect(hexes == [palette.primary, palette.secondary, palette.light, palette.surface, palette.shaded, palette.dark])
    }

    @Test("all seven groups, in the default category order")
    func order() {
        #expect(StoneLabPalettes.all.map(\.slug) == EmotionCategoryOrdering.default)
    }

    @Test("hex to rgb")
    func rgb() {
        let rgb = StoneLabPalettes.rgb("#8E4242FF")
        #expect(abs(rgb.x - 0x8E / 255.0) < 0.001)
        #expect(abs(rgb.y - 0x42 / 255.0) < 0.001)
        #expect(abs(rgb.z - 0x42 / 255.0) < 0.001)
        #expect(StoneLabPalettes.rgb("nope") == SIMD3<Float>(repeating: 0))
    }
}
