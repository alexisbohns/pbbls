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

    /// A smooth closed path through an outline: resampled every 4pt, then
    /// drawn as a Catmull-Rom spline.
    static func path(_ outline: [CGPoint]) -> Path {
        let p = resample(outline, step: 4)
        let n = p.count
        var path = Path()
        guard n > 2 else { return path }
        path.move(to: p[0])
        for i in 0..<n {
            let p0 = p[(i - 1 + n) % n], p1 = p[i], p2 = p[(i + 1) % n], p3 = p[(i + 2) % n]
            path.addCurve(
                to: p2,
                control1: CGPoint(x: p1.x + (p2.x - p0.x) / 6, y: p1.y + (p2.y - p0.y) / 6),
                control2: CGPoint(x: p2.x - (p3.x - p1.x) / 6, y: p2.y - (p3.y - p1.y) / 6)
            )
        }
        path.closeSubpath()
        return path
    }

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
        let rock = rockOutline(width: width, height: height, random: &r)
        let leanDraw = r.next()
        let rockSdf = polygonSdf(resample(rock, step: 4))
        let lean = 0.11 + jitter(leanDraw, 0.03)
        let w3 = (width - 2 * gutter) / 3
        let g1 = w3 + gutter / 2, g2 = 2 * w3 + 1.5 * gutter
        func cut(_ gx: CGFloat, _ side: CGFloat) -> (CGPoint) -> CGFloat {
            halfSpace(through: CGPoint(x: gx, y: height / 2), direction: CGPoint(x: lean * height, y: -height),
                      side: side, gap: gutter)
        }
        let reach = hypot(width, height)
        return [
            piece(rockSdf, cuts: [cut(g1, 1)], centre: CGPoint(x: w3 / 2, y: height / 2), fillet: 20, reach: reach),
            piece(rockSdf, cuts: [cut(g1, -1), cut(g2, 1)],
                  centre: CGPoint(x: g1 + w3 / 2 + gutter / 2, y: height / 2), fillet: 20, reach: reach),
            piece(rockSdf, cuts: [cut(g2, -1)],
                  centre: CGPoint(x: g2 + w3 / 2 + gutter / 2, y: height / 2), fillet: 20, reach: reach)
        ]
    }

    /// The rock filling exactly `w × h`: four unequal superelliptic corners
    /// (fullest top-left) joined by gently bowed sides. Takes nine draws.
    private static func rockOutline(width w: CGFloat, height h: CGFloat, random r: inout StoneRandom) -> [CGPoint] {
        let corners: [CGFloat] = [1.15, 0.95, 0.8, 1.05], bows: [CGFloat] = [1.0, 1.4, 0.4, 1.4]
        let most = min(w, h) / 2
        let e = corners.map { min(most, 34 * $0 * (1 + jitter(r.next(), 0.1))) }
        let bow = bows.map { $0 + jitter(r.next(), 0.3) }
        let n = 2.5 + jitter(r.next(), 0.15)
        var pts: [CGPoint] = []

        func side(from p0: CGPoint, to p1: CGPoint, normal: CGPoint, bow b: CGFloat) {
            let length = hypot(p1.x - p0.x, p1.y - p0.y)
            let count = max(2, Int((length / 1.5).rounded(.up)))
            let amp = b * min(1.6, length / 70)
            for i in 0..<count {
                let t = CGFloat(i) / CGFloat(count), o = amp * sin(.pi * t)
                pts.append(CGPoint(x: p0.x + (p1.x - p0.x) * t + normal.x * o,
                                   y: p0.y + (p1.y - p0.y) * t + normal.y * o))
            }
        }
        func corner(_ cx: CGFloat, _ cy: CGFloat, _ r: CGFloat, _ a0: CGFloat) {
            let count = max(8, Int(r.rounded(.up)))
            for i in 0..<count {
                let th = a0 + (.pi / 2) * CGFloat(i) / CGFloat(count)
                pts.append(CGPoint(x: cx + r * superellipse(cos(th), n), y: cy + r * superellipse(sin(th), n)))
            }
        }
        let (a, b, c, d) = (e[0], e[1], e[2], e[3])
        corner(a, a, a, .pi)
        side(from: CGPoint(x: a, y: 0), to: CGPoint(x: w - b, y: 0), normal: CGPoint(x: 0, y: -1), bow: bow[0])
        corner(w - b, b, b, -.pi / 2)
        side(from: CGPoint(x: w, y: b), to: CGPoint(x: w, y: h - c), normal: CGPoint(x: 1, y: 0), bow: bow[1])
        corner(w - c, h - c, c, 0)
        side(from: CGPoint(x: w - c, y: h), to: CGPoint(x: d, y: h), normal: CGPoint(x: 0, y: 1), bow: bow[2])
        corner(d, h - d, d, .pi / 2)
        side(from: CGPoint(x: 0, y: h - d), to: CGPoint(x: 0, y: a), normal: CGPoint(x: -1, y: 0), bow: bow[3])
        return fit(pts, in: CGRect(x: 0, y: 0, width: w, height: h))
    }

    /// Signed distance to a closed polygon: negative inside.
    private static func polygonSdf(_ p: [CGPoint]) -> (CGPoint) -> CGFloat {
        { q in
            var best = CGFloat.infinity
            var inside = false
            var j = p.count - 1
            for i in 0..<p.count {
                let a = p[j], b = p[i]
                let ex = b.x - a.x, ey = b.y - a.y, wx = q.x - a.x, wy = q.y - a.y
                let len2 = ex * ex + ey * ey
                let t = max(0, min(1, (wx * ex + wy * ey) / (len2 == 0 ? 1 : len2)))
                let dx = wx - ex * t, dy = wy - ey * t
                best = min(best, dx * dx + dy * dy)
                if (a.y > q.y) != (b.y > q.y) && q.x < (b.x - a.x) * (q.y - a.y) / (b.y - a.y) + a.x {
                    inside.toggle()
                }
                j = i
            }
            return (inside ? -1 : 1) * best.squareRoot()
        }
    }

    /// The side of a gutter line a piece keeps, as a distance field.
    private static func halfSpace(through p: CGPoint, direction d: CGPoint,
                                  side: CGFloat, gap: CGFloat) -> (CGPoint) -> CGFloat {
        let l = hypot(d.x, d.y), nx = -d.y / l, ny = d.x / l
        return { q in side * ((q.x - p.x) * nx + (q.y - p.y) * ny) + gap / 2 }
    }

    /// Intersection of two distance fields, rounded by `r`.
    private static func roundIntersection(_ a: CGFloat, _ b: CGFloat, _ r: CGFloat) -> CGFloat {
        min(-r, max(a, b)) + hypot(max(r + a, 0), max(r + b, 0))
    }

    /// The rock minus the gutters, traced by casting rays from the piece's
    /// centre (every piece is convex).
    private static func piece(_ rock: @escaping (CGPoint) -> CGFloat, cuts: [(CGPoint) -> CGFloat],
                              centre: CGPoint, fillet: CGFloat, reach: CGFloat) -> [CGPoint] {
        func sdf(_ q: CGPoint) -> CGFloat {
            cuts.reduce(rock(q)) { roundIntersection($0, $1(q), fillet) }
        }
        let rays = 180
        return (0..<rays).map { i in
            let th = .pi * 1.12 + 2 * .pi * CGFloat(i) / CGFloat(rays)
            let dx = cos(th), dy = sin(th)
            var lo: CGFloat = 0, hi = reach
            for _ in 0..<16 {
                let mid = (lo + hi) / 2
                if sdf(CGPoint(x: centre.x + dx * mid, y: centre.y + dy * mid)) < 0 { lo = mid } else { hi = mid }
            }
            return CGPoint(x: centre.x + dx * lo, y: centre.y + dy * lo)
        }
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
                raw.append(CGPoint(x: cx + r * superellipse(cos(a), n), y: cy + r * superellipse(sin(a), n)))
            }
        }
        corner(radii[0], radii[0], radii[0], .pi)
        corner(w - radii[1], radii[1], radii[1], -.pi / 2)
        corner(w - radii[2], h - radii[2], radii[2], 0)
        corner(radii[3], h - radii[3], radii[3], .pi / 2)

        // A slow three-lobe breath along the outline.
        var p = resample(raw, step: 1.5)
        let normal = normals(p)
        let breath = h * (0.008 + rl.next() * 0.01), phase = rl.next() * 2 * .pi
        let count = CGFloat(p.count)
        p = p.indices.map { i in
            let turn: CGFloat = 6 * .pi * CGFloat(i) / count
            let d: CGFloat = breath * sin(turn + phase)
            return CGPoint(x: p[i].x + normal[i].x * d, y: p[i].y + normal[i].y * d)
        }
        let stone = resample(fit(p, in: CGRect(origin: .zero, size: size)), step: 1)
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
        var q = resample(polygon, step: 1)
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

    // MARK: - Outline maths

    private static func jitter(_ u: CGFloat, _ amount: CGFloat) -> CGFloat { (u - 0.5) * 2 * amount }

    private static func superellipse(_ v: CGFloat, _ n: CGFloat) -> CGFloat {
        v == 0 ? 0 : (v < 0 ? -1 : 1) * pow(abs(v), 2 / n)
    }

    /// Stretches an outline so its bounds are exactly `rect`.
    private static func fit(_ p: [CGPoint], in rect: CGRect) -> [CGPoint] {
        guard let minX = p.map(\.x).min(), let maxX = p.map(\.x).max(),
              let minY = p.map(\.y).min(), let maxY = p.map(\.y).max(),
              maxX > minX, maxY > minY else { return p }
        return p.map {
            CGPoint(x: rect.minX + ($0.x - minX) / (maxX - minX) * rect.width,
                    y: rect.minY + ($0.y - minY) / (maxY - minY) * rect.height)
        }
    }

    /// Evenly spaced points along a closed outline, at least 24.
    static func resample(_ p: [CGPoint], step: CGFloat) -> [CGPoint] {
        let n = p.count
        guard n > 2 else { return p }
        var cum: [CGFloat] = [0]
        for i in 1...n {
            let a = p[i - 1], b = p[i % n]
            cum.append(cum[i - 1] + hypot(b.x - a.x, b.y - a.y))
        }
        let length = cum[n]
        let count = max(24, Int((length / step).rounded()))
        var out: [CGPoint] = []
        out.reserveCapacity(count)
        var j = 0
        for i in 0..<count {
            let s = length * CGFloat(i) / CGFloat(count)
            while j < n - 1 && cum[j + 1] < s { j += 1 }
            let a = p[j], b = p[(j + 1) % n]
            let seg = cum[j + 1] - cum[j]
            let u = seg == 0 ? 0 : (s - cum[j]) / seg
            out.append(CGPoint(x: a.x + (b.x - a.x) * u, y: a.y + (b.y - a.y) * u))
        }
        return out
    }

    /// Outward unit normals, from the points two steps either side.
    private static func normals(_ p: [CGPoint]) -> [CGPoint] {
        let n = p.count
        let cx = p.reduce(0) { $0 + $1.x } / CGFloat(n), cy = p.reduce(0) { $0 + $1.y } / CGFloat(n)
        return p.indices.map { i in
            let a = p[(i - 2 + n) % n], b = p[(i + 2) % n]
            var nx = b.y - a.y, ny = -(b.x - a.x)
            let l = hypot(nx, ny)
            if l > 0 { nx /= l; ny /= l }
            if nx * (p[i].x - cx) + ny * (p[i].y - cy) < 0 { nx = -nx; ny = -ny }
            return CGPoint(x: nx, y: ny)
        }
    }
}

/// The exploration harness's seeded generator, bit for bit (a murmur-style
/// string hash, then an xorshift-multiply step), so the shapes match the
/// reviewed mocks. Never seeds from `String.hashValue`, which changes per process.
struct StoneRandom {
    private var h: UInt32

    init(_ seed: String) {
        let units = Array(seed.utf16)
        var h = UInt32(1_779_033_703) ^ UInt32(units.count)
        for unit in units {
            h = (h ^ UInt32(unit)) &* 3_432_918_353
            h = (h << 13) | (h >> 19)
        }
        self.h = h
    }

    mutating func next() -> CGFloat {
        h = (h ^ (h >> 16)) &* 2_246_822_507
        h = (h ^ (h >> 13)) &* 3_266_489_909
        h ^= h >> 16
        return CGFloat(Double(h) / 4_294_967_296)
    }
}
