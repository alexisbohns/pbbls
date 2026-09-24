package app.pbbls.android.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Icon-above-label metadata card on `primaryContainer` — ports iOS
 * `SurfaceTile.swift`. The vector [iconPainter] is tinted here (no icon-library
 * dependency; same `painterResource` convention as `WeekRoll`/`CheckGlyph`).
 * Width comes from the caller's `Modifier.weight(1f)`; content is centered. Set
 * [muted] to render a placeholder tile (e.g. the "No domain" empty state).
 *
 * [backgroundColor] / [iconTint] / [labelColor] override the default chrome
 * colors — the pebble read page tints its tiles to the emotion palette (#605).
 * Each is null by default, reproducing the `primaryContainer` chrome elsewhere.
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
    val colors = MaterialTheme.colorScheme
    val spacing = PebblesTheme.spacing
    val tileBackground = backgroundColor ?: colors.primaryContainer
    // Placeholder tiles read as disabled content (#853 rulebook: onSurface at 38%).
    val disabled = colors.onSurface.copy(alpha = 0.38f)
    val resolvedIconTint = if (muted) disabled else (iconTint ?: colors.onPrimaryContainer)
    val resolvedLabelColor = if (muted) disabled else (labelColor ?: colors.onPrimaryContainer)
    Column(
        modifier.background(tileBackground, MaterialTheme.shapes.large).padding(vertical = spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Icon(
            painter = iconPainter,
            contentDescription = null,
            tint = resolvedIconTint,
            modifier = Modifier.size(30.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = resolvedLabelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
