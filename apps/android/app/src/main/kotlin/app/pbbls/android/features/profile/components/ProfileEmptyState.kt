package app.pbbls.android.features.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * Centered title + hint for a surface with nothing to show — the iOS
 * `ContentUnavailableView` analog (text-only; the SF-symbol slot doesn't
 * carry enough meaning to justify porting per-screen artwork).
 */
@Composable
internal fun ProfileEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PebblesText(
            text = title,
            style = PebblesTypography.headlineEmphasized,
            color = system.foreground,
            textAlign = TextAlign.Center,
        )
        PebblesText(
            text = message,
            style = PebblesTypography.subhead,
            color = system.secondary,
            textAlign = TextAlign.Center,
        )
    }
}
