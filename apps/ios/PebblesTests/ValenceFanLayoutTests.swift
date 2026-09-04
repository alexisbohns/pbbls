import CoreGraphics
import Testing
@testable import Pebbles

/// The fan's constants are eye-tuned, so what is worth asserting is not the
/// numbers themselves but the properties tuning must not break: every stone
/// stays on the canvas, no two stones collide, and the two orderings the
/// arrangement means (bigger = higher, lowlight → highlight = left → right)
/// actually hold.
@Suite("ValenceFanLayout")
struct ValenceFanLayoutTests {

    /// Matches the breathing room the design asks for at the narrowest
    /// supported content width.
    private static let minimumMargin: CGFloat = 8

    @Test("every stone stays inside the reference canvas")
    func stonesStayOnCanvas() {
        let canvas = ValenceFanLayout.reference
        for valence in Valence.allCases {
            let frame = ValenceFanLayout.frame(for: valence)
            #expect(frame.minX >= Self.minimumMargin, "\(valence) overflows left")
            #expect(frame.minY >= Self.minimumMargin, "\(valence) overflows top")
            #expect(frame.maxX <= canvas.width - Self.minimumMargin, "\(valence) overflows right")
            #expect(frame.maxY <= canvas.height - Self.minimumMargin, "\(valence) overflows bottom")
        }
    }

    @Test("no two stones overlap")
    func stonesDoNotOverlap() {
        let all = Valence.allCases
        for (index, one) in all.enumerated() {
            for other in all.dropFirst(index + 1) {
                let first = ValenceFanLayout.frame(for: one)
                let second = ValenceFanLayout.frame(for: other)
                #expect(!first.intersects(second), "\(one) overlaps \(other)")
            }
        }
    }

    @Test("stones grow with size")
    func heightsIncreaseWithSize() {
        let small = ValenceFanLayout.stoneHeight(for: .small)
        let medium = ValenceFanLayout.stoneHeight(for: .medium)
        let large = ValenceFanLayout.stoneHeight(for: .large)
        #expect(small < medium)
        #expect(medium < large)
    }

    @Test("width follows the silhouette's own aspect ratio")
    func widthFollowsAspectRatio() {
        for size in ValenceSizeGroup.allCases {
            let expected = ValenceFanLayout.stoneHeight(for: size)
                * PebbleOutlineGeometry.aspectRatio(for: size)
            // Approximate: CGFloat × Double rounds differently depending on
            // where the conversion lands, and the assertion is about the rule,
            // not the last bit.
            #expect(abs(ValenceFanLayout.stoneWidth(for: size) - expected) < 0.001)
        }
    }

    @Test("bigger events sit higher, at every polarity")
    func biggerSizesSitHigher() {
        for polarity in ValencePolarity.allCases {
            let heights = ValenceSizeGroup.allCases.map { size in
                ValenceFanLayout.centre(for: valence(size, polarity)).y
            }
            // Origin is top-left, so "higher on screen" means a smaller y.
            #expect(heights[0] > heights[1], "\(polarity): medium is not above small")
            #expect(heights[1] > heights[2], "\(polarity): large is not above medium")
        }
    }

    @Test("polarity runs lowlight → neutral → highlight, left to right")
    func polarityRunsLeftToRight() {
        for size in ValenceSizeGroup.allCases {
            let centres = ValencePolarity.allCases.map { polarity in
                ValenceFanLayout.centre(for: valence(size, polarity)).x
            }
            #expect(centres[0] < centres[1], "\(size): neutral is not right of lowlight")
            #expect(centres[1] < centres[2], "\(size): highlight is not right of neutral")
        }
    }

    @Test("the fan spreads wider as it rises")
    func spreadWidensWithSize() {
        let spreads = ValenceSizeGroup.allCases.map { size -> CGFloat in
            let centre = ValenceFanLayout.centre(for: valence(size, .highlight)).x
            return centre - ValenceFanLayout.reference.width / 2
        }
        #expect(spreads[0] < spreads[1])
        #expect(spreads[1] < spreads[2])
    }

    @Test("the neutral stone of each ring is lifted above its siblings")
    func neutralIsLifted() {
        for size in ValenceSizeGroup.allCases {
            let neutral = ValenceFanLayout.centre(for: valence(size, .neutral)).y
            let lowlight = ValenceFanLayout.centre(for: valence(size, .lowlight)).y
            let highlight = ValenceFanLayout.centre(for: valence(size, .highlight)).y
            #expect(neutral < lowlight, "\(size): neutral is not lifted")
            #expect(lowlight == highlight, "\(size): the two side stones are not level")
        }
    }

    @Test("placement is deterministic")
    func placementIsDeterministic() {
        for valence in Valence.allCases {
            #expect(ValenceFanLayout.centre(for: valence) == ValenceFanLayout.centre(for: valence))
        }
    }

    /// Lookup uniqueness is guaranteed by `ValenceHelpersTests.lookupIsUnique`.
    private func valence(_ size: ValenceSizeGroup, _ polarity: ValencePolarity) -> Valence {
        // swiftlint:disable:next force_unwrapping
        Valence.allCases.first { $0.sizeGroup == size && $0.polarity == polarity }!
    }
}
