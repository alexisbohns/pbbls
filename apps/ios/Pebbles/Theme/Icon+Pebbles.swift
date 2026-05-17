import SwiftUI

/// Icon-sizing tokens for SF Symbols. Lives in a sibling enum to `PebblesFont`
/// so call sites read intent-first (`.pebblesIcon(.medium)` rather than calling
/// an icon a "font"). Under the hood this still applies a `Font` — SF Symbols
/// are font glyphs and `.font(.system(size:weight:design:))` is the native API
/// for pixel-precise sizing.
enum PebblesIcon {
    case small   // 13pt semibold
    case medium  // 15pt medium
    case large   // 17pt semibold
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
        case .small:  return 13
        case .medium: return 15
        case .large:  return 17
        }
    }

    var weight: Font.Weight {
        switch self {
        case .small, .large: return .semibold
        case .medium:        return .medium
        }
    }
}
