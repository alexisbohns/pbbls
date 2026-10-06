import CoreGraphics
import Foundation
import ImageIO
import Testing
import UniformTypeIdentifiers
@testable import Pebbles

@Suite("ImagePipeline opaque encode")
struct ImagePipelineOpaqueTests {

    private static let opaqueAlphaInfos: Set<UInt32> = [
        CGImageAlphaInfo.none.rawValue,
        CGImageAlphaInfo.noneSkipLast.rawValue,
        CGImageAlphaInfo.noneSkipFirst.rawValue
    ]

    /// An opaque photo-like fill stored in the RGBA `.premultipliedLast`
    /// layout that `CGImageSourceCreateThumbnailAtIndex` hands back.
    private func premultipliedImage(width: Int, height: Int) throws -> CGImage {
        let space = try #require(CGColorSpace(name: CGColorSpace.sRGB))
        let context = try #require(CGContext(
            data: nil,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: 0,
            space: space,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ))
        context.setFillColor(red: 0.2, green: 0.5, blue: 0.8, alpha: 1)
        context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        return try #require(context.makeImage())
    }

    private func pngData(_ image: CGImage) throws -> Data {
        let buffer = NSMutableData()
        let destination = try #require(CGImageDestinationCreateWithData(
            buffer as CFMutableData, UTType.png.identifier as CFString, 1, nil
        ))
        CGImageDestinationAddImage(destination, image, nil)
        #expect(CGImageDestinationFinalize(destination))
        return buffer as Data
    }

    @Test("makeOpaque drops the alpha channel and keeps the pixel size")
    func stripsAlpha() throws {
        let rgba = try premultipliedImage(width: 236, height: 420)
        #expect(rgba.alphaInfo == .premultipliedLast)

        let opaque = ImagePipeline.makeOpaque(rgba)

        #expect(Self.opaqueAlphaInfos.contains(opaque.alphaInfo.rawValue))
        #expect(opaque.width == 236)
        #expect(opaque.height == 420)
    }

    @Test("makeOpaque returns an already-opaque image untouched")
    func leavesOpaqueImageAlone() throws {
        let opaque = ImagePipeline.makeOpaque(try premultipliedImage(width: 8, height: 8))
        #expect(ImagePipeline.makeOpaque(opaque) === opaque)
    }

    @Test("process still emits JPEG original and thumb at the 1024 / 420 edges")
    func processStillEmitsSizedJPEGs() throws {
        let source = try pngData(try premultipliedImage(width: 1152, height: 2048))

        let processed = try ImagePipeline.process(source, uti: "public.png")

        for (data, maxEdge) in [(processed.original, 1024), (processed.thumb, 420)] {
            let decoded = try #require(CGImageSourceCreateWithData(data as CFData, nil))
            #expect(CGImageSourceGetType(decoded) as String? == UTType.jpeg.identifier)
            let image = try #require(CGImageSourceCreateImageAtIndex(decoded, 0, nil))
            #expect(max(image.width, image.height) == maxEdge)
        }
    }
}
