import Foundation
import Testing
@testable import Pebbles

// Ported from apps/web/lib/utils/spine/entry-type.test.ts at a04aeec5. The phone
// column only: the `wide: true` rows are skipped (no spread on iPhone). Sizes are
// SpineType.Cut's `size`; weights and italics are the cut.
@Suite("EntryTypes (web entry-type.test.ts)")
struct EntryTypeTests {
    static let sizes: [Int: CGFloat] = [1: 19, 2: 24, 3: 31]
    // swiftlint:disable:next large_tuple
    static let cuts: [Int: (weight: Int, italic: Bool, tracking: CGFloat)] = [
        1: (600, false, -0.012), 0: (460, false, -0.012), -1: (340, true, -0.004)
    ]

    @Test(arguments: [1, 2, 3], [-1, 0, 1])
    func entry(intensity: Int, valence: Int) throws {
        let size = try #require(Self.sizes[intensity])
        let cut = try #require(Self.cuts[valence])
        #expect(EntryTypes.entryType(intensity: intensity, valence: valence) == EntryType(
            name: SpineType.Cut(size: size, weight: cut.weight, italic: cut.italic, trackingEm: cut.tracking),
            lineHeight: 1.12, anchor: size * 0.6
        ))
    }

    @Test("grows strictly with intensity, so size alone tells it")
    func grows() {
        let sizes = [1, 2, 3].map { EntryTypes.entryType(intensity: $0, valence: 0).name.size }
        #expect(sizes == sizes.sorted())
        #expect(Set(sizes).count == 3)
    }

    @Test("weighs contents names by intensity and sets lowlights in a light italic")
    func contents() {
        #expect([1, 2, 3].map { EntryTypes.contentsNameType(intensity: $0, valence: 1).weight } == [400, 470, 620])
        #expect([1, 2, 3].map { EntryTypes.contentsNameType(intensity: $0, valence: 0).weight } == [400, 470, 620])
        for intensity in [1, 2, 3] {
            let cut = EntryTypes.contentsNameType(intensity: intensity, valence: -1)
            #expect(cut.weight == 350)
            #expect(cut.italic)
        }
        #expect(EntryTypes.contentsNameType(intensity: 2, valence: 0).size == 16.5)
    }

    @Test("cuts a card title by valence")
    func cardTitle() {
        typealias Cut = SpineType.Cut
        #expect(EntryTypes.cardTitleCut(valence: 1) == Cut(size: 4.4, weight: 600, italic: false, trackingEm: 0))
        #expect(EntryTypes.cardTitleCut(valence: 0) == Cut(size: 4.4, weight: 480, italic: false, trackingEm: 0))
        #expect(EntryTypes.cardTitleCut(valence: -1) == Cut(size: 4.4, weight: 360, italic: true, trackingEm: 0))
    }
}
