import Foundation
import Testing
@testable import Pebbles

/// Android: `EmotionFamiliesTest`, on rows in `v_emotions_with_palette`'s shape.
@Suite("Emotion families")
struct EmotionFamiliesTests {
    private func row(_ slug: String, category: String, index: Int) throws -> EmotionWithPalette {
        let json = """
        {"id":"11111111-1111-1111-1111-11111111111\(index)","slug":"\(slug)","name":"\(slug)","emoji":"🙂",
         "category_id":"22222222-2222-2222-2222-22222222222\(index)","category_slug":"\(category)",
         "category_name":"\(category.prefix(1).uppercased() + category.dropFirst())",
         "primary_color":"#7B5E99FF","secondary_color":"#AE91CCFF","light_color":"#F2EFF5FF",
         "surface_color":"#7B5E991A","dark_color":"#2A2138FF","shaded_color":"#4A3A5CFF"}
        """
        return try JSONDecoder().decode(EmotionWithPalette.self, from: Data(json.utf8))
    }

    @Test("one family per category, in the wheel's order, unknown families last")
    func wheelOrder() throws {
        let families = EmotionFamilies.of([
            try row("calm", category: "peace", index: 1),
            try row("happy", category: "joy", index: 2),
            try row("content", category: "peace", index: 3),
            try row("awe", category: "wonder", index: 4),
            try row("brave", category: "pride", index: 5)
        ])
        #expect(families.map(\.slug) == ["joy", "pride", "peace", "wonder"])
        #expect(families[2].name == "Peace")
    }
}
