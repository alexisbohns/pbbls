import CoreGraphics
import Foundation
import SwiftUI
import Testing
@testable import Pebbles

/// `stone-recipe.test.ts` transcribed through the Android port's test, plus the
/// web's verbatim numbers for the fixture seed (printed once from `apps/web`
/// with `stoneRecipe`).
@Suite("Lit stone recipe")
struct LitStoneRecipeTests {
    private typealias Recipe = LitStoneRecipe

    private static let fixtureID = "0e9d8c7b-6a5f-4e3d-8c2b-1a0f9e8d7c6b"
    private let seed: UInt32 = StableHash.fnv1a(fixtureID)

    private func gem(_ size: ValenceSizeGroup) throws -> (facets: [LitFacet], dots: [LitDot], sparkles: [Path]) {
        guard case let .gem(facets, dots, sparkles) = Recipe.of(size, .highlight, seed: seed) else {
            throw RecipeShapeError.notAGem
        }
        return (facets, dots, sparkles)
    }

    private enum RecipeShapeError: Error { case notAGem }

    private func point(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: x, y: y) }

    private func star(_ d: String) -> Path {
        guard let cg = SVGPathParser.parse(d) else { return Path() }
        return Path(cg)
    }

    @Test("the seed is the web's hashUUID")
    func seedIsHashUUID() throws {
        let id = try #require(UUID(uuidString: Self.fixtureID))
        #expect(StableHash.seed(id) == 3_118_864_542)
        #expect(seed == 3_118_864_542)
    }

    @Test("mulberry32 matches JS for seed 1")
    func mulberry() {
        let rand = Recipe.random(seed: 1)
        #expect(rand() == 0.6270739405881613)
        #expect(rand() == 0.002735721180215478)
        #expect(rand() == 0.5274470399599522)
    }

    @Test("draws the same stone for the same seed")
    func sameSeed() {
        #expect(Recipe.of(.medium, .highlight, seed: seed) == Recipe.of(.medium, .highlight, seed: seed))
    }

    @Test("draws a different stone for another pebble")
    func otherSeed() {
        let other = StableHash.fnv1a("8d1f6c3e-2b7a-4c1e-9f0a-5e6d7c8b9a01")
        #expect(Recipe.of(.medium, .lowlight, seed: seed) != Recipe.of(.medium, .lowlight, seed: other))
    }

    @Test("cuts a gem into five facets and a face, with ten glitter dots and two sparkles")
    func gemShape() throws {
        let gem = try gem(.large)
        #expect(gem.facets.count == 6)
        #expect(gem.facets.prefix(5).allSatisfy { $0.points.count == 4 && $0.edge != nil })
        let face = gem.facets[5]
        #expect(face.edge == nil)
        #expect(face.opacity == 0.14)
        #expect(face.points.count == 5)
        #expect(gem.dots.count == 10)
        #expect(gem.dots.allSatisfy { $0.slot == .light })
        #expect(gem.sparkles.count == 2)
    }

    @Test("shades the facets from the upper-left light: lit, lit, dark, dark, lit")
    func facetShading() throws {
        let gem = try gem(.medium)
        #expect(gem.facets.prefix(5).map(\.slot) == [.white, .white, .dark, .dark, .white])
    }

    @Test("lands the medium gem where the web does")
    func mediumGem() throws {
        let gem = try gem(.medium)
        #expect(gem.dots[0] == LitDot(cx: 179.39, cy: 207.06, r: 4.26, opacity: 0.93, slot: .light))
        #expect(gem.dots[9] == LitDot(cx: 168.84, cy: 206.72, r: 3.96, opacity: 0.82, slot: .light))
        #expect(gem.facets[0].points == [
            point(84.57, 59.84), point(155.58, 45.03), point(294.13, -182.52), point(-131.93, -93.66),
        ])
        let edge = try #require(gem.facets[0].edge)
        #expect(edge.0 == point(84.57, 59.84))
        #expect(edge.1 == point(-131.93, -93.66))
        #expect(gem.facets[5].points == [
            point(84.57, 59.84), point(155.58, 45.03), point(194.3, 91.62),
            point(137.44, 143.24), point(67.46, 112.97),
        ])
        #expect(gem.sparkles[0] == star(
            "M85.8 44.14Q88.31 57.29 101.46 59.8Q88.31 62.31 85.8 75.46Q83.29 62.31 70.14 59.8Q83.29 57.29 85.8 44.14Z"))
        #expect(gem.sparkles[1] == star(
            "M163.8 68.84Q165.27 76.53 172.96 78Q165.27 79.47 163.8 87.16Q162.33 79.47 154.64 78Q162.33 76.53 163.8 68.84Z"))
    }

    @Test("grains a river pebble with fourteen specks, lit and shaded")
    func river() throws {
        guard case let .river(dots, light) = Recipe.of(.small, .neutral, seed: seed) else {
            throw RecipeShapeError.notAGem
        }
        #expect(dots.count == 14)
        #expect(dots.allSatisfy { $0.slot == .light || $0.slot == .shaded })
        #expect(dots[0] == LitDot(cx: 177.45, cy: 168.99, r: 3.6, opacity: 0.53, slot: .light))
        #expect(dots[2] == LitDot(cx: 73.04, cy: 48.03, r: 3.12, opacity: 0.37, slot: .shaded))
        #expect(light == LitEllipse(cx: 95, cy: 60, rx: 85, ry: 40, rotate: -16, opacity: 0.16))
    }

    @Test("pits lava nine times in its dark")
    func lava() throws {
        guard case let .lava(dots, light) = Recipe.of(.medium, .lowlight, seed: seed) else {
            throw RecipeShapeError.notAGem
        }
        #expect(dots.count == 9)
        #expect(dots.allSatisfy { $0.slot == .dark })
        #expect(dots[0] == LitDot(cx: 175.94, cy: 197.14, r: 4.83, opacity: 0.59, slot: .dark))
        #expect(dots[1] == LitDot(cx: 162.01, cy: 172.48, r: 4.22, opacity: 0.36, slot: .dark))
        #expect(abs(light.cx - 88.4) < 1e-9)
        #expect(abs(light.cy - 62.4) < 1e-9)
        #expect(abs(light.rx - 67.6) < 1e-9)
        #expect(abs(light.ry - 39) < 1e-9)
        #expect(light.rotate == 0)
        #expect(light.opacity == 0.07)
    }

    @Test("keeps every dot inside the canvas and its radius the same size on screen")
    func dotsStayInside() {
        for (size, width, px) in [(ValenceSizeGroup.small, 250.0, 34.0), (.large, 260.0, 54.0)] {
            guard case let .lava(dots, _) = Recipe.of(size, .lowlight, seed: seed) else {
                Issue.record("expected lava")
                return
            }
            for d in dots {
                #expect(d.cx > 0 && d.cx < width)
                let onScreen = d.r * px / width
                #expect(onScreen >= 0.4 - 0.01 && onScreen <= 0.99 + 0.01)
            }
        }
    }

    @Test("glint matches the mock's medium glint")
    func glint() {
        let glint = Recipe.glint(.medium)
        #expect(abs(glint.sheen.cx - 98.8) < 1e-9)
        #expect(abs(glint.sheen.cy - 78) < 1e-9)
        #expect(abs(glint.sheen.rx - 78) < 1e-9)
        #expect(glint.sheen.rotate == -24)
        #expect(abs(glint.travel - 416) < 1e-9)
        #expect(abs(glint.sweep.x + 143) < 1e-9)
        #expect(abs(glint.sweep.y + 104) < 1e-9)
        #expect(abs(glint.sweep.height - 468) < 1e-9)
    }

    @Test("starPath draws the mock's four-point spark")
    func starPath() {
        let d = "M82.5 28.75Q85.9 46.6 103.75 50Q85.9 53.4 82.5 71.25Q79.1 53.4 61.25 50Q79.1 46.6 82.5 28.75Z"
        #expect(Recipe.starPathData(cx: 82.5, cy: 50, r: 21.25) == d)
        #expect(Recipe.starPath(cx: 82.5, cy: 50, r: 21.25) == star(d))
    }
}
