package app.pbbls.android.core.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Height of every full-width funnel action: the M3 Expressive medium button (56 dp). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val PebblesActionHeight = ButtonDefaults.MediumContainerHeight

/**
 * Full-width primary action — a stock M3 `Button` at the medium size, with the
 * Expressive press morph (`ButtonDefaults.shapesFor`). Kept as a wrapper only
 * because seven call sites share the same two defaults: full width, and
 * [isLoading] swapping the label for a spinner. Callers should also pass
 * `enabled = false` while a request is in flight so the press can't re-fire.
 * Disabled uses M3's own treatment (#854); the iOS style this ported
 * (`PebblesPrimaryButtonStyle.swift`) is no longer mirrored.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PebblesPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapesFor(PebblesActionHeight),
        modifier = modifier.fillMaxWidth().heightIn(min = PebblesActionHeight),
        enabled = enabled && !isLoading,
        contentPadding = ButtonDefaults.contentPaddingFor(PebblesActionHeight),
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = LocalContentColor.current, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text(text = text, style = ButtonDefaults.textStyleFor(PebblesActionHeight))
        }
    }
}
