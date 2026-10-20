import CoreGraphics
import SwiftUI
import Testing
import UIKit
@testable import Pebbles

@Suite("Reference harness")
struct ReferenceImageTests {

    private struct NamingCase {
        let scheme: ReferenceScheme
        let locale: ReferenceLocale
        let dynamicType: DynamicTypeSize
        let name: String

        init(_ scheme: ReferenceScheme, _ locale: ReferenceLocale, _ dynamicType: DynamicTypeSize, _ name: String) {
            self.scheme = scheme
            self.locale = locale
            self.dynamicType = dynamicType
            self.name = name
        }
    }

    @Test("file names cover every scheme, locale and Dynamic Type combination")
    func fileNames() {
        let expected: [NamingCase] = [
            .init(.paper, .en, .large, "slide-paper-en.png"),
            .init(.paper, .fr, .large, "slide-paper-fr.png"),
            .init(.night, .en, .large, "slide-night-en.png"),
            .init(.night, .fr, .large, "slide-night-fr.png"),
            .init(.paper, .en, .accessibility2, "slide-paper-en-ax2.png"),
            .init(.paper, .fr, .accessibility2, "slide-paper-fr-ax2.png"),
            .init(.night, .en, .accessibility2, "slide-night-en-ax2.png"),
            .init(.night, .fr, .accessibility2, "slide-night-fr-ax2.png")
        ]
        for item in expected {
            let config = ReferenceConfig(scheme: item.scheme, locales: [item.locale], dynamicType: item.dynamicType)
            #expect(config.fileName("slide", locale: item.locale) == item.name)
        }
    }

    @Test("a reference only runs in the passes it declares")
    func localeSkip() {
        let englishOnly = ReferenceConfig(scheme: .paper, locales: [.en])
        #expect(englishOnly.runs(in: .en))
        #expect(!englishOnly.runs(in: .fr))
        let both = ReferenceConfig(scheme: .night, locales: [.en, .fr])
        #expect(both.runs(in: .fr))
    }

    @Test("the reference width is the iPhone 15's 393 pt")
    func defaultWidth() {
        #expect(ReferenceConfig(scheme: .paper).width == 393)
        #expect(ReferenceConfig(scheme: .paper).dynamicType == .large)
    }

    private func solid(_ width: Int, _ height: Int, rgba: [UInt8]) -> [UInt8] {
        Array((0..<(width * height)).map { _ in rgba }.joined())
    }

    @Test("identical images differ in no pixel")
    func identical() throws {
        let bytes = solid(10, 10, rgba: [10, 20, 30, 255])
        let a = try #require(ReferenceImage.image(fromRGBA: bytes, width: 10, height: 10))
        let b = try #require(ReferenceImage.image(fromRGBA: bytes, width: 10, height: 10))
        #expect(ReferenceImage.compare(a, b) == .differing(fraction: 0))
    }

    @Test("one channel of one pixel in a hundred is 1 %")
    func onePixel() throws {
        var bytes = solid(10, 10, rgba: [10, 20, 30, 255])
        let a = try #require(ReferenceImage.image(fromRGBA: bytes, width: 10, height: 10))
        bytes[4 * 37 + 2] = 31
        let b = try #require(ReferenceImage.image(fromRGBA: bytes, width: 10, height: 10))
        #expect(ReferenceImage.compare(a, b) == .differing(fraction: 0.01))
        #expect(0.01 > ReferenceImage.threshold)
    }

    @Test("a size mismatch always fails")
    func sizeMismatch() throws {
        let a = try #require(ReferenceImage.image(fromRGBA: solid(10, 10, rgba: [0, 0, 0, 255]), width: 10, height: 10))
        let b = try #require(ReferenceImage.image(fromRGBA: solid(10, 11, rgba: [0, 0, 0, 255]), width: 10, height: 11))
        #expect(ReferenceImage.compare(a, b) == .sizeMismatch)
    }

    @Test("an undecodable side never compares equal, even to another")
    func undecodable() throws {
        let image = try #require(ReferenceImage.image(fromRGBA: solid(2, 2, rgba: [0, 0, 0, 255]), width: 2, height: 2))
        let pixels = try #require(ReferenceImage.pixels(image))
        #expect(ReferenceImage.compare(nil, pixels) == .undecodable)
        #expect(ReferenceImage.compare(pixels, nil) == .undecodable)
        #expect(ReferenceImage.compare(nil, nil) == .undecodable)
    }

    @Test("the diff paints differing pixels magenta over a grey copy")
    func diffImage() throws {
        var bytes = solid(2, 1, rgba: [255, 255, 255, 255])
        let reference = try #require(ReferenceImage.image(fromRGBA: bytes, width: 2, height: 1))
        bytes[0] = 0
        let actual = try #require(ReferenceImage.image(fromRGBA: bytes, width: 2, height: 1))
        let diff = try #require(ReferenceImage.diffImage(actual, reference))
        #expect(ReferenceImage.rgba(diff) == [255, 0, 255, 255, 255, 255, 255, 255])
    }

    @Test("references sit beside the suite file; diffs go to apps/ios/build")
    func paths() {
        let paths = ReferenceImage.paths(
            file: "nineStones-paper-en.png",
            filePath: "/r/apps/ios/PebblesTests/References/StoneReferences.swift"
        )
        #expect(paths.reference.path
                == "/r/apps/ios/PebblesTests/References/__References__/StoneReferences/nineStones-paper-en.png")
        #expect(paths.actual.path == "/r/apps/ios/build/reference-diffs/StoneReferences/nineStones-paper-en.actual.png")
        #expect(paths.diff.path == "/r/apps/ios/build/reference-diffs/StoneReferences/nineStones-paper-en.diff.png")
    }

    @Test("a written PNG reads back byte for byte")
    func pngRoundTrip() throws {
        let bytes = solid(3, 2, rgba: [12, 34, 56, 255])
        let image = try #require(ReferenceImage.image(fromRGBA: bytes, width: 3, height: 2))
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("round-trip-\(UUID()).png")
        try ReferenceImage.writePNG(image, to: url)
        let read = try #require(ReferenceImage.readPNG(at: url))
        #expect(ReferenceImage.compare(image, read) == .differing(fraction: 0))
    }

    /// Role-free on purpose (stack B reads no colour role): a dynamic sRGB
    /// swatch resolved through the trait collection, so this checks both the
    /// SwiftUI environment and the `performAsCurrent` pin.
    @Test("scheme probe: paper renders white, Night renders black", arguments: ReferenceScheme.allCases)
    @MainActor
    func schemeProbe(scheme: ReferenceScheme) throws {
        let swatch = UIColor { $0.userInterfaceStyle == .dark ? .black : .white }
        let view = Rectangle().fill(Color(uiColor: swatch)).frame(width: 10, height: 10)
        let config = ReferenceConfig(scheme: scheme, width: nil)
        let image = try #require(ReferenceImage.render(view, config: config, locale: .en))
        #expect(image.width == 20 && image.height == 20)
        let bytes = try #require(ReferenceImage.rgba(image))
        let centre = (10 * 20 + 10) * 4
        let expected: UInt8 = scheme == .paper ? 255 : 0
        #expect(Array(bytes[centre..<centre + 4]) == [expected, expected, expected, 255])
    }
}
