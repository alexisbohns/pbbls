package app.pbbls.android.features.path.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.path.PathRowFormatting
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.features.path.render.PebbleThumbnail
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import app.pbbls.android.theme.ReferenceStrings
import app.pbbls.android.theme.ReferenceType
import java.time.ZoneId

/**
 * Path-specific pebble row — ports iOS `PathPebbleRow.swift`. A tap opens the
 * detail sheet ([onTap]); a long-press opens a destructive-delete menu
 * ([onRequestDelete]) — the `combinedClickable` + `contextMenu` analog
 * (sub-project B). Three states by intensity:
 * - intensity 1–2: 56dp thumbnail, `secondary` glyph stroke over a `surface`
 *   wash.
 * - intensity 3: 96dp thumbnail, `light` stroke over an opaque `primary`
 *   fill.
 *
 * Name/time color follows the scheme at every size (light=`primary`,
 * dark=`light`; the text sits on the row background, not the pebble fill —
 * iOS #510), falling back to `system.foreground` without a palette. A first
 * snap renders as a 64dp white-bordered photo on the trailing edge, rotated
 * by row parity.
 *
 * Deviation from iOS (flagged in the PR): the meta line appends the
 * localized emotion name — "MONDAY · 3:42 PM · JOY" — so the fr-localization
 * acceptance criterion is visible on-device. Resolved via the slug-keyed
 * [ReferenceStrings.referenceName], never the DB `name` directly.
 *
 * [palette] is a parameter so screenshot previews need no live services.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PathPebbleRow(
    pebble: Pebble,
    positionIndex: Int,
    palette: EmotionPalette?,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
    onRequestDelete: () -> Unit = {},
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

    var menuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.combinedClickable(onClick = onTap, onLongClick = { menuExpanded = true }),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(
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
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
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
            if (pebble.firstSnapPath != null) {
                val shape = RoundedCornerShape(8.dp)
                PathSnapThumb(
                    storagePath = pebble.firstSnapPath,
                    modifier =
                        Modifier
                            .size(64.dp)
                            .graphicsLayer {
                                rotationZ = PathPebbleRowMetrics.rotationDegrees(positionIndex)
                            }.shadow(elevation = 6.dp, shape = shape)
                            .border(4.dp, Color.White, shape)
                            .clip(shape),
                )
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    PebblesText(
                        text = stringResource(R.string.pebble_delete),
                        style = PebblesTypography.buttonLabel,
                        color = PebblesDestructive,
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_trash),
                        contentDescription = null,
                        tint = PebblesDestructive,
                    )
                },
                onClick = {
                    menuExpanded = false
                    onRequestDelete()
                },
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
