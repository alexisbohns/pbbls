package app.pbbls.android.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Icon-above-label metadata card on `accent.surface` — ports iOS
 * `SurfaceTile.swift`. The vector [iconPainter] is tinted here (no icon-library
 * dependency; same `painterResource` convention as `WeekRoll`/`CheckGlyph`).
 * Width comes from the caller's `Modifier.weight(1f)`; content is centered. Set
 * [muted] to render a placeholder tile (e.g. the "No domain" empty state).
 *
 * [backgroundColor] / [iconTint] / [labelColor] override the default chrome
 * colors — the pebble read page tints its tiles to the emotion palette (#605).
 * Each is null by default, reproducing the accent-surface chrome elsewhere.
 * [muted] still wins for the icon/label so an empty placeholder reads muted even
 * on a tinted background.
 */
@Composable
fun SurfaceTile(
    iconPainter: Painter,
    label: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    backgroundColor: Color? = null,
    iconTint: Color? = null,
    labelColor: Color? = null,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    val spacing = PebblesTheme.spacing
    val tileBackground = backgroundColor ?: accent.surface
    val resolvedIconTint = if (muted) system.muted else (iconTint ?: accent.primary)
    val resolvedLabelColor = if (muted) system.muted else (labelColor ?: system.secondary)
    Column(
        modifier.background(tileBackground, RoundedCornerShape(spacing.lg)).padding(vertical = spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Icon(
            painter = iconPainter,
            contentDescription = null,
            tint = resolvedIconTint,
            modifier = Modifier.size(30.dp),
        )
        PebblesText(
            label,
            style = PebblesTypography.callout,
            color = resolvedLabelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
