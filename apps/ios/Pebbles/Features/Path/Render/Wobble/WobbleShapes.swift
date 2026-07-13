import SwiftUI

/// Draws a wobbled `CGPath` positioned exactly like `LayerShape` positions the
/// un-wobbled layer: layer transform → viewBox-fit scale → centering offset.
/// The fit math intentionally mirrors `LayerShape.path(in:)` instead of
/// reusing it, so deleting the wobble module touches nothing else.
struct WobbledPathShape: Shape {
    let path: CGPath
    let layerTransform: CGAffineTransform
    let viewBox: CGRect

    func path(in rect: CGRect) -> Path {
        let scale = min(rect.width / viewBox.width, rect.height / viewBox.height)
        let scaledWidth = viewBox.width * scale
        let scaledHeight = viewBox.height * scale
        let dx = (rect.width - scaledWidth) / 2 - viewBox.minX * scale
        let dy = (rect.height - scaledHeight) / 2 - viewBox.minY * scale

        var transform = layerTransform
            .concatenating(CGAffineTransform(scaleX: scale, y: scale))
            .concatenating(CGAffineTransform(translationX: dx, y: dy))
        guard let transformed = path.copy(using: &transform) else {
            return Path(path)
        }
        return Path(transformed)
    }
}

/// Aspect-fits a wobbled backdrop silhouette into the proposed rect.
struct WobbledBackdropShape: Shape {
    let art: WobbleBackdropArt

    func path(in rect: CGRect) -> Path {
        WobbledPathShape(path: art.path, layerTransform: .identity, viewBox: art.viewBox)
            .path(in: rect)
    }
}

/// Reveal-mask geometry for the appear animation: the filled ink is exposed
/// by a fat trimmed stroke running along the wobbled centerline.
enum WobbleMask {
    /// Mask stroke width in viewBox units. Must cover the ink's half-extent
    /// (half-width 3) plus the divergence between displaced contour points
    /// and their displaced centerline anchor — 2.5× the base stroke width is
    /// the starting point. Tuning constant: adjust on the read sheet.
    static let widthInViewBoxUnits: CGFloat = 15

    /// On-screen mask lineWidth for a layer fit into `frame` (same min-axis
    /// fit rule as `PebbleStroke.lineWidth`).
    static func lineWidth(viewBox: CGRect, frame: CGSize) -> CGFloat {
        guard viewBox.width > 0, viewBox.height > 0 else { return widthInViewBoxUnits }
        let scale = min(frame.width / viewBox.width, frame.height / viewBox.height)
        return widthInViewBoxUnits * scale
    }
}
