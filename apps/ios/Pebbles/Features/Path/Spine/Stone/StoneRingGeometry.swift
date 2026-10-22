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

    /// The outermost ring's top edge in the stone's own frame (negative above
    /// the stone), at rest: the outline's top scaled about the centroid, as
    /// `path(scaled:offset:)` draws it.
    var ringTop: CGFloat {
        centroid.y + (outline.boundingRect.minY - centroid.y) * (rings.last?.height ?? 1)
    }

    static let count: [ValenceSizeGroup: Int] = [.small: 1, .medium: 2, .large: 3]
    static let step: [ValenceSizeGroup: CGFloat] = [.small: 4.8, .medium: 4.4, .large: 4.2]
    static let haloStep: CGFloat = 0.78

    /// The web and Android flatten the silhouette at this step, in outline
    /// units, before taking its centroid; flattening after the fit to points
    /// would sample a different polygon and the centroids would drift.
    static let flattenStep = 2.0

    /// - Parameters:
    ///   - outline: the silhouette in its own units (the SVG's viewBox, or points already).
    ///   - toPoints: the fit from those units into the stone's frame, in points.
    /// - Returns: nil for an outline with no extent, which has no frame to ring.
    static func make(outline: CGPath, size: ValenceSizeGroup,
                     toPoints: CGAffineTransform = .identity) -> StoneRingGeometry? {
        let unitBounds = outline.boundingBoxOfPath
        guard !outline.isEmpty, !unitBounds.isNull, !unitBounds.isInfinite else { return nil }
        let polygon = WobblePathFlattener.flatten(outline, step: flattenStep).first?.points ?? []
        let unitCentroid = areaCentroid(polygon) ?? CGPoint(x: unitBounds.midX, y: unitBounds.midY)
        // `CGPath.copy(using:)`, as `WobbledPathShape` fits it: the same points to the last bit.
        var transform = toPoints
        let fitted = outline.copy(using: &transform).map(Path.init) ?? Path(outline).applying(toPoints)
        let bounds = fitted.boundingRect
        let step = Self.step[size] ?? 4.4
        let width = max(bounds.width, 1)
        let height = max(bounds.height, 1)
        func at(_ k: CGFloat) -> CGSize {
            CGSize(width: 1 + 2 * k * step / width, height: 1 + 2 * k * step / height)
        }
        let rings = Self.count[size] ?? 2
        return StoneRingGeometry(outline: fitted, centroid: unitCentroid.applying(toPoints),
                                 rings: (1...rings).map { at(CGFloat($0)) }, halo: at(CGFloat(rings) + haloStep))
    }

    /// The geometry of `valence`'s soft outline at its phone width, cached: the
    /// flattening walks the whole path.
    @MainActor
    static func forStone(_ valence: Valence) -> StoneRingGeometry? {
        if let cached = cache[valence] { return cached }
        guard let art = WobbleRenderer.softBackdropArt(size: valence.sizeGroup, polarity: valence.polarity),
              let geometry = make(outline: art.path, size: valence.sizeGroup,
                                  toPoints: fit(art.viewBox, into: SpineLayout.stoneSize(valence.sizeGroup))) else {
            return nil
        }
        cache[valence] = geometry
        return geometry
    }

    /// The aspect fit `WobbledBackdropShape` draws with: viewBox to the stone's frame, centred.
    private static func fit(_ viewBox: CGRect, into stone: CGSize) -> CGAffineTransform {
        let scale = min(stone.width / viewBox.width, stone.height / viewBox.height)
        let dx = (stone.width - viewBox.width * scale) / 2 - viewBox.minX * scale
        let dy = (stone.height - viewBox.height * scale) / 2 - viewBox.minY * scale
        return CGAffineTransform(scaleX: scale, y: scale).concatenating(CGAffineTransform(translationX: dx, y: dy))
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
