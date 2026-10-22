import SwiftUI

/// What the print reads its geometry from, in one layout pass (anchors, not
/// measured state, so it also holds under `ImageRenderer`): the bare page's
/// place and dashed stone, and the verso's stone.
enum PrintAnchor: Hashable {
    case place, ghost, versoStone
}

struct PrintAnchors: PreferenceKey {
    static let defaultValue: [PrintAnchor: Anchor<CGRect>] = [:]

    static func reduce(value: inout [PrintAnchor: Anchor<CGRect>], nextValue: () -> [PrintAnchor: Anchor<CGRect>]) {
        value.merge(nextValue()) { $1 }
    }
}
