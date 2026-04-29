import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

@Suite("PebbleSVGModel")
struct PebbleSVGModelTests {

    private let composedSvg = """
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 240" width="240" height="240">
      <g id="layer:shape">
        <path id="shape:stroke-0" d="M 10 10 L 230 10 L 230 230 L 10 230 Z" fill="none" stroke="currentColor"/>
      </g>
      <g id="layer:fossil" opacity="0.3">
        <path id="fossil:stroke-0" d="M 50 50 L 190 190" fill="none" stroke="currentColor"/>
      </g>
      <g id="layer:glyph" transform="translate(40, 40) scale(0.8)">
        <path id="glyph:stroke-0" d="M 0 0 L 200 200" fill="none" stroke="currentColor"/>
      </g>
    </svg>
    """

    @Test("parses viewBox, layer order, and transforms")
    func happy() throws {
        let model = try #require(PebbleSVGModel(svg: composedSvg))
        #expect(model.viewBox == CGRect(x: 0, y: 0, width: 240, height: 240))
        #expect(model.layers.map(\.kind) == [.shape, .fossil, .glyph])
        #expect(abs(model.layers[1].opacity - 0.3) < 1e-6)
        #expect(model.layers[0].opacity == 1.0)
        let glyphTransform = model.layers[2].transform
        // translate(40, 40) scale(0.8) → tx=40, ty=40, a=0.8, d=0.8
        #expect(abs(glyphTransform.a - 0.8) < 1e-6)
        #expect(abs(glyphTransform.d - 0.8) < 1e-6)
        #expect(abs(glyphTransform.tx - 40) < 1e-6)
        #expect(abs(glyphTransform.ty - 40) < 1e-6)
        for layer in model.layers {
            #expect(!layer.combinedPath.boundingBoxOfPath.isNull)
        }
    }

    @Test("handles fossil-less svg")
    func noFossil() throws {
        let svg = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="100" height="100">
          <g id="layer:shape"><path d="M 0 0 L 100 100" fill="none"/></g>
          <g id="layer:glyph" transform="translate(0, 0) scale(1)"><path d="M 0 0 L 50 50" fill="none"/></g>
        </svg>
        """
        let model = try #require(PebbleSVGModel(svg: svg))
        #expect(model.layers.map(\.kind) == [.shape, .glyph])
    }

    @Test("returns nil when viewBox is missing")
    func missingViewBox() {
        let svg = """
        <svg xmlns="http://www.w3.org/2000/svg">
          <g id="layer:shape"><path d="M 0 0 L 100 100"/></g>
        </svg>
        """
        #expect(PebbleSVGModel(svg: svg) == nil)
    }

    @Test("returns nil when no recognized layer is present")
    func noLayers() {
        let svg = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
          <g id="some:other"><path d="M 0 0 L 1 1"/></g>
        </svg>
        """
        #expect(PebbleSVGModel(svg: svg) == nil)
    }
}
