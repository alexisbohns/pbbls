import CoreGraphics

/// The sizes a glyph draws at (icon spec D6). A 24-unit drawing with a 1.75
/// stroke draws a 1.2pt line at 16, the floor; nothing is drawn smaller except
/// `CollectionMark`'s 14pt band mark, noted there. The three SF Symbols the app
/// keeps as chrome (`lock`, the chevrons) size themselves with an inline
/// rounded font at their call sites (iOS migration spec D1, D6).
///
/// - `inline`: beside text (karma amounts, "taken from your photo"), relative to that text's style.
/// - `row`: a list row's leading icon, a segmented pill, a menu item.
/// - `toolbar`: toolbar and chrome buttons inside their 44×44 hit frame.
/// - `tile`: `SurfaceTile`, the Home shortcuts, card leading icons.
/// - `hero`: `ContentUnavailableView` images, the achievement celebration, the invalid-invite page.
enum PbblsGlyphSize: CGFloat, CaseIterable {
    case inline = 16
    case row = 20
    case toolbar = 22
    case tile = 28
    case hero = 48
}
