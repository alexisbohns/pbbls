import CoreGraphics
import Foundation
import ImageIO
import Testing
import UniformTypeIdentifiers
@testable import Pebbles

@Suite("CropSource")
struct CropSourceTests {
    private func jpeg(width: Int, height: Int) throws -> Data {
        let image = try ImagePipelineCroppedTests.solid(width: width, height: height)
        let buffer = NSMutableData()
        let destination = try #require(CGImageDestinationCreateWithData(
            buffer as CFMutableData, UTType.jpeg.identifier as CFString, 1, nil))
        CGImageDestinationAddImage(destination, image, nil)
        #expect(CGImageDestinationFinalize(destination))
        return buffer as Data
    }

    @Test("bytes become a working image capped at 2048")
    func loads() async throws {
        let source = try #require(await CropSource.make(from: try jpeg(width: 3000, height: 4000)))
        #expect(source.image.height == 2048)
        #expect(source.captureDate == nil)
    }

    @Test("bytes that are not an image give nothing")
    func rejects() async {
        #expect(await CropSource.make(from: Data("not an image".utf8)) == nil)
    }
}
