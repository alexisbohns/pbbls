import CoreGraphics
import SwiftUI

/// The rock geometry the stones share (#1382 D6): the outline, the distance
/// field that cuts it, the resampler and the smooth path. Moved unchanged from
/// `ShortcutStones` (Home's quick actions), so the same seeds draw the same
/// stones. Every draw comes from `StoneRandom` on a constant seed.
enum StoneGeometry {
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

    /// The rock filling exactly `w × h`: four unequal superelliptic corners
    /// (fullest top-left) joined by gently bowed sides. Takes nine draws.
    /// `corner` is the base corner radius and `bow` scales the sides' bulge; the
    /// defaults are the quick actions' rock, draw for draw.
    static func rockOutline(width w: CGFloat, height h: CGFloat, corner base: CGFloat = 34, bow bowScale: CGFloat = 1,
                            random r: inout StoneRandom) -> [CGPoint] {
        let corners: [CGFloat] = [1.15, 0.95, 0.8, 1.05], bows: [CGFloat] = [1.0, 1.4, 0.4, 1.4]
        let most = min(w, h) / 2
        let e = corners.map { min(most, base * $0 * (1 + jitter(r.next(), 0.1))) }
        let bow = bows.map { ($0 + jitter(r.next(), 0.3)) * bowScale }
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

    // MARK: - The kit: one stone, and one stone cut in n

    /// One stone filling exactly `size` (the kit's `d(w, h, seed, opt)`): the
    /// rock outline on its own seed. Only `corner` and `bow` of `options` apply.
    static func single(size: CGSize, seed: String, options: StoneOptions = StoneOptions()) -> [CGPoint] {
        guard size.width > 0, size.height > 0 else { return [] }
        var r = StoneRandom(seed)
        return rockOutline(width: size.width, height: size.height, corner: options.corner, bow: options.bow,
                           random: &r)
    }

    /// One rock cut into `count` pieces by parallel leaning cuts (the kit's
    /// `split(w, h, n, seed, opt)`), left to right. Each cut leaves `gutter`
    /// between neighbours and rounds both fresh edges by `fillet`, so the pieces
    /// fit back. The lean is drawn after the rock (0.11 ± 0.03) unless `options`
    /// gives one; the quick actions' row is `count` 3 on the defaults.
    static func split(size: CGSize, count n: Int, seed: String,
                      options o: StoneOptions = StoneOptions()) -> [[CGPoint]] {
        let w = size.width, h = size.height
        guard n > 0 else { return [] }
        guard w > CGFloat(n - 1) * o.gutter, h > 0 else { return Array(repeating: [], count: n) }
        var r = StoneRandom(seed)
        let rock = rockOutline(width: w, height: h, corner: o.corner, bow: o.bow, random: &r)
        let lean = o.lean ?? 0.11 + jitter(r.next(), 0.03)
        let rockSdf = polygonSdf(resample(rock, step: 4))
        let wn = (w - CGFloat(n - 1) * o.gutter) / CGFloat(n)
        let lines: [CGFloat] = (0..<max(n - 1, 0)).map { index in
            let k = CGFloat(index)
            return (k + 1) * wn + (k + 0.5) * o.gutter
        }
        func cut(_ gx: CGFloat, _ side: CGFloat) -> (CGPoint) -> CGFloat {
            halfSpace(through: CGPoint(x: gx, y: h / 2), direction: CGPoint(x: lean * h, y: -h),
                      side: side, gap: o.gutter)
        }
        let reach = hypot(w, h)
        return (0..<n).map { k in
            var cuts: [(CGPoint) -> CGFloat] = []
            if k > 0 { cuts.append(cut(lines[k - 1], -1)) }
            if k < n - 1 { cuts.append(cut(lines[k], 1)) }
            let centre = CGPoint(x: CGFloat(k) * (wn + o.gutter) + wn / 2, y: h / 2)
            return piece(rockSdf, cuts: cuts, centre: centre, fillet: o.fillet, reach: reach)
        }
    }

    /// Signed distance to a closed polygon: negative inside.
    static func polygonSdf(_ p: [CGPoint]) -> (CGPoint) -> CGFloat {
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
    static func halfSpace(through p: CGPoint, direction d: CGPoint,
                          side: CGFloat, gap: CGFloat) -> (CGPoint) -> CGFloat {
        let l = hypot(d.x, d.y), nx = -d.y / l, ny = d.x / l
        return { q in side * ((q.x - p.x) * nx + (q.y - p.y) * ny) + gap / 2 }
    }

    /// Intersection of two distance fields, rounded by `r`.
    static func roundIntersection(_ a: CGFloat, _ b: CGFloat, _ r: CGFloat) -> CGFloat {
        min(-r, max(a, b)) + hypot(max(r + a, 0), max(r + b, 0))
    }

    /// The rock minus the gutters, traced by casting rays from the piece's
    /// centre (every piece is convex).
    static func piece(_ rock: @escaping (CGPoint) -> CGFloat, cuts: [(CGPoint) -> CGFloat],
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

    // MARK: - Outline maths

    static func jitter(_ u: CGFloat, _ amount: CGFloat) -> CGFloat { (u - 0.5) * 2 * amount }

    static func superellipse(_ v: CGFloat, _ n: CGFloat) -> CGFloat {
        v == 0 ? 0 : (v < 0 ? -1 : 1) * pow(abs(v), 2 / n)
    }

    /// Stretches an outline so its bounds are exactly `rect`.
    static func fit(_ p: [CGPoint], in rect: CGRect) -> [CGPoint] {
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
    static func normals(_ p: [CGPoint]) -> [CGPoint] {
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

/// A stone's cut (the kit's `opt`): the base corner radius, the sides' bulge,
/// the cuts' lean (drawn when nil), the gutter between pieces and the fillet
/// that rounds a fresh cut.
struct StoneOptions: Hashable {
    var corner: CGFloat = 34
    var bow: CGFloat = 1
    var lean: CGFloat?
    var gutter: CGFloat = 8
    var fillet: CGFloat = 20
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
