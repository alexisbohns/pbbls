import Foundation
import Testing
@testable import Pebbles

@Suite("EmotionCategoryOrdering")
struct EmotionCategoryOrderingTests {

    /// The seven category slugs that must appear (and only these) in every
    /// ordering. Mirrors public.emotion_categories.slug.
    private let allCategorySlugs: Set<String> = [
        "anger", "fear", "joy", "peace", "pride", "sadness", "shame"
    ]

    @Test("every cell has exactly 7 slugs")
    func everyCellHasSevenSlugs() {
        for (key, slugs) in EmotionCategoryOrdering.byValence {
            #expect(slugs.count == 7, "\(key) has \(slugs.count) slugs, expected 7")
        }
    }

    @Test("every cell contains the same 7 unique slugs")
    func everyCellMatchesCanonicalSet() {
        for (key, slugs) in EmotionCategoryOrdering.byValence {
            let asSet = Set(slugs)
            #expect(asSet == allCategorySlugs, "\(key) slug set mismatch: \(asSet)")
            #expect(asSet.count == slugs.count, "\(key) has duplicate slugs")
        }
    }

    @Test("default ordering has the same 7 unique slugs")
    func defaultMatchesCanonicalSet() {
        let asSet = Set(EmotionCategoryOrdering.default)
        #expect(asSet == allCategorySlugs)
        #expect(asSet.count == EmotionCategoryOrdering.default.count)
    }

    @Test("all 9 valence cells are populated")
    func allNineCellsPresent() {
        let expected: [(ValenceSizeGroup, ValencePolarity)] = [
            (.large, .highlight), (.medium, .highlight), (.small, .highlight),
            (.large, .neutral),   (.medium, .neutral),   (.small, .neutral),
            (.large, .lowlight),  (.medium, .lowlight),  (.small, .lowlight),
        ]
        for (size, polarity) in expected {
            let key = EmotionCategoryOrdering.Key(size, polarity)
            #expect(
                EmotionCategoryOrdering.byValence[key] != nil,
                "missing ordering for (\(size), \(polarity))"
            )
        }
    }

    @Test("order(for: nil) returns the default")
    func nilValenceUsesDefault() {
        #expect(EmotionCategoryOrdering.order(for: nil) == EmotionCategoryOrdering.default)
    }

    @Test("order(for: .highlightLarge) matches the user-anchored ordering")
    func largeHighlightAnchor() {
        let order = EmotionCategoryOrdering.order(for: .highlightLarge)
        #expect(order == ["pride", "joy", "peace", "fear", "anger", "shame", "sadness"])
    }

    @Test("order(for: .lowlightMedium) matches the user-anchored ordering")
    func mediumLowlightAnchor() {
        let order = EmotionCategoryOrdering.order(for: .lowlightMedium)
        #expect(order == ["anger", "fear", "shame", "sadness", "peace", "pride", "joy"])
    }

    @Test("order(for: .lowlightLarge) matches the user-anchored ordering")
    func largeLowlightAnchor() {
        let order = EmotionCategoryOrdering.order(for: .lowlightLarge)
        #expect(order == ["sadness", "fear", "anger", "shame", "peace", "joy", "pride"])
    }
}
