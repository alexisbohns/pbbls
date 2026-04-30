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
          <g id="layer:shape">
            <path d="M 0 0 L 100 100" fill="none" stroke="currentColor"/>
          </g>
          <g id="layer:glyph" transform="translate(0, 0) scale(1)">
            <path d="M 0 0 L 50 50" fill="none" stroke="currentColor"/>
          </g>
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

    @Test("propagates nested-group transforms into layer paths (glyph centering wrapper)")
    func nestedGroupBakesTransform() throws {
        // The glyph engine emits glyph strokes inside an extra <g transform=...>
        // centering wrapper. The model must bake that transform into the path
        // so the layer ends up with non-empty geometry.
        let svg = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 240" width="240" height="240">
          <g id="layer:shape">
            <path d="M 0 0 L 240 240" fill="none" stroke="currentColor"/>
          </g>
          <g id="layer:glyph" transform="translate(40, 40) scale(0.8)">
            <g transform="translate(20, 20) scale(0.5)">
              <path d="M 0 0 L 100 100" fill="none" stroke="currentColor"/>
            </g>
          </g>
        </svg>
        """
        let model = try #require(PebbleSVGModel(svg: svg))
        #expect(model.layers.map(\.kind) == [.shape, .glyph])
        // Glyph layer must have a non-empty path despite the path being nested
        // one level deeper than the layer. Without the bake-and-propagate fix,
        // this assertion fails: combinedPath is empty and the layer is dropped.
        let glyph = try #require(model.layers.first { $0.kind == .glyph })
        #expect(!glyph.combinedPath.boundingBoxOfPath.isNull)
        // The bbox should reflect the inner transform (translate 20,20 scale 0.5)
        // applied to the path's source points (0,0)–(100,100). Resulting points
        // are (20,20)–(70,70) BEFORE the layer's own transform (which the
        // renderer applies separately).
        let bbox = glyph.combinedPath.boundingBoxOfPath
        #expect(abs(bbox.minX - 20) < 0.001)
        #expect(abs(bbox.minY - 20) < 0.001)
        #expect(abs(bbox.maxX - 70) < 0.001)
        #expect(abs(bbox.maxY - 70) < 0.001)
    }

    @Test("returns nil when any path d-string fails to parse")
    func failsClosedOnBadPath() {
        let svg = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
          <g id="layer:shape">
            <path d="this is not a valid path" fill="none"/>
          </g>
        </svg>
        """
        #expect(PebbleSVGModel(svg: svg) == nil)
    }

    @Test("skips SVG-invisible paths (fill=none + no stroke)")
    func skipsInvisiblePaths() throws {
        // Mirrors the engine's shape output: one visible stroke path plus
        // one path that's fill=none with no stroke attribute (originally a
        // Figma fill shape that stripFills zeroed). SVGView correctly
        // skips the second; the model must too.
        //
        // The invisible path's geometry extends beyond the visible one, so
        // if it leaks into the combined layer path the bounding box widens
        // past (0,0)–(100,100). That's the assertion below.
        let svg = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200">
          <g id="layer:shape">
            <path d="M 0 0 L 100 100" fill="none" stroke="currentColor" stroke-width="6"/>
            <path fill-rule="evenodd" d="M -50 -50 L 200 200 L 200 -50 Z" fill="none"/>
          </g>
        </svg>
        """
        let model = try #require(PebbleSVGModel(svg: svg))
        let shape = try #require(model.layers.first { $0.kind == .shape })
        // Only the visible stroke path contributes to the combined geometry.
        let bbox = shape.combinedPath.boundingBoxOfPath
        #expect(abs(bbox.minX - 0) < 0.001)
        #expect(abs(bbox.minY - 0) < 0.001)
        #expect(abs(bbox.maxX - 100) < 0.001)
        #expect(abs(bbox.maxY - 100) < 0.001)
    }
}
