package app.pbbls.android.features.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import app.pbbls.android.core.designsystem.PebblesIcon
import app.pbbls.android.core.designsystem.PebblesIconToken
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.Collection

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
    val colors = MaterialTheme.colorScheme
    // The stroke is drawn by hand (Modifier.border has no dash), so the
    // `shapes.large` corner is resolved to pixels here.
    val corner = MaterialTheme.shapes.large.topStart
    val borderModifier =
        if (collection != null) {
            Modifier.drawBehind {
                drawRoundRect(
                    color = colors.outlineVariant,
                    cornerRadius = CornerRadius(corner.toPx(size, this)),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        } else {
            Modifier.drawBehind {
                drawRoundRect(
                    color = colors.outlineVariant,
                    cornerRadius = CornerRadius(corner.toPx(size, this)),
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
                    .background(colors.primaryContainer, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            PebblesIcon(
                painter = painterResource(if (collection != null) R.drawable.ic_stack else R.drawable.ic_plus),
                token = PebblesIconToken.SMALL,
                contentDescription = null,
                tint = colors.onPrimaryContainer,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xs)) {
            Text(
                text = collection?.name ?: stringResource(R.string.profile_collection_new),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 1,
            )
            if (collection != null) {
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.pebbles_count,
                            collection.pebbleCount,
                            collection.pebbleCount,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
