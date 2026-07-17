import CoreGraphics

/// Aspect-ratio bucket chosen for a pebble's snap on the read view. The snap's
/// width/height ratio snaps to the nearest of three fixed buckets — 4:3
/// (landscape), 1:1, 3:4 (portrait) — so the imported photo reads at a natural
/// shape instead of a forced strip (issue #599).
///
/// These buckets replace the earlier 16:9/4:3/1:1 set: the redesigned page
/// frames the whole picture (portrait included) with the Petroglyph overlapping
/// its top-right, so a tall 3:4 crop is now first-class and the wide 16:9 strip
/// is gone.
///
/// Pure value type. No view dependencies; trivially unit-testable.
enum BannerAspect: Equatable {
    case fourThree
    case square
    case threeFour

    /// CG ratio (width / height) for the bucket.
    var cgRatio: CGFloat {
        switch self {
        case .fourThree: return 4.0 / 3.0
        case .square:    return 1.0
        case .threeFour: return 3.0 / 4.0
        }
    }

    /// Pick the bucket whose `cgRatio` is closest to `ratio` (absolute
    /// distance). Very wide sources collapse to `.fourThree` and very tall
    /// ones to `.threeFour` since those are the extreme candidates — no
    /// special case.
    static func nearest(to ratio: CGFloat) -> BannerAspect {
        let candidates: [BannerAspect] = [.fourThree, .square, .threeFour]
        return candidates.min(by: { abs($0.cgRatio - ratio) < abs($1.cgRatio - ratio) })
            ?? .square
    }
}
