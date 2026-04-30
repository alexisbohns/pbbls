import CoreGraphics

/// Banner aspect-ratio bucket chosen for a source image. The pebble read
/// banner snaps the source's width/height ratio to the nearest of three
/// fixed buckets — 16:9, 4:3, 1:1 — so portrait or near-square uploads no
/// longer get cropped to a forced landscape strip.
///
/// Pure value type. No view dependencies; trivially unit-testable.
enum BannerAspect: Equatable {
    case sixteenNine
    case fourThree
    case square

    /// CG ratio (width / height) for the bucket.
    var cgRatio: CGFloat {
        switch self {
        case .sixteenNine: return 16.0 / 9.0
        case .fourThree:   return 4.0 / 3.0
        case .square:      return 1.0
        }
    }

    /// Pick the bucket whose `cgRatio` is closest to `ratio` (absolute
    /// distance). Portrait sources (`ratio < 1`) always bucket to `.square`
    /// since 1.0 is the smallest of the three candidates — no special case.
    static func nearest(to ratio: CGFloat) -> BannerAspect {
        let candidates: [BannerAspect] = [.sixteenNine, .fourThree, .square]
        return candidates.min(by: { abs($0.cgRatio - ratio) < abs($1.cgRatio - ratio) })
            ?? .sixteenNine
    }
}
