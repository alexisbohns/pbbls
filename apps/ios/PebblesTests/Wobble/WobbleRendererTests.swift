import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

@Suite("WobbleRenderer")
struct WobbleRendererTests {

    private let composedSvg = """
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 260 260" width="260" height="260">
      <g id="layer:shape">
        <path d="M 20 130 C 20 70 70 20 130 20 C 190 20 240 70 240 130 C 240 190 190 240 130 240 C 70 240 20 190 20 130 Z" fill="none" stroke="currentColor"/>
      </g>
      <g id="layer:glyph" transform="translate(55, 55) scale(0.75)">
        <path d="M 0 0 L 200 200 M 0 200 L 200 0" fill="none" stroke="currentColor"/>
      </g>
    </svg>
    """

    @Test("pebble art is cached by svg content and index-aligned with layers")
    func pebbleCache() throws {
        let model = try #require(PebbleSVGModel(svg: composedSvg))
        let first = WobbleRenderer.pebbleArt(svg: composedSvg, model: model)
        let second = WobbleRenderer.pebbleArt(svg: composedSvg, model: model)
        #expect(first === second)
        #expect(first.layers.count == model.layers.count)
        for layer in first.layers {
            #expect(!layer.ink.isEmpty)
            #expect(!layer.centerline.isEmpty)
        }
    }

    @Test("§2.1 space rule scales amplitude, frequency and step")
    func spaceRule() {
        let params = WobbleParams.scaled(for: CGSize(width: 260, height: 310))
        let normalization = 200.0 / 310.0
        #expect(abs(params.amplitude - 18 / normalization) < 1e-12)
        #expect(abs(params.frequency - 0.024 * normalization) < 1e-12)
        #expect(abs(params.flattenStep - 2 / normalization) < 1e-12)
        #expect(params.octaves == 5)
    }

    @Test("degenerate space falls back to canonical params")
    func degenerateSpace() {
        let params = WobbleParams.scaled(for: .zero)
        #expect(params.amplitude == WobbleParams.canonical.amplitude)
        #expect(params.frequency == WobbleParams.canonical.frequency)
    }

    @Test("all nine backdrop assets parse and wobble")
    func backdropAssets() throws {
        for size in ValenceSizeGroup.allCases {
            for polarity in ValencePolarity.allCases {
                let art = try #require(
                    WobbleRenderer.backdropArt(size: size, polarity: polarity),
                    "backdrop art failed for \(size.rawValue)-\(polarity.rawValue)"
                )
                #expect(!art.path.isEmpty)
                #expect(art.viewBox.width > 0)
                #expect(art.viewBox.height > 0)
            }
        }
        // The only evenodd asset is large-lowlight (it carves a hole).
        #expect(WobbleRenderer.backdropArt(size: .large, polarity: .lowlight)?.usesEvenOddFill == true)
        #expect(WobbleRenderer.backdropArt(size: .medium, polarity: .neutral)?.usesEvenOddFill == false)
    }

    @Test("asset parsing survives on synthetic markup and rejects garbage")
    func assetParsing() {
        let asset = """
        <svg width="200" height="100" viewBox="0 0 200 100" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M10 50 C 10 10, 190 10, 190 50 C 190 90, 10 90, 10 50 Z" fill="#FF00FF"/>
        </svg>
        """
        let art = WobbleRenderer.backdropArt(fromAsset: asset)
        #expect(art != nil)
        #expect(art?.viewBox == CGRect(x: 0, y: 0, width: 200, height: 100))
        #expect(art?.usesEvenOddFill == false)
        #expect(WobbleRenderer.backdropArt(fromAsset: "<svg></svg>") == nil)
    }

    @Test("glyph ink parses carve strokes and caches by content")
    func glyphInk() {
        // swiftlint:disable:next identifier_name
        let d = "M30,30 Q60,80 100,100 L170,170"
        let first = WobbleRenderer.glyphInk(d: d, width: 6)
        let second = WobbleRenderer.glyphInk(d: d, width: 6)
        #expect(first != nil)
        #expect(first === second)
        // A different width is different ink.
        let wider = WobbleRenderer.glyphInk(d: d, width: 10)
        #expect(wider !== first)
        // Unparseable input falls back to nil (caller strokes plainly).
        #expect(WobbleRenderer.glyphInk(d: "not a path", width: 6) == nil)
    }
}
