import CoreGraphics
import Foundation

/// The stone's grain, rasterised once per display scale: web's `feTurbulence`
/// grain filter (`StoneSprite.tsx`) through Android's `grainTile`. Fractal noise
/// sampled per CSS px (so the specks keep their size at every scale), a black
/// speck where the noise's red runs high and a white one where its green does,
/// merged light over dark. Only the R and G channels are read.
///
/// The tile is full strength and meant to repeat; the painter applies the
/// material's strength and the seed's shift when it overlays it.
enum LitStoneGrain {

    /// The tile's side in device px.
    static let tilePx = 256

    private static let baseFrequency = 0.9
    private static let octaves = 2
    private static let seed = 7
    private static let sampleShift = 0.5
    private static let premultiplyBias = 0.625

    private static let noise = SVGTurbulence(seed: seed)

    /// Safe from any thread. Looked up by key only, never iterated.
    private static let lock = NSLock()
    nonisolated(unsafe) private static var tiles: [CGFloat: CGImage] = [:]

    /// The grain tile at a display scale. Two calls at one scale return the same image.
    static func tile(scale: CGFloat) -> CGImage {
        lock.lock()
        defer { lock.unlock() }
        if let cached = tiles[scale] { return cached }
        let image = render(scale: scale)
        tiles[scale] = image
        return image
    }

    private static func render(scale: CGFloat) -> CGImage {
        let side = tilePx
        var bytes = [UInt8](repeating: 0, count: side * side * 4)
        for y in 0..<side {
            for x in 0..<side {
                // Chrome samples at the pixel's centre, and its filter space sits half a CSS px in.
                let sampleX = (Double(x) + 0.5) / Double(scale) + sampleShift
                let sampleY = (Double(y) + 0.5) / Double(scale) + sampleShift
                // Each layer sits in an 8-bit buffer before the merge.
                let dark = byte(clamp(effective(0, sampleX, sampleY) * 6 - 3.5))
                let light = byte(clamp(effective(1, sampleX, sampleY) * 4 - 2.75))
                // Light over dark. Premultiplied, the white's colour is its own
                // coverage, so the grey needs no division.
                let coverage = light + dark * (1 - light)
                let offset = (y * side + x) * 4
                let white = UInt8((light * 255).rounded())
                bytes[offset] = white
                bytes[offset + 1] = white
                bytes[offset + 2] = white
                bytes[offset + 3] = UInt8((coverage * 255).rounded())
            }
        }
        let provider = CGDataProvider(data: Data(bytes) as CFData)
        guard let provider,
              let image = CGImage(
                width: side, height: side,
                bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: side * 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent
              )
        else {
            preconditionFailure("the grain tile's bitmap parameters are fixed")
        }
        return image
    }

    /// feTurbulence's channel value: the signed fractal sum mapped onto [0, 1].
    private static func channel(_ channel: Int, _ x: Double, _ y: Double) -> Double {
        clamp((noise.turbulence(channel: channel, x: x, y: y, baseFrequency: baseFrequency, octaves: octaves) + 1) / 2)
    }

    /// What the colour matrix reads back from the noise. Chrome keeps the noise
    /// as an 8-bit premultiplied image, colour scaled by the noise's own alpha
    /// (channel 3), so the matrix unpremultiplies a rounded value: harmless
    /// where that alpha is high, a visible wobble where it is low. The premultiply
    /// rounds a touch up (`premultiplyBias`), calibrated against the browser's
    /// pixels (512 of them agree within 2).
    private static func effective(_ colourChannel: Int, _ x: Double, _ y: Double) -> Double {
        let alpha = (channel(3, x, y) * 255 + 0.5).rounded(.down)
        guard alpha > 0 else { return 0 }
        let premultiplied = (channel(colourChannel, x, y) * channel(3, x, y) * 255 + premultiplyBias).rounded(.down)
        return clamp(premultiplied / alpha)
    }

    /// A value as it sits in an 8-bit buffer.
    private static func byte(_ value: Double) -> Double { (value * 255).rounded() / 255 }

    private static func clamp(_ value: Double) -> Double { min(max(value, 0), 1) }
}
