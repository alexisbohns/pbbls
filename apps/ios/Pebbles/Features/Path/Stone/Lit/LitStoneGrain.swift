import CoreGraphics
import Foundation

/// The stone's grain, rasterised once per display scale: web's `feTurbulence`
/// grain filter (`StoneSprite.tsx`) through Android's `grainTile` / `grainPixel`
/// (`StonePainter.kt`), so the two native surfaces generate the same specks.
/// Fractal noise sampled per CSS px (the specks keep their size at every
/// scale), a black speck where the noise's red runs high and a white one where
/// its green does, merged light over dark. Only the R and G channels are read.
///
/// The tile is full strength and meant to repeat; the painter applies the
/// material's strength and the seed's shift when it overlays it. Its bytes are
/// straight (non-premultiplied) RGBA, as Android's bitmap holds them.
enum LitStoneGrain {

    /// The tile's side in device px.
    static let tilePx = 256

    private static let baseFrequency = 0.9
    private static let octaves = 2
    private static let seed = 7

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
                let sampleX = Double(CGFloat(x) / scale)
                let sampleY = Double(CGFloat(y) / scale)
                let dark = clamp(channel(0, sampleX, sampleY) * 6 - 3.5)
                let light = clamp(channel(1, sampleX, sampleY) * 4 - 2.75)
                // Light over dark.
                let coverage = light + dark * (1 - light)
                guard coverage > 0 else { continue }
                let grey = UInt8((light / coverage * 255).rounded())
                let offset = (y * side + x) * 4
                bytes[offset] = grey
                bytes[offset + 1] = grey
                bytes[offset + 2] = grey
                bytes[offset + 3] = UInt8((coverage * 255).rounded())
            }
        }
        let provider = CGDataProvider(data: Data(bytes) as CFData)
        guard let provider,
              let image = CGImage(
                width: side, height: side,
                bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: side * 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.last.rawValue),
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

    private static func clamp(_ value: Double) -> Double { min(max(value, 0), 1) }
}
