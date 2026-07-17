package app.pbbls.android.features.path.read

import androidx.compose.ui.graphics.Color
import app.pbbls.android.features.path.models.EmotionPalette

/**
 * Emotion-palette colors for the whole pebble read page (issue #605), resolved
 * per element AND theme. Before #605 only the pebble render carried the palette
 * and the rest of the page used the system/accent chrome; now the page tints to
 * the pebble's emotion palette per this table:
 *
 * | element         | light     | dark    |
 * | --------------- | --------- | ------- |
 * | background      | light     | dark    |
 * | name (title)    | shaded    | light   |
 * | date            | secondary | primary |
 * | tile.background | surface   | surface |
 * | tile.icon       | secondary | primary |
 * | tile.label      | shaded    | light   |
 * | description     | shaded    | light   |
 * | soul.glyph      | secondary | primary |
 * | soul.name       | primary   | light   |
 *
 * Pure — no Compose runtime dependency (only the `Color` value class), so it
 * unit-tests directly. Resolved once at the page root and threaded into the leaf
 * read composables as parameters, keeping them previewable. Callers fall back to
 * the system/accent chrome when the palette is unavailable (cache miss).
 */
data class PebblePageColors(
    val background: Color,
    val title: Color,
    val date: Color,
    val tileBackground: Color,
    val tileIcon: Color,
    val tileLabel: Color,
    val description: Color,
    val soulGlyph: Color,
    val soulName: Color,
)

/** Resolves [PebblePageColors] from a [palette] for the active theme ([isDark]). */
fun pebblePageColors(
    palette: EmotionPalette,
    isDark: Boolean,
): PebblePageColors =
    if (isDark) {
        PebblePageColors(
            background = palette.dark,
            title = palette.light,
            date = palette.primary,
            tileBackground = palette.surface,
            tileIcon = palette.primary,
            tileLabel = palette.light,
            description = palette.light,
            soulGlyph = palette.primary,
            soulName = palette.light,
        )
    } else {
        PebblePageColors(
            background = palette.light,
            title = palette.shaded,
            date = palette.secondary,
            tileBackground = palette.surface,
            tileIcon = palette.secondary,
            tileLabel = palette.shaded,
            description = palette.shaded,
            soulGlyph = palette.secondary,
            soulName = palette.primary,
        )
    }
