package app.pbbls.android.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Screen scaffold — the `pebblesScreen()` analog (iOS `Theme/PebblesScreen.swift`):
 * fills the window with `system.background`, applies safe-drawing insets, and
 * seeds `LocalContentColor` with `system.secondary` so unstyled content inherits
 * the branded foreground the way iOS's `.foregroundStyle` cascade does.
 * Background is always `system.background` — no override knob; screens should
 * not deviate.
 *
 * Compose has no NavigationStack toolbar, so the bar is an explicit [topBar]
 * slot (use [PebblesTopBar]) stacked above the content column.
 */
@Composable
fun PebblesScreen(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val system = PebblesTheme.colors.system
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(system.background)
                .safeDrawingPadding(),
    ) {
        CompositionLocalProvider(LocalContentColor provides system.secondary) {
            topBar()
            content()
        }
    }
}

/**
 * Shared top bar: leading slot, centered title, trailing slot — the
 * `pebblesToolbarTitle` + toolbar-row analog (iOS `Theme/PebblesToolbarTitle.swift`).
 * Defaults follow the iOS idiom: `meta` token (uppercase via [PebblesText]) in
 * `system.secondary`. The M39 create bar predates this idiom and keeps its
 * shipped look (`headlineEmphasized` title, accent buttons) via the style
 * parameters — harmonizing it with iOS is a separate, deliberate change.
 *
 * Geometry matches the shipped M39 bar exactly: 8dp horizontal / 4dp vertical
 * row padding, title weighted between the slots. The title carries the
 * `heading()` semantics role — the TalkBack analog of iOS keeping
 * `navigationTitle` alive for VoiceOver.
 */
@Composable
fun PebblesTopBar(
    title: String,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = PebblesTypography.meta,
    titleColor: Color = PebblesTheme.colors.system.secondary,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        PebblesText(
            text = title,
            style = titleStyle,
            color = titleColor,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .weight(1f)
                    .semantics { heading() },
        )
        trailing()
    }
}

/**
 * Text button for [PebblesTopBar] slots — the `PebbleToolbarButton` analog
 * (iOS `Theme/PebbleToolbarButton.swift`): label pinned to `system.secondary`
 * by default rather than the ambient accent, so toolbar actions read in the
 * branded secondary color and the rule has one grep target. [color] exists for
 * the shipped create-bar accent look and disabled-muted states; it is applied
 * even while [enabled] is false (the shipped M39 treatment — no Material
 * disabled alpha).
 */
@Composable
fun PebblesTopBarTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = PebblesTheme.colors.system.secondary,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        PebblesText(
            text = text,
            style = PebblesTypography.buttonLabel,
            color = color,
        )
    }
}
