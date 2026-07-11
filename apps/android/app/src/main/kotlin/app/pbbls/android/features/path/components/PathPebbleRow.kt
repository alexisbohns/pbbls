package app.pbbls.android.features.path.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import app.pbbls.android.features.path.PathRowFormatting
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.features.path.render.PebbleThumbnail
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import app.pbbls.android.theme.ReferenceStrings
import app.pbbls.android.theme.ReferenceType
import java.time.ZoneId

/**
 * Path-specific pebble row — ports iOS `PathPebbleRow.swift` minus tap,
 * delete and the photo thumb (read-only milestone; the photo lands with the
 * snap-URL work). Three states by intensity:
 * - intensity 1–2: 56dp thumbnail, `secondary` glyph stroke over a `surface`
 *   wash.
 * - intensity 3: 96dp thumbnail, `light` stroke over an opaque `primary`
 *   fill.
 *
 * Name/time color follows the scheme at every size (light=`primary`,
 * dark=`light`; the text sits on the row background, not the pebble fill —
 * iOS #510), falling back to `system.foreground` without a palette.
 *
 * Deviation from iOS (flagged in the PR): the meta line appends the
 * localized emotion name — "MONDAY · 3:42 PM · JOY" — so the fr-localization
 * acceptance criterion is visible on-device. Resolved via the slug-keyed
 * [ReferenceStrings.referenceName], never the DB `name` directly.
 *
 * [palette] is a parameter so screenshot previews need no live services.
 */
@Composable
fun PathPebbleRow(
    pebble: Pebble,
    positionIndex: Int,
    palette: EmotionPalette?,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val system = PebblesTheme.colors.system
    val locale = LocalConfiguration.current.locales[0]

    val isLarge = pebble.intensity >= 3
    val hasPhoto = pebble.firstSnapPath != null
    val nameColor =
        when {
            palette == null -> system.foreground
            isDark -> palette.light
            else -> palette.primary
        }

    val weekdayTime = PathRowFormatting.weekdayTime(pebble.happenedAt, ZoneId.systemDefault(), locale)
    val metaLine =
        pebble.emotion?.let { emotion ->
            "$weekdayTime · ${ReferenceStrings.referenceName(ReferenceType.EMOTION, emotion.slug, emotion.name)}"
        } ?: weekdayTime

    Row(
        modifier =
            modifier.height(
                PathPebbleRowMetrics.rowHeightDp(pebble.intensity, hasPhoto, positionIndex).dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PebbleThumbnail(
            pebble = pebble,
            palette = palette,
            modifier = Modifier.size(if (isLarge) 96.dp else 56.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PebblesText(
                text = pebble.name,
                style = PebblesTypography.buttonLabel,
                color = nameColor,
            )
            PebblesText(
                text = metaLine,
                style = PebblesTypography.meta,
                color = nameColor.copy(alpha = 0.5f),
            )
        }
    }
}

/**
 * Pure layout rules ported from iOS `PathPebbleRow` extensions — extracted so
 * they are JVM-testable (values in dp/degrees).
 */
object PathPebbleRowMetrics {
    /**
     * Row height by intensity + photo state + parity: large rows are
     * dominated by the 96dp thumbnail (100); photo-less rows are compact
     * (60); rows with a rotated photo need its bounding box (71 even / 68
     * odd).
     */
    fun rowHeightDp(
        intensity: Int,
        hasPhoto: Boolean,
        positionIndex: Int,
    ): Float =
        when {
            intensity >= 3 -> 100f
            !hasPhoto -> 60f
            positionIndex % 2 == 0 -> 71f
            else -> 68f
        }

    /** Photo rotation by row parity: even lean counter-clockwise, odd clockwise. */
    fun rotationDegrees(positionIndex: Int): Float = if (positionIndex % 2 == 0) -7f else 4f
}
