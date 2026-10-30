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

/// The sizes a pebbles icon draws at (spec D6). A 24-unit drawing with a 1.75
/// stroke draws a 1.2pt line at 16, the floor; nothing is drawn smaller except
/// `CollectionMark`'s 14pt band mark, noted there.
///
/// - `inline`: beside text (karma amounts, "taken from your photo"), relative to that text's style.
/// - `row`: a list row's leading icon, a segmented pill, a menu item.
/// - `toolbar`: toolbar and chrome buttons inside their 44×44 hit frame.
/// - `tile`: `SurfaceTile`, `DataTile`, the Home shortcuts, card leading icons.
/// - `hero`: `ContentUnavailableView` images, the achievement celebration, the invalid-invite page.
enum PbblsIconSize: CGFloat, CaseIterable {
    case inline = 16
    case row = 20
    case toolbar = 22
    case tile = 28
    case hero = 48
}

extension PbblsIconImage {
    /// `PbblsIconImage(.home, .tile)`: the generated initializer at a named size.
    init(_ icon: PbblsIcon, _ size: PbblsIconSize, relativeTo textStyle: Font.TextStyle = .body,
         style: AnyShapeStyle? = nil, label: LocalizedStringResource? = nil) {
        self.init(icon, size: size.rawValue, relativeTo: textStyle, style: style, label: label)
    }
}
