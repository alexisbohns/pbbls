import CoreGraphics
import SwiftUI

/// The pebble shapes behind the Home shortcuts: one stone cut into three.
/// The row splits a wide rock with two leaning cuts and rounds every fresh
/// cut; the bento cuts one calm stone along its straight gutters. Ported from
/// the 2026-10-31 quick actions exploration (row "One rock, three", bento
/// "One stone, split" with calmer outer corners). The shapes are fixed: every
/// draw comes from `StoneRandom` on a constant seed.
enum ShortcutStones {
    static let gutter: CGFloat = 8

    /// Outlines for the row's three cells, left to right.
    @MainActor static func row(size: CGSize) -> [[CGPoint]] {
        let key = Key(kind: "row", lead: "", size: size, leadWidth: 0)
        if let hit = cache[key] { return hit }
        let pieces = splitRow(width: size.width, height: size.height)
        remember(key, pieces)
        return pieces
    }

    /// Outlines for the bento's three cells, in `HomeShortcut.bento(lead:)` order.
    @MainActor static func bento(size: CGSize, leadWidth: CGFloat, lead: HomeShortcut) -> [[CGPoint]] {
        let key = Key(kind: "bento", lead: lead.rawValue, size: size, leadWidth: leadWidth)
        if let hit = cache[key] { return hit }
        let pieces = splitBento(size: size, leadWidth: leadWidth, lead: lead)
        remember(key, pieces)
        return pieces
    }

    /// The row's cells: three equal columns.
    static func rowCells(size: CGSize) -> [CGRect] {
        let w = (size.width - 2 * gutter) / 3
        return (0..<3).map { CGRect(x: CGFloat($0) * (w + gutter), y: 0, width: w, height: size.height) }
    }

    /// The bento's cells: the lead on the left, the other two stacked beside it.
    static func bentoCells(size: CGSize, leadWidth: CGFloat) -> [CGRect] {
        let x = leadWidth + gutter, w = size.width - x, h = (size.height - gutter) / 2
        return [
            CGRect(x: 0, y: 0, width: leadWidth, height: size.height),
            CGRect(x: x, y: 0, width: w, height: h),
            CGRect(x: x, y: h + gutter, width: w, height: h)
        ]
    }

    /// A smooth closed path through an outline (`StoneGeometry.path`).
    static func path(_ outline: [CGPoint]) -> Path { StoneGeometry.path(outline) }

    // MARK: - Cache

    private struct Key: Hashable {
        let kind: String
        let lead: String
        let width: Int
        let height: Int
        let leadWidth: Int

        init(kind: String, lead: String, size: CGSize, leadWidth: CGFloat) {
            self.kind = kind
            self.lead = lead
            width = Int((size.width * 4).rounded())
            height = Int((size.height * 4).rounded())
            self.leadWidth = Int((leadWidth * 4).rounded())
        }
    }

    @MainActor private static var cache: [Key: [[CGPoint]]] = [:]

    @MainActor private static func remember(_ key: Key, _ pieces: [[CGPoint]]) {
        if cache.count > 24 { cache.removeAll() }
        cache[key] = pieces
    }

    // MARK: - Row: one rock, three

    /// One wide rock, split by two parallel leaning cuts. Each cut leaves an
    /// 8pt gutter and a 20pt fillet on both sides, so the pieces fit back.
    static func splitRow(width: CGFloat, height: CGFloat) -> [[CGPoint]] {
        guard width > 3 * gutter, height > 0 else { return [[], [], []] }
        var r = StoneRandom("camera|fixed|rock|q11")
        let rock = StoneGeometry.rockOutline(width: width, height: height, random: &r)
        let leanDraw = r.next()
        let rockSdf = StoneGeometry.polygonSdf(StoneGeometry.resample(rock, step: 4))
        let lean = 0.11 + StoneGeometry.jitter(leanDraw, 0.03)
        let w3 = (width - 2 * gutter) / 3
        let g1 = w3 + gutter / 2, g2 = 2 * w3 + 1.5 * gutter
        func cut(_ gx: CGFloat, _ side: CGFloat) -> (CGPoint) -> CGFloat {
            StoneGeometry.halfSpace(through: CGPoint(x: gx, y: height / 2),
                                    direction: CGPoint(x: lean * height, y: -height), side: side, gap: gutter)
        }
        let reach = hypot(width, height)
        return [
            StoneGeometry.piece(rockSdf, cuts: [cut(g1, 1)],
                                centre: CGPoint(x: w3 / 2, y: height / 2), fillet: 20, reach: reach),
            StoneGeometry.piece(rockSdf, cuts: [cut(g1, -1), cut(g2, 1)],
                                centre: CGPoint(x: g1 + w3 / 2 + gutter / 2, y: height / 2), fillet: 20, reach: reach),
            StoneGeometry.piece(rockSdf, cuts: [cut(g2, -1)],
                                centre: CGPoint(x: g2 + w3 / 2 + gutter / 2, y: height / 2), fillet: 20, reach: reach)
        ]
    }

}

extension ShortcutStones {
    // MARK: - Bento: one stone, split

    /// One calm stone over the whole bento, clipped along its straight
    /// gutters. The lead's two corners are the fullest.
    static func splitBento(size: CGSize, leadWidth: CGFloat, lead: HomeShortcut) -> [[CGPoint]] {
        let w = size.width, h = size.height
        guard w > leadWidth + gutter, h > gutter, leadWidth > 0 else { return [[], [], []] }
        let order = HomeShortcut.bento(lead: lead)
        var rl = StoneRandom(order[0].rawValue + "|fixed|q12split")
        var r1 = StoneRandom(order[1].rawValue + "|fixed|q12split")
        var r2 = StoneRandom(order[2].rawValue + "|fixed|q12split")
        let base = min(leadWidth, h) / 2
        let topLeft = 0.44 + rl.next() * 0.1
        let bottomLeft = 0.44 + rl.next() * 0.1
        let radii = [topLeft, 0.34 + r1.next() * 0.12, 0.34 + r2.next() * 0.12, bottomLeft].map { $0 * base }
        let n = 2.3 + rl.next() * 0.4

        var raw: [CGPoint] = []
        func corner(_ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat, _ a0: CGFloat) {
            for i in 0...24 {
                let a = a0 + (.pi / 2) * CGFloat(i) / 24
                raw.append(CGPoint(x: cx + r * StoneGeometry.superellipse(cos(a), n),
                                   y: cy + r * StoneGeometry.superellipse(sin(a), n)))
            }
        }
        corner(radii[0], radii[0], radii[0], .pi)
        corner(w - radii[1], radii[1], radii[1], -.pi / 2)
        corner(w - radii[2], h - radii[2], radii[2], 0)
        corner(radii[3], h - radii[3], radii[3], .pi / 2)

        // A slow three-lobe breath along the outline.
        var p = StoneGeometry.resample(raw, step: 1.5)
        let normal = StoneGeometry.normals(p)
        let breath = h * (0.008 + rl.next() * 0.01), phase = rl.next() * 2 * .pi
        let count = CGFloat(p.count)
        p = p.indices.map { i in
            let turn: CGFloat = 6 * .pi * CGFloat(i) / count
            let d: CGFloat = breath * sin(turn + phase)
            return CGPoint(x: p[i].x + normal[i].x * d, y: p[i].y + normal[i].y * d)
        }
        let stone = StoneGeometry.resample(StoneGeometry.fit(p, in: CGRect(origin: .zero, size: size)), step: 1)
        return bentoCells(size: size, leadWidth: leadWidth).map { smooth(clip(stone, to: $0)) }
    }

    /// Sutherland–Hodgman against a rectangle.
    private static func clip(_ polygon: [CGPoint], to rect: CGRect) -> [CGPoint] {
        typealias Edge = (inside: (CGPoint) -> Bool, cross: (CGPoint, CGPoint) -> CGPoint)
        func atX(_ x: CGFloat) -> (CGPoint, CGPoint) -> CGPoint {
            { a, b in CGPoint(x: x, y: a.y + (b.y - a.y) * (x - a.x) / (b.x - a.x)) }
        }
        func atY(_ y: CGFloat) -> (CGPoint, CGPoint) -> CGPoint {
            { a, b in CGPoint(x: a.x + (b.x - a.x) * (y - a.y) / (b.y - a.y), y: y) }
        }
        let edges: [Edge] = [
            ({ $0.x >= rect.minX }, atX(rect.minX)),
            ({ $0.x <= rect.maxX }, atX(rect.maxX)),
            ({ $0.y >= rect.minY }, atY(rect.minY)),
            ({ $0.y <= rect.maxY }, atY(rect.maxY))
        ]
        var out = polygon
        for edge in edges {
            let input = out
            out = []
            for i in input.indices {
                let a = input[(i - 1 + input.count) % input.count], b = input[i]
                let aIn = edge.inside(a), bIn = edge.inside(b)
                if bIn {
                    if !aIn { out.append(edge.cross(a, b)) }
                    out.append(b)
                } else if aIn {
                    out.append(edge.cross(a, b))
                }
            }
            if out.isEmpty { return [] }
        }
        return out
    }

    /// Eases the clipped corners: five passes of an 11-point moving average.
    private static func smooth(_ polygon: [CGPoint]) -> [CGPoint] {
        guard polygon.count > 2 else { return polygon }
        var q = StoneGeometry.resample(polygon, step: 1)
        let n = q.count, window = 5
        for _ in 0..<5 {
            let prev = q
            q = (0..<n).map { i in
                var sx: CGFloat = 0, sy: CGFloat = 0
                for j in -window...window {
                    let p = prev[((i + j) % n + n) % n]
                    sx += p.x
                    sy += p.y
                }
                let k = CGFloat(2 * window + 1)
                return CGPoint(x: sx / k, y: sy / k)
            }
        }
        return q
    }

    /// Evenly spaced points along a closed outline (`StoneGeometry.resample`).
    static func resample(_ p: [CGPoint], step: CGFloat) -> [CGPoint] { StoneGeometry.resample(p, step: step) }
}
