import CoreGraphics
import Foundation
import os

/// The nine bundled valence artworks, wobbled once and kept.
///
/// The SVGs in `Resources/ValenceArt/` are generated from the vector PDFs by
/// `Scripts/valence-art-to-svg.mjs` and committed. Each artwork holds two kinds
/// of path, wobbled the two ways the renderer already knows:
///
/// - **Stroked** paths are centerlines carrying the width the PDF drew them
///   with, inked through `WobbleRenderer.glyphInk` — the leaky-outline pass a
///   carved glyph goes through. Their own widths rather than the uniform
///   `PebbleStroke.outlineWidth` a real pebble uses: the box is ~190 units and
///   the detail is fine, so the pebble weight is too heavy here.
/// - **Filled** paths (one per artwork: the fossil's spiral) go through
///   `WobbleRenderer.backdropArt`, which displaces a region's contours instead
///   of inking a line. Tracing a filled spiral as a centerline fills it in
///   solid, and the fossil reads as a blob.
enum ValenceArt {

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "valence-art")

    /// A filled region of an artwork, already displaced.
    struct Region {
        let path: CGPath
        let usesEvenOddFill: Bool
    }

    /// One artwork's wobbled art, in its own viewBox space.
    /// `@unchecked Sendable` for the same reason as `WobbleBackdropArt`: every
    /// `CGPath` here is an immutable copy nothing mutates after `init`, and
    /// CGPath predates `Sendable`.
    final class Art: @unchecked Sendable {
        let viewBox: CGRect
        /// Every stroked path's leaky ink, merged. Always nonzero-filled.
        let ink: CGPath
        /// The artwork's filled regions, each with its own fill rule.
        let regions: [Region]

        init(viewBox: CGRect, ink: CGPath, regions: [Region]) {
            self.viewBox = viewBox
            self.ink = ink
            self.regions = regions
        }
    }

    /// Guards look-up → build → store. `ValenceStoneView` bodies run on the
    /// main actor today, but a cache that is only accidentally single-threaded
    /// is exactly the bug #650 fixed in `WobbleRenderer`.
    private static let lock = NSLock()
    private static var cache: [Valence: Art] = [:]

    /// Nil only when the bundled asset is missing or unparseable — a setup bug,
    /// logged for the caller to render around.
    static func art(for valence: Valence) -> Art? {
        lock.lock()
        defer { lock.unlock() }

        if let cached = cache[valence] { return cached }
        guard let built = build(valence) else { return nil }
        cache[valence] = built
        return built
    }

    /// Builds all nine ahead of the picker needing them. Wobbling the whole
    /// set costs ~70ms on a Mac, which is a visible hitch if it lands on the
    /// main thread the moment the valence step appears. Safe to call off the
    /// main actor, and safe to call twice — the cache absorbs the second.
    static func prewarm() {
        for valence in Valence.allCases { _ = art(for: valence) }
    }

    private static func build(_ valence: Valence) -> Art? {
        let name = valence.assetName
        guard let url = Bundle.main.url(forResource: name, withExtension: "svg"),
              let svg = try? String(contentsOf: url, encoding: .utf8) else {
            logger.error("missing valence artwork: \(name, privacy: .public).svg")
            return nil
        }
        guard let viewBox = viewBox(in: svg) else {
            logger.error("valence artwork has no viewBox: \(name, privacy: .public).svg")
            return nil
        }

        let combined = CGMutablePath()
        var regions: [Region] = []
        var wobbled = 0

        for element in paths(in: svg) {
            if element.isFilled {
                // `backdropArt` takes asset markup rather than a path, so the
                // region is handed to it as the one-path asset it expects.
                let asset = """
                <svg viewBox="\(viewBox.minX) \(viewBox.minY) \(viewBox.width) \(viewBox.height)">\
                <path d="\(element.pathData)"\(element.usesEvenOddFill ? " fill-rule=\"evenodd\"" : "")/></svg>
                """
                guard let art = WobbleRenderer.backdropArt(fromAsset: asset) else { continue }
                regions.append(Region(path: art.path, usesEvenOddFill: art.usesEvenOddFill))
            } else {
                guard let ink = WobbleRenderer.glyphInk(d: element.pathData, width: element.width) else { continue }
                combined.addPath(ink)
            }
            wobbled += 1
        }

        guard wobbled > 0 else {
            logger.error("valence artwork wobbled no paths: \(name, privacy: .public).svg")
            return nil
        }

        return Art(viewBox: viewBox, ink: combined.copy() ?? combined, regions: regions)
    }

    // MARK: - Parsing

    private struct Element {
        let pathData: String
        /// Meaningless when `isFilled`.
        let width: Double
        let isFilled: Bool
        let usesEvenOddFill: Bool
    }

    /// The generated files are flat lists of `<path>` elements the companion
    /// script writes, so a scan beats standing up an `XMLParser` delegate.
    private static let pathPattern = try? NSRegularExpression(pattern: "<path\\s+([^>]*)/>")
    private static let attributePattern = try? NSRegularExpression(
        pattern: "([\\w-]+)=\"([^\"]*)\""
    )

    private static func paths(in svg: String) -> [Element] {
        guard let pathPattern, let attributePattern else { return [] }
        return pathPattern.matches(in: svg, range: NSRange(svg.startIndex..., in: svg))
            .compactMap { match -> Element? in
                guard let range = Range(match.range(at: 1), in: svg) else { return nil }
                let body = String(svg[range])

                var attributes: [String: String] = [:]
                for attribute in attributePattern.matches(
                    in: body, range: NSRange(body.startIndex..., in: body)
                ) {
                    guard let keyRange = Range(attribute.range(at: 1), in: body),
                          let valueRange = Range(attribute.range(at: 2), in: body) else { continue }
                    attributes[String(body[keyRange])] = String(body[valueRange])
                }

                guard let pathData = attributes["d"] else { return nil }
                let fill = attributes["fill"] ?? "none"
                return Element(
                    pathData: pathData,
                    width: Double(attributes["stroke-width"] ?? "") ?? 0,
                    isFilled: fill != "none",
                    usesEvenOddFill: attributes["fill-rule"] == "evenodd"
                )
            }
    }

    private static func viewBox(in svg: String) -> CGRect? {
        guard let regex = try? NSRegularExpression(pattern: "viewBox=\"([^\"]*)\""),
              let match = regex.firstMatch(in: svg, range: NSRange(svg.startIndex..., in: svg)),
              let range = Range(match.range(at: 1), in: svg) else { return nil }
        let parts = svg[range]
            .split(whereSeparator: { $0 == " " || $0 == "," })
            .compactMap { Double($0) }
        guard parts.count == 4, parts[2] > 0, parts[3] > 0 else { return nil }
        return CGRect(x: parts[0], y: parts[1], width: parts[2], height: parts[3])
    }
}
