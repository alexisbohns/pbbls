package app.pbbls.android.features.profile.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.SurfaceTile

/**
 * The Profile shortcut tiles — ports iOS `ProfileShortcutsRow.swift`
 * (Souls · Glyphs · Connections, in that order). Collections are reached from
 * their own section card below, so they get no shortcut tile. Handlers are
 * optional and a tile renders only when its handler is wired (D11 — no dead
 * chrome).
 */
@Composable
fun ProfileShortcutsRow(
    modifier: Modifier = Modifier,
    onOpenSouls: (() -> Unit)? = null,
    onOpenGlyphs: (() -> Unit)? = null,
    onOpenConnections: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm),
    ) {
        if (onOpenSouls != null) {
            ShortcutTile(
                iconRes = R.drawable.ic_person_pair,
                label = stringResource(R.string.souls_title),
                onClick = onOpenSouls,
            )
        }
        if (onOpenGlyphs != null) {
            ShortcutTile(
                iconRes = R.drawable.ic_scribble,
                label = stringResource(R.string.glyphs_title),
                onClick = onOpenGlyphs,
            )
        }
        if (onOpenConnections != null) {
            ShortcutTile(
                iconRes = R.drawable.ic_person_pair,
                label = stringResource(R.string.connections_title),
                onClick = onOpenConnections,
            )
        }
    }
}

/** One tappable [SurfaceTile] — the `ProfileShortcutTile` analog (nav lambda instead of NavigationLink). */
@Composable
private fun RowScope.ShortcutTile(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
) {
    SurfaceTile(
        iconPainter = painterResource(iconRes),
        label = label,
        modifier =
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(PebblesTheme.spacing.lg))
                .clickable(onClick = onClick),
    )
}
