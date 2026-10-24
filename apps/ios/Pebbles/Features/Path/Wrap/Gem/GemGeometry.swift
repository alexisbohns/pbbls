import Foundation

/// A point of the cut, in the gem's units (the round stone's girdle is 80 from the centre; y down).
struct GemPoint: Hashable, Sendable {
    let x: Double
    let y: Double
}

enum FacetKind: Hashable, Sendable {
    case table, star, kite, girdle
}

/// One facet of the cut: `theta` is its direction in sevenths of a turn from
/// the top, clockwise; `tone` its light, 0 (lit) to 1 (in shade), fixed by the
/// cut and never by data.
struct GemFacet: Identifiable, Hashable, Sendable {
    let id: String
    let kind: FacetKind
    let theta: Double?
    let points: [GemPoint]
    var tone: Double
}

/// An edge two facets share, for the seams.
struct GemEdge: Hashable, Sendable {
    let a: GemPoint
    let b: GemPoint
    let first: String
    let second: String

    func touches(_ facet: String) -> Bool {
        first == facet || second == facet
    }
}

/// The emotions gem's fixed cut (#1226, D10): the faithful brilliant of the
/// round-5 board, a port of `gem-method.js`'s `geometry` (the oracle the
/// server's `gem_allocate` is proven against) with the board's lighting.
/// Display only: which family owns a facet is the server's `gem.facets`; this
/// never decides it. The table, 7 stars, 7 kites and 14 girdle facets; the 28
/// around the table are the allocation's ids. Android: `GemGeometry`.
enum GemGeometry {
    static let sides = 7
    private static let step = 2 * Double.pi / Double(sides)
    private static let half = step / 2

    // The oracle's shape: table corners at `tableRadius`, star points at `starRadius`,
    // kite tips at the reach profile.
    private static let tableRadius = 35.0
    private static let starRadius = 53.0
    private static let roundRadius = 80.0
    private static let reach: [Double] = [88, 84, 79, 76, 76, 79, 84]
    private static let arcSteps = 24

    // The board's light: the table raised by a 34.5° crown, one light 38° up from the upper left.
    private static let crownDegrees = 34.5
    private static let lightElevationDegrees = 38.0
    private static let lightAzimuthDegrees = -135.0

    static let facets: [GemFacet] = cut(reach).map { facet in
        var lit = facet
        lit.tone = tone(facet)
        return lit
    }

    static let byId: [String: GemFacet] = Dictionary(uniqueKeysWithValues: facets.map { ($0.id, $0) })

    /// The 28 ids the allocation colours, in the oracle's order.
    static let allocated: [String] = facets.filter { $0.kind != .table }.map(\.id)

    static let edges: [GemEdge] = sharedEdges(facets)

    /// The farthest a girdle point reaches from the centre, in the gem's units.
    static let reachMax: Double = reach.max() ?? roundRadius

    /// The round stone's facet areas by kind: the allocation's nominal areas (`gem_allocate`'s constants).
    static let nominalAreas: [FacetKind: Double] = {
        var areas: [FacetKind: Double] = [:]
        for facet in cut(Array(repeating: roundRadius, count: sides)) where areas[facet.kind] == nil {
            areas[facet.kind] = shoelace(facet.points)
        }
        return areas
    }()

    private static let rim: [(angle: Double, radius: Double)] = facets.filter { $0.kind == .girdle }
        .flatMap(\.points)
        .map { (atan2($0.y, $0.x), hypot($0.x, $0.y)) }

    /// The rim's radius toward `angle` (radians, 0 at three o'clock, clockwise), for placing names outside.
    static func rimRadius(_ angle: Double) -> Double {
        rim.min { abs(atan2(sin($0.angle - angle), cos($0.angle - angle)))
            < abs(atan2(sin($1.angle - angle), cos($1.angle - angle))) }?.radius ?? roundRadius
    }

    /// The area-weighted circular mean of `ids`' directions, in radians: where a family's run sits.
    static func runAngle(_ ids: [String]) -> Double {
        var x = 0.0
        var y = 0.0
        for facet in ids.compactMap({ byId[$0] }) {
            let angle = -Double.pi / 2 + (facet.theta ?? 0) * step
            let weight = nominalAreas[facet.kind] ?? 0
            x += weight * cos(angle)
            y += weight * sin(angle)
        }
        return hypot(x, y) < 1e-6 ? -Double.pi / 2 : atan2(y, x)
    }

    // MARK: - The cut

    private static func angle(_ point: Int) -> Double {
        -Double.pi / 2 + Double(point) * step
    }

    private static func at(_ angle: Double, _ radius: Double) -> GemPoint {
        GemPoint(x: radius * cos(angle), y: radius * sin(angle))
    }

    private static func cut(_ reach: [Double]) -> [GemFacet] {
        let n = sides
        func radius(_ point: Int) -> Double { reach[((point % n) + n) % n] }
        let corners = (0..<n).map { at(angle($0), tableRadius) }
        let stars = (0..<n).map { at(angle($0) + half, starRadius) }
        let tips = (0..<n).map { at(angle($0), radius($0)) }
        let edgeRadius = (0..<n).map { (radius($0) + radius($0 + 1)) / 2 }
        let edges = (0..<n).map { at(angle($0) + half, edgeRadius[$0]) }

        // The rim between two girdle points: polar steps, the radius eased.
        func arc(_ a0: Double, _ r0: Double, _ a1: Double, _ r1: Double) -> [GemPoint] {
            (1...arcSteps).map { stepIndex in
                let t = Double(stepIndex) / Double(arcSteps)
                let eased = (1 - cos(t * Double.pi)) / 2
                return at(a0 + (a1 - a0) * t, r0 + (r1 - r0) * eased)
            }
        }

        var out = [GemFacet(id: "table", kind: .table, theta: nil, points: corners, tone: 0)]
        for p in 0..<n {
            out.append(GemFacet(id: "star_\(p)", kind: .star, theta: Double(p) + 0.5,
                                points: [corners[p], corners[(p + 1) % n], stars[p]], tone: 0))
        }
        for p in 0..<n {
            out.append(GemFacet(id: "kite_\(p)", kind: .kite, theta: Double(p),
                                points: [corners[p], stars[p], tips[p], stars[(p + n - 1) % n]], tone: 0))
        }
        for p in 0..<n {
            let h = (p + n - 1) % n
            out.append(GemFacet(id: "girdle_\(2 * p)", kind: .girdle, theta: Double(p) - 0.25,
                                points: [tips[p], stars[h], edges[h]]
                                    + arc(angle(p) - half, edgeRadius[h], angle(p), radius(p)).dropLast(),
                                tone: 0))
            out.append(GemFacet(id: "girdle_\(2 * p + 1)", kind: .girdle, theta: Double(p) + 0.25,
                                points: [tips[p]] + arc(angle(p), radius(p), angle(p) + half, edgeRadius[p])
                                    + [stars[p]],
                                tone: 0))
        }
        return out
    }

    private static func shoelace(_ points: [GemPoint]) -> Double {
        var sum = 0.0
        for index in points.indices {
            let a = points[index]
            let b = points[(index + 1) % points.count]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) / 2
    }

    // MARK: - The light

    private static let lightDirection: [Double] = {
        let elevation = lightElevationDegrees * Double.pi / 180
        let azimuth = lightAzimuthDegrees * Double.pi / 180
        return [cos(elevation) * cos(azimuth), cos(elevation) * sin(azimuth), sin(elevation)]
    }()

    private static let tableHeight = (roundRadius - tableRadius) * tan(crownDegrees * Double.pi / 180)
    private static let starHeight = tableHeight
        * (1 - (starRadius * cos(step / 2) - tableRadius) / (roundRadius - tableRadius))

    private static func lift(_ point: GemPoint) -> [Double] {
        let radius = hypot(point.x, point.y)
        let z: Double
        if abs(radius - tableRadius) < 0.5 {
            z = tableHeight
        } else if abs(radius - starRadius) < 0.5 {
            z = starHeight
        } else {
            z = 0
        }
        return [point.x, point.y, z]
    }

    private static func newell(_ points: [[Double]]) -> [Double] {
        var nx = 0.0
        var ny = 0.0
        var nz = 0.0
        for index in points.indices {
            let a = points[index]
            let b = points[(index + 1) % points.count]
            nx += (a[1] - b[1]) * (a[2] + b[2])
            ny += (a[2] - b[2]) * (a[0] + b[0])
            nz += (a[0] - b[0]) * (a[1] + b[1])
        }
        let length = (nx * nx + ny * ny + nz * nz).squareRoot()
        let l = length > 0 ? length : 1
        let normal = [nx / l, ny / l, nz / l]
        return normal[2] < 0 ? normal.map { -$0 } : normal
    }

    /// The board's flat tone: the light on the lifted facet, then a per-kind range (kites never full shade).
    private static func tone(_ facet: GemFacet) -> Double {
        if facet.kind == .table { return 0 }
        let normal = newell(facet.points.map(lift))
        let light = zip(normal, lightDirection).map(*).reduce(0, +)
        let shade = min(max((0.97 - light) / 0.78, 0), 1)
        switch facet.kind {
        case .kite: return 0.12 + shade * 0.72
        case .star: return shade * 0.8
        case .girdle, .table: return shade
        }
    }

    // MARK: - Seams

    private static func sharedEdges(_ facets: [GemFacet]) -> [GemEdge] {
        // Java's Math.round (half up), as the oracle keys its points.
        func key(_ point: GemPoint) -> String {
            "\(Int64((point.x * 100 + 0.5).rounded(.down))),\(Int64((point.y * 100 + 0.5).rounded(.down)))"
        }
        struct Seen {
            let a: GemPoint
            let b: GemPoint
            var facets: [String]
        }
        var order: [String] = []
        var found: [String: Seen] = [:]
        for facet in facets {
            for (index, a) in facet.points.enumerated() {
                let b = facet.points[(index + 1) % facet.points.count]
                let ka = key(a)
                let kb = key(b)
                let edgeKey = ka < kb ? "\(ka)|\(kb)" : "\(kb)|\(ka)"
                if found[edgeKey] == nil {
                    order.append(edgeKey)
                    found[edgeKey] = Seen(a: a, b: b, facets: [])
                }
                found[edgeKey]?.facets.append(facet.id)
            }
        }
        return order.compactMap { found[$0] }.filter { $0.facets.count == 2 }.map {
            GemEdge(a: $0.a, b: $0.b, first: $0.facets[0], second: $0.facets[1])
        }
    }
}
