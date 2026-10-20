import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// The nine lit stones on the brand palette, laid out as `StoneView`'s
    /// "Nine stones · brand" preview: Metal, the wobbled silhouette and both
    /// schemes. B3 moves these on purpose.
    @Suite("Stone references")
    @MainActor
    struct StoneReferences {

        @Test(.enabled(if: ReferenceLocale.current == .en), arguments: ReferenceScheme.allCases)
        func nineStones(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(NineStonesFixture(isFlat: false), named: "nineStones",
                                         config: ReferenceConfig(scheme: scheme, width: nil))
        }

        /// The Low Power drawing.
        @Test(.enabled(if: ReferenceLocale.current == .en), arguments: ReferenceScheme.allCases)
        func nineStonesFlat(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(NineStonesFixture(isFlat: true), named: "nineStonesFlat",
                                         config: ReferenceConfig(scheme: scheme, width: nil))
        }

        /// The `layerEffect` check (spec D6): a lit stone with no shadow, no
        /// edge, no sheen and no carving is the shader alone. If it draws
        /// nothing, the window fallback is needed.
        @Test("the lit stone draws material under ImageRenderer")
        func litStoneDrawsMaterial() throws {
            let config = ReferenceConfig(scheme: .paper, width: nil)
            let lit = try #require(ReferenceImage.render(ShaderOnlyStone(isFlat: false), config: config, locale: .en))
            let flat = try #require(ReferenceImage.render(ShaderOnlyStone(isFlat: true), config: config, locale: .en))
            let bytes = ReferenceImage.rgba(lit)
            let centre = ((lit.height / 2) * lit.width + lit.width / 2) * 4
            #expect(bytes[centre + 3] == 255, "the lit stone's centre is transparent: the shader drew nothing")
            guard case .differing(let fraction) = ReferenceImage.compare(lit, flat) else {
                Issue.record("lit and flat stones differ in size")
                return
            }
            #expect(fraction > 0.1, "the shader changed only \(fraction * 100) % of pixels")
        }
    }
}

private struct NineStonesFixture: View {
    let isFlat: Bool

    var body: some View {
        VStack(spacing: 12) {
            ForEach(ValenceSizeGroup.allCases) { size in
                HStack(spacing: 12) {
                    ForEach(ValencePolarity.allCases, id: \.self) { polarity in
                        if let valence = Valence.allCases.first(where: {
                            $0.sizeGroup == size && $0.polarity == polarity
                        }) {
                            StoneView(
                                valence: valence, palette: .brand,
                                material: .starting(for: polarity), tones: .starting(for: polarity),
                                light: .rest, height: 110, isFlat: isFlat
                            )
                        }
                    }
                }
            }
        }
        .padding()
        .background(Color.system.background)
    }
}

private struct ShaderOnlyStone: View {
    let isFlat: Bool

    var body: some View {
        var material = StoneMaterial.starting(for: .neutral)
        material.sheen = 0
        return StoneView(
            valence: .neutralMedium, palette: .brand, material: material, tones: .starting(for: .neutral),
            light: .rest, height: 110, isFlat: isFlat, look: .flat, carving: .blank
        )
    }
}
