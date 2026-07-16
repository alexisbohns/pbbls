package app.pbbls.android.features.profile.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.profile.models.CollectionMode
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * Capsule badge showing a collection's mode with emoji + label — ports iOS
 * `CollectionModeBadge.swift`. Renders nothing when [mode] is null. The
 * Stack/Pack/Track labels are product vocabulary (string resources so the
 * maintainer can localize deliberately — never machine-translated).
 */
@Composable
fun CollectionModeBadge(
    mode: CollectionMode?,
    modifier: Modifier = Modifier,
) {
    if (mode == null) return
    val system = PebblesTheme.colors.system
    val label = stringResource(mode.labelRes)
    val a11y = stringResource(R.string.collection_mode_a11y, label)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            modifier
                .border(1.dp, system.secondary.copy(alpha = 0.3f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .clearAndSetSemantics { contentDescription = a11y },
    ) {
        PebblesText(
            text = mode.emoji,
            style = PebblesTypography.captionEmphasized,
            color = system.foreground,
        )
        PebblesText(
            text = label,
            style = PebblesTypography.captionEmphasized,
            color = system.foreground,
        )
    }
}

/** The iOS emoji table, verbatim (never localized). */
private val CollectionMode.emoji: String
    get() =
        when (this) {
            CollectionMode.STACK -> "🎯"
            CollectionMode.PACK -> "📦"
            CollectionMode.TRACK -> "🔄"
        }

/** Mode display labels — internal so the form's mode picker shares the table. */
internal val CollectionMode.labelRes: Int
    get() =
        when (this) {
            CollectionMode.STACK -> R.string.collection_mode_stack
            CollectionMode.PACK -> R.string.collection_mode_pack
            CollectionMode.TRACK -> R.string.collection_mode_track
        }
