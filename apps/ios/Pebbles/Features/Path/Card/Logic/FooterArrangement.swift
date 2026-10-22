import CoreGraphics

/// The footer's two arrangements (#1146 §Footer): the turn beside the privacy,
/// or, once it would take more than half the width (large type), under it,
/// trailing-aligned, rather than squeezing the privacy to a column.
enum FooterArrangement {
    static func stacked(turnWidth: CGFloat, available: CGFloat) -> Bool {
        turnWidth > available / 2
    }
}
