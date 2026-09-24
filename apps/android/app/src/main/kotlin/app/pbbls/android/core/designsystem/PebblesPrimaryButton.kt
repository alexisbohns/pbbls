package app.pbbls.android.core.designsystem

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Pill-shaped, full-width primary button. Enabled = `primary` fill +
 * `onPrimary` label; disabled = clear fill + 1dp `outlineVariant` outline +
 * `onSurface` at 38% label. [isLoading] swaps the label for a spinner — callers should also pass
 * `enabled = false` while a request is in flight so the press can't re-fire.
 * Ports `apps/ios/Pebbles/Components/Buttons/PebblesPrimaryButtonStyle.swift`.
 * (Google/Apple sign-in buttons are sub-project C, not B.)
 */
@Composable
fun PebblesPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val shape = CircleShape
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = tween(durationMillis = 100, easing = LinearOutSlowInEasing),
        label = "pebblesPrimaryButtonPressAlpha",
    )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .alpha(pressAlpha)
                .background(if (enabled) colors.primary else Color.Transparent, shape)
                .border(1.dp, if (enabled) Color.Transparent else colors.outlineVariant, shape)
                .clickable(
                    enabled = enabled && !isLoading,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = colors.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) colors.onPrimary else colors.onSurface.copy(alpha = 0.38f),
            )
        }
    }
}
