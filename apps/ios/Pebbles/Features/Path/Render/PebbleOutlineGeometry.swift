import CoreGraphics

/// Layout constants for composing `PebbleOutlineBackdropView` underneath
/// `PebbleRenderView`. The outline viewBox is intentionally ~1.35× the
/// pebble's viewBox so the silhouette frames the artwork with ~12–13%
/// margin per edge. `SVGView` scales each child to fill its proposed
/// frame, so the pebble must be down-scaled explicitly to land at the
/// correct relative size inside the backdrop.
enum PebbleOutlineGeometry {

    /// Outline frame dimensions from issue #473's SVG assets (the
    /// `width` / `height` attributes on each `apps/ios/Pebbles/Resources/Outlines/<name>.svg`).
    /// Keep in sync with the SVG files manually.
    private static let outlineSize: [ValenceSizeGroup: CGSize] = [
        .small:  CGSize(width: 337, height: 270),
        .medium: CGSize(width: 350, height: 350),
        .large:  CGSize(width: 335, height: 400),
    ]

    /// Pebble composed-SVG canvas dims per size. Mirrors
    /// `packages/supabase/supabase/functions/_shared/engine/layout.ts`.
    private static let pebbleSize: [ValenceSizeGroup: CGSize] = [
        .small:  CGSize(width: 250, height: 200),
        .medium: CGSize(width: 260, height: 260),
        .large:  CGSize(width: 260, height: 310),
    ]

    /// Linear scale factor to apply to `PebbleRenderView` so it fits
    /// inside the larger backdrop viewBox. Computed from the per-size
    /// viewBox width ratio; per-axis match is guaranteed by the matched
    /// aspect ratios (see `PebbleOutlineGeometryTests`).
    static func pebbleScale(for size: ValenceSizeGroup) -> Double {
        guard let outline = outlineSize[size], let pebble = pebbleSize[size] else {
            fatalError("PebbleOutlineGeometry: missing geometry for \(size) — add to pebbleSize and outlineSize dictionaries")
        }
        return Double(pebble.width / outline.width)
    }

    /// Aspect ratio (`width / height`) of the outline viewBox for the
    /// outer `ZStack` to adopt via `.aspectRatio(_:contentMode:)`.
    static func aspectRatio(for size: ValenceSizeGroup) -> Double {
        guard let outline = outlineSize[size] else {
            fatalError("PebbleOutlineGeometry: missing geometry for \(size) — add to outlineSize dictionary")
        }
        return Double(outline.width / outline.height)
    }
}
