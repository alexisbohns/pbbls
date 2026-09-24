package app.pbbls.android.features.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme

/**
 * Full-width `primaryContainer` pill signing the user out — ports iOS
 * `ProfileLogoutButton.swift`. Retires PathScreen's temporary sign-out.
 */
@Composable
fun ProfileLogoutButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.primaryContainer, shape)
                .clickable(onClick = onClick)
                .padding(vertical = PebblesTheme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.profile_log_out),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onPrimaryContainer,
        )
    }
}
