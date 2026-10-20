import CoreGraphics
import Foundation
import ImageIO
import SwiftUI
import Testing
import UIKit
import UniformTypeIdentifiers

/// The iOS reference-image gate (#1159). Renders a view with `ImageRenderer`,
/// compares it pixel for pixel with a committed PNG, and records a new PNG only
/// when the process sees `PBBLS_RECORD_REFERENCES=1` (xcodebuild passes it in as
/// `TEST_RUNNER_PBBLS_RECORD_REFERENCES=1`). References are rendered on CI only
/// (`ios-references.yml`, the `rebaseline-ios-screenshots` label): a local render
/// is for looking at, never for committing.
///
/// New pattern (raised in the B2 PR): a hand-rolled harness, no dependency, the
/// same metric as Android's gate (the fraction of pixels that differ).
enum ReferenceImage {
    /// The fraction of pixels allowed to differ, exact per pixel (any RGBA
    /// channel). Calibrated in B2 with at least 2× headroom against a one-word
    /// re-wording (see the plan's Lessons). NEVER widen it: a change that moves
    /// pixels on purpose re-baselines. If calibration shows less headroom, lower it.
    static let threshold = 0.0005

    /// 2× keeps PNGs small; parity with Android is judged by eye.
    static let scale: CGFloat = 2

    enum Comparison: Equatable {
        /// One side could not be drawn into `Pixels`. Always a failure: two
        /// bad decodes must never compare equal.
        case undecodable
        case sizeMismatch
        case differing(fraction: Double)
    }

    /// sRGB, 8 bits per channel, RGBA order with premultiplied alpha last: the
    /// one layout every compare reads.
    struct Pixels: Equatable {
        let bytes: [UInt8]
        let width: Int
        let height: Int
    }

    struct Paths {
        let reference: URL
        let actual: URL
        let diff: URL
    }

    enum ReferenceError: Error {
        case cannotWrite(URL)
    }

    static var isRecording: Bool {
        ProcessInfo.processInfo.environment["PBBLS_RECORD_REFERENCES"] == "1"
    }

    // MARK: - Pixels

    /// Draws `image` into the `Pixels` layout; nil when no colour space or
    /// bitmap context could be made, never a zero-filled buffer.
    static func pixels(_ image: CGImage) -> Pixels? {
        guard let space = CGColorSpace(name: CGColorSpace.sRGB) else { return nil }
        var bytes = [UInt8](repeating: 0, count: image.width * image.height * 4)
        let drawn = bytes.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(
                data: buffer.baseAddress, width: image.width, height: image.height, bitsPerComponent: 8,
                bytesPerRow: image.width * 4, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return false }
            context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
            return true
        }
        return drawn ? Pixels(bytes: bytes, width: image.width, height: image.height) : nil
    }

    /// The `Pixels` bytes of `image`, or nil when it cannot be decoded.
    static func rgba(_ image: CGImage) -> [UInt8]? {
        pixels(image)?.bytes
    }

    static func image(fromRGBA bytes: [UInt8], width: Int, height: Int) -> CGImage? {
        guard bytes.count == width * height * 4,
              let space = CGColorSpace(name: CGColorSpace.sRGB),
              let provider = CGDataProvider(data: Data(bytes) as CFData) else { return nil }
        return CGImage(
            width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: width * 4,
            space: space, bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
            provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent
        )
    }

    /// Re-draws any image into the `Pixels` layout, so a recorded PNG and a
    /// fresh render are compared in the same bytes.
    static func normalized(_ image: CGImage) -> CGImage? {
        pixels(image).flatMap { Self.image(fromRGBA: $0.bytes, width: $0.width, height: $0.height) }
    }

    private static func differs(_ a: [UInt8], _ b: [UInt8], at i: Int) -> Bool {
        a[i] != b[i] || a[i + 1] != b[i + 1] || a[i + 2] != b[i + 2] || a[i + 3] != b[i + 3]
    }

    static func compare(_ actual: CGImage, _ reference: CGImage) -> Comparison {
        compare(pixels(actual), pixels(reference))
    }

    static func compare(_ actual: Pixels?, _ reference: Pixels?) -> Comparison {
        guard let actual, let reference else { return .undecodable }
        guard actual.width == reference.width, actual.height == reference.height else { return .sizeMismatch }
        let count = actual.width * actual.height
        guard count > 0 else { return .differing(fraction: 0) }
        let a = actual.bytes
        let b = reference.bytes
        var differing = 0
        for i in stride(from: 0, to: a.count, by: 4) where differs(a, b, at: i) {
            differing += 1
        }
        return .differing(fraction: Double(differing) / Double(count))
    }

    static func diffImage(_ actual: CGImage, _ reference: CGImage) -> CGImage? {
        guard let a = pixels(actual), let b = pixels(reference) else { return nil }
        return diffImage(a, b)
    }

    /// Differing pixels in magenta over a 30 % grey copy of the reference.
    static func diffImage(_ actual: Pixels, _ reference: Pixels) -> CGImage? {
        guard actual.width == reference.width, actual.height == reference.height else { return nil }
        let a = actual.bytes
        let b = reference.bytes
        var out = [UInt8](repeating: 255, count: a.count)
        for i in stride(from: 0, to: a.count, by: 4) {
            if differs(a, b, at: i) {
                out[i] = 255; out[i + 1] = 0; out[i + 2] = 255
            } else {
                let luma = (Int(b[i]) * 299 + Int(b[i + 1]) * 587 + Int(b[i + 2]) * 114) / 1000
                let grey = UInt8(255 - (255 - luma) * 3 / 10)
                out[i] = grey; out[i + 1] = grey; out[i + 2] = grey
            }
        }
        return image(fromRGBA: out, width: actual.width, height: actual.height)
    }

    // MARK: - Files

    /// `filePath` is the suite file's `#filePath`; the simulator shares the host
    /// filesystem, so nothing is bundled.
    static func paths(file: String, filePath: String) -> Paths {
        let suiteFile = URL(fileURLWithPath: filePath)
        let suite = suiteFile.deletingPathExtension().lastPathComponent
        let referencesDir = suiteFile.deletingLastPathComponent()
        let iosRoot = referencesDir.deletingLastPathComponent().deletingLastPathComponent()
        let base = (file as NSString).deletingPathExtension
        let diffs = iosRoot.appendingPathComponent("build/reference-diffs/\(suite)")
        return Paths(
            reference: referencesDir.appendingPathComponent("__References__/\(suite)/\(file)"),
            actual: diffs.appendingPathComponent("\(base).actual.png"),
            diff: diffs.appendingPathComponent("\(base).diff.png")
        )
    }

    static func readPNG(at url: URL) -> CGImage? {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
        return CGImageSourceCreateImageAtIndex(source, 0, nil)
    }

    static func writePNG(_ image: CGImage, to url: URL) throws {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        guard let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)
        else { throw ReferenceError.cannotWrite(url) }
        CGImageDestinationAddImage(destination, image, nil)
        guard CGImageDestinationFinalize(destination) else { throw ReferenceError.cannotWrite(url) }
    }

    // MARK: - Render and assert

    /// Renders under every pin of `config` and `locale`, then normalises the bytes.
    @MainActor
    static func render<V: View>(_ view: V, config: ReferenceConfig, locale: ReferenceLocale) -> CGImage? {
        let content = view
            .frame(width: config.width)
            .environment(\.colorScheme, config.scheme.colorScheme)
            .environment(\.locale, Locale(identifier: locale.rawValue))
            .environment(\.timeZone, TimeZone(identifier: "Europe/Paris") ?? .gmt)
            .environment(\.dynamicTypeSize, config.dynamicType)
        let renderer = ImageRenderer(content: content)
        renderer.scale = scale
        renderer.proposedSize = ProposedViewSize(width: config.width, height: nil)
        var image: CGImage?
        // A UIColor resolved against `UITraitCollection.current` ignores the
        // SwiftUI environment; this makes it follow the scheme too.
        UITraitCollection(userInterfaceStyle: config.scheme.userInterfaceStyle).performAsCurrent {
            image = renderer.cgImage
        }
        return image.flatMap(normalized)
    }

    /// Compares `view` with `__References__/<Suite>/<name>-<scheme>-<locale>[-ax2].png`
    /// beside the calling suite file. A missing reference fails; it is never
    /// recorded implicitly. Returns without a check in a locale pass the config
    /// does not declare.
    @MainActor
    static func assertMatches<V: View>(
        _ view: V,
        named name: String,
        config: ReferenceConfig,
        filePath: String = #filePath,
        sourceLocation: SourceLocation = #_sourceLocation
    ) {
        let locale = ReferenceLocale.current
        guard config.runs(in: locale) else { return }
        let paths = paths(file: config.fileName(name, locale: locale), filePath: filePath)
        guard let actual = render(view, config: config, locale: locale) else {
            Issue.record("\(name): ImageRenderer produced no image", sourceLocation: sourceLocation)
            return
        }
        if isRecording {
            do {
                try writePNG(actual, to: paths.reference)
            } catch {
                Issue.record("\(name): could not record \(paths.reference.path): \(error)",
                             sourceLocation: sourceLocation)
            }
            return
        }
        guard let reference = readPNG(at: paths.reference) else {
            try? writePNG(actual, to: paths.actual)
            Issue.record("""
                \(name): no reference at \(paths.reference.path). References are rendered on CI only: \
                add the rebaseline-ios-screenshots label to the PR. Actual at \(paths.actual.path)
                """, sourceLocation: sourceLocation)
            return
        }
        check(actual, against: reference, named: name, paths: paths, sourceLocation: sourceLocation)
    }

    /// Records a failure when `actual` and `reference` differ beyond the
    /// threshold, writing the actual and the diff beside each other.
    private static func check(
        _ actual: CGImage,
        against reference: CGImage,
        named name: String,
        paths: Paths,
        sourceLocation: SourceLocation
    ) {
        // Decoded once: the compare and the diff read the same buffers.
        let actualPixels = pixels(actual)
        let referencePixels = pixels(reference)
        switch compare(actualPixels, referencePixels) {
        case .undecodable:
            try? writePNG(actual, to: paths.actual)
            let side = actualPixels == nil ? "the render" : "the reference at \(paths.reference.path)"
            Issue.record("""
                \(name): \(side) could not be decoded into sRGB RGBA8. Actual at \(paths.actual.path)
                """, sourceLocation: sourceLocation)
        case .sizeMismatch:
            try? writePNG(actual, to: paths.actual)
            Issue.record("""
                \(name): size \(actual.width)×\(actual.height) px ≠ \
                reference \(reference.width)×\(reference.height) px. Actual at \(paths.actual.path)
                """, sourceLocation: sourceLocation)
        case .differing(let fraction) where fraction > threshold:
            try? writePNG(actual, to: paths.actual)
            if let actualPixels, let referencePixels, let diff = diffImage(actualPixels, referencePixels) {
                try? writePNG(diff, to: paths.diff)
            }
            let percent = String(format: "%.4f", fraction * 100)
            Issue.record("""
                \(name): \(percent) % of pixels differ (threshold \(threshold * 100) %). \
                Actual and diff in \(paths.diff.deletingLastPathComponent().path)
                """, sourceLocation: sourceLocation)
        case .differing:
            return
        }
    }
}
