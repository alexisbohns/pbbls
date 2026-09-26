package app.pbbls.android.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow

/**
 * Screen scaffold — a stock M3 `Scaffold` (#854) on `surface`, with the
 * [topBar] slot (use [PebblesTopBar]) and a content column that seeds
 * `LocalContentColor` with `onSurfaceVariant`, so unstyled content inherits the
 * branded foreground the way iOS's `.foregroundStyle` cascade does.
 *
 * Insets: `contentWindowInsets` is `safeDrawing` (IME included, as the
 * hand-rolled `safeDrawingPadding()` was). Inside the app's tab scaffold those
 * insets are already consumed, and both `Scaffold` and `TopAppBar` subtract
 * consumed insets, so nothing pads twice; outside it, this scaffold pads for
 * itself. The content padding is consumed in turn, so an `imePadding()` below
 * resolves to what is left.
 *
 * Width: the content column is [readableWidth] — centered and capped at 600 dp
 * on tablets and desktop windows (#855), a no-op on phones. The [topBar] sits
 * outside it and stays full-width.
 *
 * [overlay] floats over the content across the full width, outside the cap:
 * for chrome that belongs to the window's edges rather than the column's,
 * such as the glyph store's floating toolbar on the end edge of a tablet.
 */
@Composable
fun PebblesScreen(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        containerColor = colors.surface,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding),
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) {
                Column(modifier = Modifier.fillMaxSize().readableWidth()) {
                    content()
                }
                overlay()
            }
        }
    }
}

/**
 * Shared top bar — a stock `CenterAlignedTopAppBar` (#854): the M3 title
 * style, 64 dp, 48 dp action targets, status-bar insets handled by the bar.
 * [leading] is the navigation slot and [trailing] the actions. The title
 * carries the `heading()` semantics role — the TalkBack analog of iOS keeping
 * `navigationTitle` alive for VoiceOver. Pass [scrollBehavior] (and the
 * matching `nestedScroll` on the screen) for the scrolled container colour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PebblesTopBar(
    title: String,
    modifier: Modifier = Modifier,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        },
        modifier = modifier,
        navigationIcon = { Row(verticalAlignment = Alignment.CenterVertically) { leading() } },
        actions = trailing,
        colors =
            TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        scrollBehavior = scrollBehavior,
    )
}

/**
 * Text button for [PebblesTopBar] slots — the `PebbleToolbarButton` analog
 * (iOS `Theme/PebbleToolbarButton.swift`): label pinned to `onSurfaceVariant`
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
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}
