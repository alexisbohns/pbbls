import CoreGraphics

/// Banner aspect-ratio bucket chosen for a source image. The pebble read
/// banner snaps the source's width/height ratio to the nearest of four fixed
/// buckets — 16:9, 4:3, 1:1, 3:4 — so landscape, square, and portrait uploads
/// are each shown at a sensible ratio (issue #599 added the 3:4 portrait
/// bucket).
///
/// Pure value type. No view dependencies; trivially unit-testable.
enum BannerAspect: Equatable {
    case sixteenNine
    case fourThree
    case square
    case threeFour

    /// CG ratio (width / height) for the bucket.
    var cgRatio: CGFloat {
        switch self {
        case .sixteenNine: return 16.0 / 9.0
        case .fourThree:   return 4.0 / 3.0
        case .square:      return 1.0
        case .threeFour:   return 3.0 / 4.0
        }
    }

    /// Pick the bucket whose `cgRatio` is closest to `ratio` (absolute
    /// distance). Portrait sources (`ratio < 1`) now bucket to `.threeFour`
    /// (0.75) rather than collapsing to `.square`.
    static func nearest(to ratio: CGFloat) -> BannerAspect {
        let candidates: [BannerAspect] = [.sixteenNine, .fourThree, .square, .threeFour]
        return candidates.min(by: { abs($0.cgRatio - ratio) < abs($1.cgRatio - ratio) })
            ?? .sixteenNine
    }
}
