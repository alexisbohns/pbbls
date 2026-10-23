package app.pbbls.android.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CURRENT = 8.dp
private val OTHER = 6.dp
private val SPACING = 4.dp

/** A pager's position (#1207; onboarding, the wrap sheets): [count] dots, the [current] one larger. Decorative. */
@Composable
fun PageDots(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
    currentColor: Color = MaterialTheme.colorScheme.primary,
    otherColor: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.Center) {
        repeat(count) { index ->
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = SPACING)
                        .size(if (index == current) CURRENT else OTHER)
                        .clip(CircleShape)
                        .background(if (index == current) currentColor else otherColor),
            )
        }
    }
}
