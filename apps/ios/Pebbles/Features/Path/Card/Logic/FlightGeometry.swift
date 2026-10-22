import CoreGraphics

/// The FLIP (#1160 D1): the card lays out at rest and is drawn from the slot,
/// scaled to the slot's width (fill-width) about its top-leading corner and
/// moved onto the slot's origin, then eased to the identity. Progress 0 is the
/// slot, 1 the rest.
enum FlightGeometry {
    static func transform(from slot: CGRect, to rest: CGRect, progress: Double) -> (scale: CGFloat, offset: CGSize) {
        guard rest.width > 0 else { return (1, .zero) }
        let p = CGFloat(min(max(progress, 0), 1))
        let start = slot.width / rest.width
        return (start + (1 - start) * p,
                CGSize(width: (slot.minX - rest.minX) * (1 - p), height: (slot.minY - rest.minY) * (1 - p)))
    }
}
