import CoreGraphics
import Foundation
import SwiftUI

/// The lit stone's carved glyph: one raw glyph stroke's wobbled centreline, still
/// a line, so the painter strokes it at its own constant on-screen weight. A port
/// of web's `wobbleGlyphLine` (`apps/web/lib/wobble/renderer.ts`): parse, flatten
/// at the params scaled to the glyph's own square `side`, displace every point.
/// Never outlined — `WobbleRenderer.glyphInk` is the leaky-ink sibling.
enum LitGlyphLine {
    private static let noise = SVGTurbulence(seed: WobbleParams.seed)

    /// Look-up-or-build is guarded, as in `WobbleRenderer`: a prewarm runs off the
    /// main actor while the cache paints on it. A cache by key — never iterated
    /// while drawing.
    private static let lock = NSLock()
    private static var memo: [String: Path?] = [:]
    private static let memoLimit = 512

    /// The wobbled centreline of `d`, in the glyph's own space, or nil when `d`
    /// doesn't parse or nothing renders. A lone tap comes back as a zero-length
    /// segment, which the painter's round cap draws as a dot.
    static func line(d: String, side: Double) -> Path? {
        let key = "\(side)|\(d)"
        lock.lock()
        defer { lock.unlock() }
        if let hit = memo[key] { return hit }
        let built = build(d: d, side: side)
        if memo.count >= memoLimit { memo.removeAll() }
        memo[key] = built
        return built
    }

    private static func build(d: String, side: Double) -> Path? {
        guard let parsed = SVGPathParser.parse(d), !parsed.isEmpty else { return nil }
        let params = WobbleParams.scaled(for: CGSize(width: side, height: side))
        let line = CGMutablePath()
        for polyline in WobblePathFlattener.flatten(parsed, step: params.flattenStep)
        where !polyline.points.isEmpty {
            let displaced = polyline.points.map { params.displace($0, using: noise) }
            line.move(to: displaced[0])
            for point in displaced.dropFirst() {
                line.addLine(to: point)
            }
            // A single tap is a dot, as on Android: a zero-length segment the round cap fills.
            if displaced.count == 1 { line.addLine(to: displaced[0]) }
            if polyline.isClosed { line.closeSubpath() }
        }
        guard !line.isEmpty else { return nil }
        return Path(line)
    }
}
