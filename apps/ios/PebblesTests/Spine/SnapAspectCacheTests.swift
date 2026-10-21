import CoreGraphics
import Foundation
import ImageIO
import Testing
import UniformTypeIdentifiers
@testable import Pebbles

/// #1130 D3: a photo's ratio is read from its header, cached by storage path for
/// the session, and a probe that times out yields nil once and is never retried.
/// Signing goes through the existing `FakeSignedURLProvider` (SnapURLCacheTests).
@Suite("SnapAspectCache")
@MainActor
struct SnapAspectCacheTests {
    /// Counts loads; `@unchecked` because the loader is `@Sendable` and the tests are serial.
    final class Loads: @unchecked Sendable {
        var count = 0
    }

    private static func png(width: Int, height: Int) -> Data {
        let space = CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB()
        let context = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                                space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        let data = NSMutableData()
        if let image = context?.makeImage(),
           let destination = CGImageDestinationCreateWithData(data, UTType.png.identifier as CFString, 1, nil) {
            CGImageDestinationAddImage(destination, image, nil)
            CGImageDestinationFinalize(destination)
        }
        return data as Data
    }

    private func cache(loads: Loads, timeout: Duration = .seconds(4),
                       load: @escaping @Sendable () async throws -> Data) -> (SnapAspectCache, FakeSignedURLProvider) {
        let provider = FakeSignedURLProvider()
        let urls = SnapURLCache(provider: provider, now: { Date() })
        let aspects = SnapAspectCache(urls: urls, load: { _ in
            loads.count += 1
            return try await load()
        }, timeout: timeout)
        return (aspects, provider)
    }

    @Test("reads the ratio from the header and caches it by path")
    func caches() async {
        let loads = Loads()
        let data = Self.png(width: 30, height: 20)
        let (aspects, provider) = cache(loads: loads) { data }
        #expect(await aspects.probe("a/b") == 1.5)
        #expect(await aspects.probe("a/b") == 1.5)
        #expect(aspects.ratio(for: "a/b") == 1.5)
        #expect(loads.count == 1)
        #expect(provider.callCount == 1)
    }

    @Test("a timeout yields nil once, and the path is not probed again this session")
    func timeout() async {
        let loads = Loads()
        let (aspects, _) = cache(loads: loads, timeout: .milliseconds(50)) {
            try await Task.sleep(for: .seconds(5))
            return Data()
        }
        #expect(await aspects.probe("slow") == nil)
        #expect(await aspects.probe("slow") == nil)
        #expect(loads.count == 1)
    }

    @Test("an unreadable body is a failure too, and is not retried")
    func unreadable() async {
        let loads = Loads()
        let (aspects, _) = cache(loads: loads) { Data("not an image".utf8) }
        #expect(await aspects.probe("bad") == nil)
        #expect(await aspects.probe("bad") == nil)
        #expect(loads.count == 1)
    }

    @Test("a primed ratio needs no probe")
    func primed() async {
        let loads = Loads()
        let (aspects, provider) = cache(loads: loads) { Data() }
        aspects.prime("fixture/x.jpg", ratio: 2 / 3)
        #expect(await aspects.probe("fixture/x.jpg") == 2 / 3)
        #expect(loads.count == 0 && provider.callCount == 0)
    }

    @Test("the header reader swaps the sides of a rotated photo")
    func header() {
        #expect(SnapAspectCache.ratio(ofImage: Self.png(width: 40, height: 10)) == 4)
        #expect(SnapAspectCache.ratio(ofImage: Data()) == nil)
        #expect(SnapAspectCache.ratio(width: 40, height: 10, orientation: 6) == 0.25)
        #expect(SnapAspectCache.ratio(width: 40, height: 10, orientation: 1) == 4)
    }
}
