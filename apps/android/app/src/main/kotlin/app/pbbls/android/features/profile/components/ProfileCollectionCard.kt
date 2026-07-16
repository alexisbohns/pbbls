package app.pbbls.android.features.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.profile.models.Collection
import app.pbbls.android.theme.PebblesIcon
import app.pbbls.android.theme.PebblesIconToken
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * Tile in the horizontal Collections scroller — ports iOS
 * `ProfileCollectionCard.swift`. `collection == null` renders the dashed
 * "New collection" placeholder variant; otherwise the filled variant with
 * icon box, name, and live pebble count. Pure visuals — the parent owns
 * tap handling.
 */
@Composable
fun ProfileCollectionCard(
    collection: Collection?,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    val radius = PebblesTheme.spacing.lg
    val borderModifier =
        if (collection != null) {
            Modifier.drawBehind {
                drawRoundRect(
                    color = system.muted,
                    cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        } else {
            Modifier.drawBehind {
                drawRoundRect(
                    color = system.muted,
                    cornerRadius = CornerRadius(radius.toPx()),
                    style =
                        Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 10.dp.toPx())),
                        ),
                )
            }
        }

    Column(
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm),
        modifier =
            modifier
                .width(140.dp)
                .then(borderModifier)
                .padding(PebblesTheme.spacing.lg),
    ) {
        Box(
            modifier =
                Modifier
                    .size(PebblesTheme.spacing.xxl)
                    .background(accent.surface, RoundedCornerShape(PebblesTheme.spacing.sm)),
            contentAlignment = Alignment.Center,
        ) {
            PebblesIcon(
                painter = painterResource(if (collection != null) R.drawable.ic_stack else R.drawable.ic_plus),
                token = PebblesIconToken.SMALL,
                contentDescription = null,
                tint = accent.primary,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xs)) {
            PebblesText(
                text = collection?.name ?: stringResource(R.string.profile_collection_new),
                style = PebblesTypography.headline,
                color = system.foreground,
                maxLines = 1,
            )
            if (collection != null) {
                PebblesText(
                    text =
                        pluralStringResource(
                            R.plurals.pebbles_count,
                            collection.pebbleCount,
                            collection.pebbleCount,
                        ),
                    style = PebblesTypography.subhead,
                    color = system.secondary,
                )
            }
        }
    }
}
