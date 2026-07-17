import SwiftUI

/// The read-view "Petroglyph" (issue #599): the framed pebble — outline
/// silhouette backfill with the composed render (outline + glyph) traced on
/// top — shown either as the page heading (no snap) or overlapping the snap's
/// top-right corner (`PebbleSnapFrame`).
///
/// Reuses `PebbleAnimatedRenderView`, which already composites the backdrop and
/// the pebble with the read view's native draw-on animation; only the coloring
/// changes here — from the theme-neutral `pebbleFrameColors` used by the Path
/// rows to #599's per-size, per-scheme `petroglyphColors` table.
///
/// Sized by the caller: the heading treatment gives it a large square slot, the
/// snap overlay a smaller one (the render fits its own outline aspect inside
/// whatever frame it gets). A missing `palette` (cache cold, unknown or absent
/// emotion) falls back to the brand accent, mirroring `PathPebbleRow`. `palette`
/// and the valence data arrive as parameters so previews drive it without a
/// live client.
struct PebbleReadPetroglyph: View {
    let renderSvg: String?
    let renderVersion: String?
    let valence: Valence
    let palette: EmotionPalette?

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        if let renderSvg {
            let colors = resolvedColors
            PebbleAnimatedRenderView(
                svg: renderSvg,
                strokeColor: Color(hex: colors.strokeHex) ?? Color.accent.primary,
                strokeColorHex: colors.strokeHex,
                fillHex: colors.fillHex,
                fillOpacity: colors.fillOpacity,
                size: valence.sizeGroup,
                polarity: valence.polarity,
                renderVersion: renderVersion
            )
        }
    }

    /// #599 colors for the current size + scheme, or the brand accent when the
    /// palette is unavailable (opaque accent for both stroke and fill).
    private var resolvedColors: PetroglyphColors {
        guard let palette else {
            let accentHex = Color.accent.primaryHex
            return PetroglyphColors(strokeHex: accentHex, fillHex: accentHex, fillOpacity: 1)
        }
        return palette.petroglyphColors(forSize: valence.sizeGroup, scheme: colorScheme)
    }
}
