import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

/// The expected pixels are the web's own grain filter (`StoneSprite.tsx`:
/// `feTurbulence` fractalNoise 0.9 / 2 octaves / seed 7, the dark and light
/// colour matrices, `feMerge`), rendered at 64 × 64 in a browser and read back
/// with `getImageData`. They are unpremultiplied there; the comparison runs
/// on premultiplied values, because a tile stores them that way and the
/// browser's own canvas rounds at low alpha.
@Suite("Lit stone grain")
struct LitStoneGrainTests {

    private struct Pixel { let x: Int, y: Int, r: Int, g: Int, b: Int, a: Int }

    private static let golden: [Pixel] = [
        Pixel(x: 3, y: 5, r: 0, g: 0, b: 0, a: 20),
        Pixel(x: 17, y: 2, r: 0, g: 0, b: 0, a: 0),
        Pixel(x: 30, y: 40, r: 0, g: 0, b: 0, a: 0),
        Pixel(x: 45, y: 12, r: 249, g: 249, b: 249, a: 41),
        Pixel(x: 60, y: 60, r: 0, g: 0, b: 0, a: 0),
        Pixel(x: 8, y: 50, r: 0, g: 0, b: 0, a: 9),
        Pixel(x: 33, y: 27, r: 0, g: 0, b: 0, a: 0),
        Pixel(x: 52, y: 37, r: 0, g: 0, b: 0, a: 47)
    ]

    /// The tile's RGBA bytes, premultiplied, row by row.
    private func premultipliedBytes(_ image: CGImage) -> [UInt8] {
        var bytes = [UInt8](repeating: 0, count: image.width * image.height * 4)
        bytes.withUnsafeMutableBytes { buffer in
            let context = CGContext(
                data: buffer.baseAddress,
                width: image.width,
                height: image.height,
                bitsPerComponent: 8,
                bytesPerRow: image.width * 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            )
            context?.setBlendMode(.copy)
            context?.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
        }
        return bytes
    }

    @Test("the tile is 256 px square")
    func tileSize() {
        let tile = LitStoneGrain.tile(scale: 1)
        #expect(tile.width == 256)
        #expect(tile.height == 256)
    }

    @Test("eight pixels match web's grain within 2 per channel")
    func matchesWeb() {
        let tile = LitStoneGrain.tile(scale: 1)
        #expect(tile.width == 256 && tile.height == 256)
        let bytes = premultipliedBytes(tile)
        #expect(bytes.count == 256 * 256 * 4)
        #expect(Self.golden.contains { $0.a > 0 }, "the golden must not be all clear")

        var seenInk = false
        for pixel in Self.golden {
            let offset = (pixel.y * tile.width + pixel.x) * 4
            let want = [pixel.r, pixel.g, pixel.b].map { Int((Double($0 * pixel.a) / 255).rounded()) }
            let got = (0..<3).map { Int(bytes[offset + $0]) }
            let gotAlpha = Int(bytes[offset + 3])
            if gotAlpha > 0 { seenInk = true }
            for channel in 0..<3 {
                #expect(abs(got[channel] - want[channel]) <= 2, "(\(pixel.x), \(pixel.y)) channel \(channel): \(got[channel]) vs \(want[channel])")
            }
            #expect(abs(gotAlpha - pixel.a) <= 2, "(\(pixel.x), \(pixel.y)) alpha: \(gotAlpha) vs \(pixel.a)")
        }
        #expect(seenInk, "the tile must carry some ink at the sampled pixels")
    }

    /// Alpha read from the same filter drawn at 128 px, i.e. at a scale of 2.
    @Test("a scale of 2 matches web drawn at twice the size")
    func matchesWebAtScaleTwo() {
        let tile = LitStoneGrain.tile(scale: 2)
        let bytes = premultipliedBytes(tile)
        let golden: [(x: Int, y: Int, a: Int)] = [(10, 0, 70), (59, 0, 104), (1, 1, 32)]
        #expect(golden.allSatisfy { $0.a > 0 })
        for pixel in golden {
            let got = Int(bytes[(pixel.y * tile.width + pixel.x) * 4 + 3])
            #expect(abs(got - pixel.a) <= 2, "(\(pixel.x), \(pixel.y)) alpha: \(got) vs \(pixel.a)")
        }
    }

    @Test("two calls at one scale return the same image")
    func cachedPerScale() {
        #expect(LitStoneGrain.tile(scale: 3) === LitStoneGrain.tile(scale: 3))
        #expect(LitStoneGrain.tile(scale: 2) !== LitStoneGrain.tile(scale: 3))
    }
}
