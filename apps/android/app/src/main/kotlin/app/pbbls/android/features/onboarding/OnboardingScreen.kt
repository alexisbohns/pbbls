package app.pbbls.android.features.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.readableWidth

/**
 * Paged onboarding flow — the `OnboardingView` analog. One [OnboardingPageView]
 * per step inside a [HorizontalPager] with page dots; a top bar with close (✕)
 * and Skip (both finish), and a "Start your path" button on the last page.
 *
 * Pushed as an ordinary [app.pbbls.android.navigation.PebblesKey.Onboarding]
 * entry (#852); [onFinish] persists the `hasSeenOnboarding` flag at the
 * call site so this view stays previewable and serves both the initial gate
 * and any replay. System back is consumed entirely rather than popping the
 * entry — the flow is dismissible only via skip or close (D5) — via
 * [NavigationBackHandler] rather than the legacy `BackHandler`, so it shows no
 * exit animation: a completed gesture never reaches `NavDisplay`'s own
 * predictive-pop transition (design §6).
 */
@Composable
fun OnboardingScreen(
    steps: List<OnboardingStep>,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val pagerState = rememberPagerState(pageCount = { steps.size })

    // Block system back so the flow is dismissible only via skip/close, and
    // consume it entirely (no exit animation) rather than letting NavDisplay
    // scrub its predictive-pop transition. Skipped under @Preview/screenshot
    // rendering, where no NavigationEventDispatcherOwner is provided.
    if (!LocalInspectionMode.current) {
        val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
        NavigationBackHandler(state = backState) { /* consume — no-op */ }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.surface),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .readableWidth(),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val closeLabel = stringResource(R.string.onboarding_close)
                TextButton(
                    onClick = onFinish,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = closeLabel },
                ) {
                    Text(text = "✕", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                }
                TextButton(onClick = onFinish) {
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            ) { page ->
                OnboardingPageView(step = steps[page])
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                steps.indices.forEach { index ->
                    Box(
                        modifier =
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (index == pagerState.currentPage) colors.primary else colors.outlineVariant),
                    )
                }
            }

            // Direct child of the Column (not wrapped in a Box) so this resolves
            // to ColumnScope.AnimatedVisibility with the innermost receiver.
            AnimatedVisibility(
                visible = pagerState.currentPage == steps.size - 1,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp),
            ) {
                PebblesPrimaryButton(
                    text = stringResource(R.string.onboarding_start),
                    onClick = onFinish,
                )
            }
        }
    }
}
