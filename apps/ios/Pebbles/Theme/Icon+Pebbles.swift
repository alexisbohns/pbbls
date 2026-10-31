import SwiftUI

/// The named sizes a pebbles icon draws at, and the `PbblsIconImage`
/// initializer that takes one. The three SF Symbols the app keeps as chrome
/// (`lock`, the chevrons) size themselves with an inline rounded font at their
/// call sites (iOS migration spec D1, D6).

/// The sizes a pebbles icon draws at (spec D6). A 24-unit drawing with a 1.75
/// stroke draws a 1.2pt line at 16, the floor; nothing is drawn smaller except
/// `CollectionMark`'s 14pt band mark, noted there.
///
/// - `inline`: beside text (karma amounts, "taken from your photo"), relative to that text's style.
/// - `row`: a list row's leading icon, a segmented pill, a menu item.
/// - `toolbar`: toolbar and chrome buttons inside their 44×44 hit frame.
/// - `tile`: `SurfaceTile`, the Home shortcuts, card leading icons.
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
