import SwiftUI

/// Draws one parsed `PebbleSVGModel.Layer` into a SwiftUI `Shape`.
///
/// Combines the layer's SVG-space transform with the viewBox→rect fit so the
/// resulting path draws at the right size and position inside the Shape's
/// drawing rect. Shared by `PebbleAnimatedRenderView` (animated trim) and
/// `PebbleStaticRenderView` (static, full trim).
struct LayerShape: Shape {
    let layer: PebbleSVGModel.Layer
    let viewBox: CGRect

    func path(in rect: CGRect) -> Path {
        // Composition order (CG row-vector math):
        //   p' = p * layer.transform * scale * translate
        // ⇒ apply layer.transform first, then fit-scale, then center-offset.
        let scale = min(rect.width / viewBox.width, rect.height / viewBox.height)
        let scaledWidth = viewBox.width * scale
        let scaledHeight = viewBox.height * scale
        let dx = (rect.width - scaledWidth) / 2 - viewBox.minX * scale
        let dy = (rect.height - scaledHeight) / 2 - viewBox.minY * scale

        var transform = layer.transform
            .concatenating(CGAffineTransform(scaleX: scale, y: scale))
            .concatenating(CGAffineTransform(translationX: dx, y: dy))
        guard let transformed = layer.combinedPath.copy(using: &transform) else {
            return Path(layer.combinedPath)
        }
        return Path(transformed)
    }
}
