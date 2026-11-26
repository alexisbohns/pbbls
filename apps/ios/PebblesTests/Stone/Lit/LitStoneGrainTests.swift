import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

/// Web's numbers come from its own grain filter (`StoneSprite.tsx`: `feTurbulence`
/// fractalNoise 0.9 / 2 octaves / seed 7, the dark and light colour matrices,
/// `feMerge`), drawn as a 64 x 64 SVG onto a 64 x 64 canvas in Chrome (browser
/// pane, dpr 2) and read back with `getImageData`, i.e. unpremultiplied RGBA.
/// Over all 4096 pixels:
/// - 1186 have alpha > 0 (28.96%);
/// - 918 of those are dark specks (R = G = B = 0), mean alpha 91.887 / 255;
/// - 268 are light specks (any other colour), mean alpha 60.183 / 255.
///
/// The tile is Android's `grainTile` model, not Chrome's rasteriser, so it is
/// held to those numbers statistically (10% relative), not pixel for pixel.
@Suite("Lit stone grain")
struct LitStoneGrainTests {

    private struct Stats {
        let fraction: Double
        let darkMeanAlpha: Double
        let lightMeanAlpha: Double
    }

    /// The tile's straight RGBA bytes, as stored.
    private func bytes(_ image: CGImage) throws -> [UInt8] {
        let data = try #require(image.dataProvider?.data)
        return Array(data as Data)
    }

    /// The same statistics web's 64 x 64 frame reports, over the tile's top-left 64 x 64 px.
    private func stats(_ image: CGImage) throws -> Stats {
        let pixels = try bytes(image)
        var inked = 0, dark = 0, light = 0
        var darkAlpha = 0.0, lightAlpha = 0.0
        for y in 0..<64 {
            for x in 0..<64 {
                let offset = (y * image.width + x) * 4
                let alpha = Double(pixels[offset + 3])
                guard alpha > 0 else { continue }
                inked += 1
                if pixels[offset] == 0 && pixels[offset + 1] == 0 && pixels[offset + 2] == 0 {
                    dark += 1
                    darkAlpha += alpha
                } else {
                    light += 1
                    lightAlpha += alpha
                }
            }
        }
        return Stats(
            fraction: Double(inked) / 4096,
            darkMeanAlpha: dark > 0 ? darkAlpha / Double(dark) : 0,
            lightMeanAlpha: light > 0 ? lightAlpha / Double(light) : 0
        )
    }

    private func within(_ value: Double, _ expected: Double, relative: Double = 0.10) -> Bool {
        abs(value - expected) <= expected * relative
    }

    @Test("the tile is 256 px square")
    func tileSize() {
        let tile = LitStoneGrain.tile(scale: 1)
        #expect(tile.width == 256)
        #expect(tile.height == 256)
    }

    @Test("the scale-1 frame has web's amount and strength of dark and light specks")
    func matchesWebStatistically() throws {
        let tile = LitStoneGrain.tile(scale: 1)
        #expect(tile.width == 256 && tile.height == 256)
        let got = try stats(tile)
        #expect(got.fraction > 0, "the frame must not be clear")
        #expect(within(got.fraction, 1186.0 / 4096), "nonzero fraction \(got.fraction) vs 0.2896")
        #expect(within(got.darkMeanAlpha, 91.887), "dark mean alpha \(got.darkMeanAlpha) vs 91.887")
        #expect(within(got.lightMeanAlpha, 60.183), "light mean alpha \(got.lightMeanAlpha) vs 60.183")
    }

    @Test("a scale of 2 is its own tile with a similar amount of ink")
    func scaleTwo() throws {
        let one = LitStoneGrain.tile(scale: 1)
        let two = LitStoneGrain.tile(scale: 2)
        #expect(one !== two)
        #expect(two === LitStoneGrain.tile(scale: 2))
        let base = try stats(one)
        let doubled = try stats(two)
        #expect(doubled.fraction > 0)
        #expect(within(doubled.fraction, base.fraction), "scale 2 fraction \(doubled.fraction) vs \(base.fraction)")
    }

    @Test("two calls at one scale return the same image")
    func cachedPerScale() {
        #expect(LitStoneGrain.tile(scale: 3) === LitStoneGrain.tile(scale: 3))
        #expect(LitStoneGrain.tile(scale: 2) !== LitStoneGrain.tile(scale: 3))
    }
}
