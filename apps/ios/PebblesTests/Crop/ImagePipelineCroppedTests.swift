import CoreGraphics
import Foundation
import ImageIO
import Testing
import UniformTypeIdentifiers
@testable import Pebbles

@Suite("ImagePipeline cropped input")
struct ImagePipelineCroppedTests {
    static func solid(width: Int, height: Int) throws -> CGImage {
        let space = try #require(CGColorSpace(name: CGColorSpace.sRGB))
        let context = try #require(CGContext(data: nil, width: width, height: height, bitsPerComponent: 8,
                                             bytesPerRow: 0, space: space,
                                             bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        context.setFillColor(red: 0.3, green: 0.6, blue: 0.2, alpha: 1)
        context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        return try #require(context.makeImage())
    }

    private func pixelSize(_ jpeg: Data) throws -> CGSize {
        let source = try #require(CGImageSourceCreateWithData(jpeg as CFData, nil))
        let props = try #require(CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any])
        let w = try #require(props[kCGImagePropertyPixelWidth] as? Int)
        let h = try #require(props[kCGImagePropertyPixelHeight] as? Int)
        return CGSize(width: w, height: h)
    }

    @Test("a cropped image comes out as a 1024 original and a 420 thumb, ratio kept")
    func sizes() throws {
        let processed = try ImagePipeline.process(Self.solid(width: 1600, height: 2000))
        #expect(try pixelSize(processed.original) == CGSize(width: 819, height: 1024))
        #expect(try pixelSize(processed.thumb) == CGSize(width: 336, height: 420))
    }

    @Test("a small crop is not upscaled")
    func noUpscale() throws {
        let processed = try ImagePipeline.process(Self.solid(width: 600, height: 600))
        #expect(try pixelSize(processed.original) == CGSize(width: 600, height: 600))
    }

    @Test("no capture metadata, GPS or TIFF dictionary is written")
    func noMetadata() throws {
        let processed = try ImagePipeline.process(Self.solid(width: 800, height: 1000))
        for data in [processed.original, processed.thumb] {
            let source = try #require(CGImageSourceCreateWithData(data as CFData, nil))
            let props = try #require(CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any])
            // ImageIO always writes the structural EXIF keys of a JPEG; what
            // must never appear is anything describing the shot or the place.
            let exif = props[kCGImagePropertyExifDictionary] as? [CFString: Any] ?? [:]
            let structural: Set<String> = [
                kCGImagePropertyExifColorSpace as String,
                kCGImagePropertyExifPixelXDimension as String,
                kCGImagePropertyExifPixelYDimension as String
            ]
            #expect(Set(exif.keys.map { $0 as String }).isSubset(of: structural))
            #expect(props[kCGImagePropertyGPSDictionary] == nil)
            #expect(props[kCGImagePropertyTIFFDictionary] == nil)
        }
    }

    @Test("decodeOriented caps the long edge")
    func decodeOriented() throws {
        let buffer = NSMutableData()
        let destination = try #require(CGImageDestinationCreateWithData(
            buffer as CFMutableData, UTType.png.identifier as CFString, 1, nil))
        CGImageDestinationAddImage(destination, try Self.solid(width: 3000, height: 4000), nil)
        #expect(CGImageDestinationFinalize(destination))
        let image = try ImagePipeline.decodeOriented(buffer as Data, maxEdge: 2048)
        #expect(image.width == 1536 && image.height == 2048)
    }

    @Test("decodeOriented never upscales a small photo")
    func decodeOrientedKeepsSmall() throws {
        let buffer = NSMutableData()
        let destination = try #require(CGImageDestinationCreateWithData(
            buffer as CFMutableData, UTType.png.identifier as CFString, 1, nil))
        CGImageDestinationAddImage(destination, try Self.solid(width: 600, height: 800), nil)
        #expect(CGImageDestinationFinalize(destination))
        let image = try ImagePipeline.decodeOriented(buffer as Data, maxEdge: 2048)
        #expect(image.width == 600 && image.height == 800)
    }

    /// Deterministic noise (splitmix64), so the byte-cap test never flakes.
    private static func noise(width: Int, height: Int, amplitude: Int) throws -> CGImage {
        var state: UInt64 = 0x9E37_79B9_7F4A_7C15
        func next() -> UInt64 {
            state &+= 0x9E37_79B9_7F4A_7C15
            var z = state
            z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
            z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
            return z ^ (z >> 31)
        }
        var pixels = [UInt8](repeating: 255, count: width * height * 4)
        for i in 0..<(width * height) {
            let base = 128 + (i % width) * 40 / width
            for c in 0..<3 {
                pixels[i * 4 + c] = UInt8(max(0, min(255, base + Int(next() % UInt64(amplitude * 2 + 1)) - amplitude)))
            }
        }
        let space = try #require(CGColorSpace(name: CGColorSpace.sRGB))
        let provider = try #require(CGDataProvider(data: Data(pixels) as CFData))
        return try #require(CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32,
                                    bytesPerRow: width * 4, space: space,
                                    bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipLast.rawValue),
                                    provider: provider, decode: nil, shouldInterpolate: true,
                                    intent: .defaultIntent))
    }

    @Test("a noisy 1024 px crop still fits the 1 MB and 300 KB caps", arguments: [40, 127])
    func byteCaps(amplitude: Int) throws {
        let processed = try ImagePipeline.process(Self.noise(width: 1024, height: 1024, amplitude: amplitude))
        #expect(processed.original.count <= 1_048_576)
        #expect(processed.thumb.count <= 307_200)
    }
}
