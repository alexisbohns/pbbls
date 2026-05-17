import SwiftUI

/// Icon-sizing tokens for SF Symbols. Lives in a sibling enum to `PebblesFont`
/// so call sites read intent-first (`.pebblesIcon(.md)` rather than calling an
/// icon a "font"). Under the hood this still applies a `Font` — SF Symbols are
/// font glyphs and `.font(.system(size:weight:design:))` is the native API for
/// pixel-precise sizing.
enum PebblesIcon {
    case sm     // 13pt semibold
    case md     // 15pt medium
    case large  // 17pt semibold
}

extension View {
    /// Apply a Pebbles icon-size token to an `Image(systemName:)`.
    func pebblesIcon(_ token: PebblesIcon) -> some View {
        font(.system(size: token.size, weight: token.weight, design: .rounded))
    }
}

private extension PebblesIcon {
    var size: CGFloat {
        switch self {
        case .sm:    return 13
        case .md:    return 15
        case .large: return 17
        }
    }

    var weight: Font.Weight {
        switch self {
        case .sm, .large: return .semibold
        case .md:         return .medium
        }
    }
}
