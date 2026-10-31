package app.pbbls.android.features.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.home.HomeGreeting

/** The font scale up to which the greeting holds one line, shrinking to fit (D18). */
private const val ONE_LINE_MAX_FONT_SCALE = 1.3f

/** How far the greeting may shrink to hold its line: 28 sp of its 40 (D18). */
private const val SMALLEST_FRACTION = 0.7f

/** Between the dateline, the greeting and its line (the mock's 8 px margins). */
private val GAP = 8.dp

/**
 * Home's first lines (#1383; iOS `HomeGreetingView`): the dateline in `primary`, the greeting, its
 * italic line in `onSurfaceVariant`. One TalkBack node reading the greeting, the line, then the date.
 *
 * Up to 1.3 × font scale the greeting holds one line, shrinking to 28 sp to fit a long name; above
 * it, it wraps to two lines at full size, because shrinking a 200 % greeting back would undo the
 * user's setting (D18). [onGreetingLayout] is the greeting's own layout, for the fit test.
 */
@Composable
internal fun HomeGreetingBlock(
    lines: HomeGreeting.Lines,
    modifier: Modifier = Modifier,
    onGreetingLayout: (TextLayoutResult) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val type = PebblesTheme.page
    val oneLine = LocalDensity.current.fontScale <= ONE_LINE_MAX_FONT_SCALE
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = lines.spoken },
    ) {
        Text(text = lines.dateline, style = type.dateline, color = colors.primary)
        Spacer(Modifier.height(GAP))
        Text(
            text = lines.title,
            style = type.greeting,
            color = colors.onSurface,
            maxLines = if (oneLine) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            autoSize =
                if (oneLine) {
                    TextAutoSize.StepBased(
                        minFontSize = type.greeting.fontSize * SMALLEST_FRACTION,
                        maxFontSize = type.greeting.fontSize,
                    )
                } else {
                    null
                },
            onTextLayout = onGreetingLayout,
        )
        Spacer(Modifier.height(GAP))
        Text(text = lines.line, style = type.lead, color = colors.onSurfaceVariant)
    }
}
