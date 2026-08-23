import CoreGraphics
import Foundation
import ImageIO
import Testing
import UniformTypeIdentifiers
@testable import Pebbles

/// Builds a real 4×4 JPEG, optionally carrying an EXIF `DateTimeOriginal`.
/// A real encoded image is used rather than a canned blob so the test exercises
/// the same `CGImageSource` path production does.
private func makeJPEG(dateTimeOriginal: String?) -> Data {
    let side = 4
    let context = CGContext(
        data: nil,
        width: side,
        height: side,
        bitsPerComponent: 8,
        bytesPerRow: 0,
        space: CGColorSpaceCreateDeviceRGB(),
        bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
    )!
    context.setFillColor(CGColor(red: 0.5, green: 0.5, blue: 0.5, alpha: 1))
    context.fill(CGRect(x: 0, y: 0, width: side, height: side))
    let image = context.makeImage()!

    let output = NSMutableData()
    let destination = CGImageDestinationCreateWithData(
        output, UTType.jpeg.identifier as CFString, 1, nil
    )!
    var properties: [CFString: Any] = [:]
    if let dateTimeOriginal {
        properties[kCGImagePropertyExifDictionary] = [
            kCGImagePropertyExifDateTimeOriginal: dateTimeOriginal
        ]
    }
    CGImageDestinationAddImage(destination, image, properties as CFDictionary)
    CGImageDestinationFinalize(destination)
    return output as Data
}

@Suite("ExifCaptureDate")
struct ExifCaptureDateTests {

    @Test("reads DateTimeOriginal as a local-time wall clock")
    func readsDateTimeOriginal() throws {
        let data = makeJPEG(dateTimeOriginal: "2026:08:14 17:32:05")

        let parsed = try #require(ExifCaptureDate.from(data))

        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .current
        let parts = calendar.dateComponents(
            [.year, .month, .day, .hour, .minute, .second], from: parsed
        )
        #expect(parts.year == 2026)
        #expect(parts.month == 8)
        #expect(parts.day == 14)
        #expect(parts.hour == 17)
        #expect(parts.minute == 32)
        #expect(parts.second == 5)
    }

    @Test("returns nil when the image carries no EXIF date")
    func nilWithoutExif() {
        #expect(ExifCaptureDate.from(makeJPEG(dateTimeOriginal: nil)) == nil)
    }

    @Test("returns nil for a malformed EXIF date rather than inventing one")
    func nilForMalformedDate() {
        #expect(ExifCaptureDate.from(makeJPEG(dateTimeOriginal: "not a date")) == nil)
    }

    @Test("returns nil for data that is not an image")
    func nilForNonImage() {
        #expect(ExifCaptureDate.from(Data("plainly not an image".utf8)) == nil)
    }

    @Test("returns nil for empty data")
    func nilForEmptyData() {
        #expect(ExifCaptureDate.from(Data()) == nil)
    }
}
