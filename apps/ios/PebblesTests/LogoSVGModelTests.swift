import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

@Suite("LogoSVGModel")
struct LogoSVGModelTests {

    @Test("parses viewBox and preserves path order")
    func parsesViewBoxAndOrder() throws {
        let svg = """
        <svg viewBox="0 0 300 300" xmlns="http://www.w3.org/2000/svg">
          <path d="M 0 0 L 100 100"/>
          <path d="M 100 0 L 0 100"/>
          <path d="M 50 50 L 200 200"/>
        </svg>
        """
        let model = try #require(LogoSVGModel(svg: svg))
        #expect(model.viewBox == CGRect(x: 0, y: 0, width: 300, height: 300))
        #expect(model.paths.count == 3)
        // Document order preserved: first path's bbox starts at (0, 0).
        let firstBox = model.paths[0].boundingBoxOfPath
        #expect(abs(firstBox.minX) < 0.001)
        #expect(abs(firstBox.minY) < 0.001)
    }

    @Test("descends into nested groups")
    func descendsIntoNestedGroups() throws {
        // Mirrors the bundled WelcomeLogo.svg structure: a top-level <g>
        // wrapper plus an inner <g id="creature"> with multiple paths.
        let svg = """
        <svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
          <g id="outer">
            <path d="M 0 0 L 10 10"/>
            <g id="inner">
              <path d="M 20 20 L 30 30"/>
              <path d="M 40 40 L 50 50"/>
            </g>
            <path d="M 60 60 L 70 70"/>
          </g>
        </svg>
        """
        let model = try #require(LogoSVGModel(svg: svg))
        #expect(model.paths.count == 4)
    }

    @Test("returns nil for empty string")
    func returnsNilForEmpty() {
        #expect(LogoSVGModel(svg: "") == nil)
    }

    @Test("returns nil when no <path> elements")
    func returnsNilWhenNoPaths() {
        let svg = "<svg viewBox=\"0 0 10 10\"></svg>"
        #expect(LogoSVGModel(svg: svg) == nil)
    }

    @Test("returns nil when viewBox is missing")
    func returnsNilWhenViewBoxMissing() {
        let svg = "<svg><path d=\"M 0 0 L 1 1\"/></svg>"
        #expect(LogoSVGModel(svg: svg) == nil)
    }

    @Test("parses the bundled WelcomeLogo.svg with 16 paths")
    func parsesBundledLogo() throws {
        // The bundled welcome logo from issue #357 has 16 stroked paths
        // inside <g id="pebbles-logo-strokes">: 1 outline + 12 creature
        // parts + 2 veins + 1 fossil. Read the SVG from disk via a path
        // relative to this test file so the test does not depend on the
        // test bundle's resource layout.
        let testFile = URL(fileURLWithPath: #filePath)
        let svgURL = testFile
            .deletingLastPathComponent()           // PebblesTests/
            .deletingLastPathComponent()           // apps/ios/
            .appendingPathComponent("Pebbles/Resources/WelcomeLogo.svg")
        let svg = try String(contentsOf: svgURL, encoding: .utf8)
        let model = try #require(LogoSVGModel(svg: svg))
        #expect(model.viewBox == CGRect(x: 0, y: 0, width: 300, height: 300))
        #expect(model.paths.count == 16)
    }
}
