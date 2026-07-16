package app.pbbls.android.features.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * Full-width accent-surface pill signing the user out — ports iOS
 * `ProfileLogoutButton.swift`. Retires PathScreen's temporary sign-out.
 */
@Composable
fun ProfileLogoutButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = PebblesTheme.colors.accent
    val shape = RoundedCornerShape(PebblesTheme.spacing.lg)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(accent.surface, shape)
                .clickable(onClick = onClick)
                .padding(vertical = PebblesTheme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        PebblesText(
            text = stringResource(R.string.profile_log_out),
            style = PebblesTypography.buttonLabel,
            color = accent.primary,
        )
    }
}
