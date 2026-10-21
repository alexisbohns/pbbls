import SwiftUI

/// The rings and the halo around a Spine stone (#1130 R3): the flat outline
/// scaled about its area centroid, each ring a step further out (4.8 / 4.4 /
/// 4.2 pt by size), the halo 0.78 of a step past the last ring. The steps are
/// points, not outline units, as the web's non-scaling strokes are.
struct StoneRingGeometry {
    /// The silhouette in the stone's own frame, in points.
    let outline: Path
    /// Its area centroid, in the same frame.
    let centroid: CGPoint
    /// Per ring, innermost first: the x and y scales about `centroid`.
    let rings: [CGSize]
    let halo: CGSize

    static let count: [ValenceSizeGroup: Int] = [.small: 1, .medium: 2, .large: 3]
    static let step: [ValenceSizeGroup: CGFloat] = [.small: 4.8, .medium: 4.4, .large: 4.2]
    static let haloStep: CGFloat = 0.78

    /// - Parameter outline: the silhouette already in points, in the stone's frame.
    static func make(outline: CGPath, size: ValenceSizeGroup) -> StoneRingGeometry {
        let polygon = WobblePathFlattener.flatten(outline, step: 2).first?.points ?? []
        let bounds = outline.boundingBoxOfPath
        let centroid = areaCentroid(polygon) ?? CGPoint(x: bounds.midX, y: bounds.midY)
        let step = Self.step[size] ?? 4.4
        let width = max(bounds.width, 1)
        let height = max(bounds.height, 1)
        func at(_ k: CGFloat) -> CGSize {
            CGSize(width: 1 + 2 * k * step / width, height: 1 + 2 * k * step / height)
        }
        let rings = Self.count[size] ?? 2
        return StoneRingGeometry(outline: Path(outline), centroid: centroid,
                                 rings: (1...rings).map { at(CGFloat($0)) }, halo: at(CGFloat(rings) + haloStep))
    }

    /// The geometry of `valence`'s soft outline at its phone width, cached: the
    /// flattening walks the whole path.
    @MainActor
    static func forStone(_ valence: Valence) -> StoneRingGeometry? {
        if let cached = cache[valence] { return cached }
        guard let art = WobbleRenderer.softBackdropArt(size: valence.sizeGroup, polarity: valence.polarity) else {
            return nil
        }
        let stone = SpineLayout.stoneSize(valence.sizeGroup)
        let outline = WobbledBackdropShape(art: art).path(in: CGRect(origin: .zero, size: stone)).cgPath
        let geometry = make(outline: outline, size: valence.sizeGroup)
        cache[valence] = geometry
        return geometry
    }

    @MainActor private static var cache: [Valence: StoneRingGeometry] = [:]

    /// Shoelace over the first contour; nil when it encloses no area.
    private static func areaCentroid(_ points: [CGPoint]) -> CGPoint? {
        guard points.count >= 3 else { return nil }
        var area: CGFloat = 0
        var cx: CGFloat = 0
        var cy: CGFloat = 0
        for index in points.indices {
            let p = points[index]
            let q = points[(index + 1) % points.count]
            let cross = p.x * q.y - q.x * p.y
            area += cross
            cx += (p.x + q.x) * cross
            cy += (p.y + q.y) * cross
        }
        guard abs(area) > 1e-9 else { return nil }
        return CGPoint(x: cx / (3 * area), y: cy / (3 * area))
    }
}
