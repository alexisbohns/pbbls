package app.pbbls.android.features.path.read

import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.ValenceSizeGroup

/**
 * Render-ready colors for the read-view Petroglyph (issue #599) — the framed
 * pebble (backfill + outline + glyph) shown as the page heading, or overlapping
 * the snap's top-right. Resolved per size group AND theme, per the issue table:
 *
 * | layer                 | light             | dark                |
 * | --------------------- | ----------------- | ------------------- |
 * | small/medium strokes  | palette.primary   | palette.secondary   |
 * | small/medium backfill | palette.light     | palette.dark        |
 * | large strokes         | palette.light     | palette.light       |
 * | large backfill        | palette.primary   | palette.primary     |
 *
 * "palette.dark" in the spec is the DB `surface_color` — the fourth palette
 * slot (seeded as primary at ~10% alpha), the only dark-mode backfill
 * candidate in the four-color palette. Its low alpha rides in [fillOpacity]
 * because a 6-digit hex can't carry it.
 *
 * This is deliberately distinct from the shared, theme-neutral
 * [EmotionPalette.pebbleFrameColors] used by the Path rows: #599 scopes the new
 * theme-dependent coloring to the read view. Large is identical to
 * `pebbleFrameColors` (light stroke + opaque primary fill); only small/medium
 * diverges, and only in light mode (primary stroke + solid `light` backfill).
 */
data class PetroglyphColors(
    /** 6-digit `#RRGGBB` for the outline + glyph strokes. */
    val strokeHex: String,
    /** 6-digit `#RRGGBB` for the backfill silhouette. */
    val fillHex: String,
    /** The backfill fill color's alpha (0..1), applied as backdrop view alpha. */
    val fillOpacity: Float,
)

/**
 * Resolves [PetroglyphColors] from a [palette] for the given [sizeGroup] and
 * theme ([isDark]). Pure — no Compose or Android dependency, so it unit-tests
 * directly.
 */
fun petroglyphColors(
    palette: EmotionPalette,
    sizeGroup: ValenceSizeGroup,
    isDark: Boolean,
): PetroglyphColors =
    when (sizeGroup) {
        // Large: pale `light` stroke over an opaque `primary` silhouette, both
        // themes — the same hero treatment `pebbleFrameColors(3)` already gives.
        ValenceSizeGroup.LARGE ->
            PetroglyphColors(
                strokeHex = EmotionPalette.rgbHex(palette.lightHex),
                fillHex = EmotionPalette.rgbHex(palette.primaryHex),
                fillOpacity = EmotionPalette.alphaComponent(palette.primaryHex),
            )
        // Small / medium: theme-dependent. Dark keeps the current secondary
        // stroke + faint `surface` wash; light switches to a primary stroke over
        // a solid `light` backfill.
        else ->
            if (isDark) {
                PetroglyphColors(
                    strokeHex = EmotionPalette.rgbHex(palette.secondaryHex),
                    fillHex = EmotionPalette.rgbHex(palette.surfaceHex),
                    fillOpacity = EmotionPalette.alphaComponent(palette.surfaceHex),
                )
            } else {
                PetroglyphColors(
                    strokeHex = EmotionPalette.rgbHex(palette.primaryHex),
                    fillHex = EmotionPalette.rgbHex(palette.lightHex),
                    fillOpacity = EmotionPalette.alphaComponent(palette.lightHex),
                )
            }
    }
