package app.pbbls.android.features.onboarding

import androidx.activity.compose.BackHandler
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
import app.pbbls.android.R
import app.pbbls.android.components.PebblesPrimaryButton
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * Paged onboarding flow — the `OnboardingView` analog. One [OnboardingPageView]
 * per step inside a [HorizontalPager] with page dots; a top bar with close (✕)
 * and Skip (both finish), and a "Start your path" button on the last page.
 *
 * Rendered by `RootScreen` as a full-screen overlay (the `fullScreenCover`
 * analog); [onFinish] persists the `hasSeenOnboarding` flag at the call site so
 * this view stays previewable and serves both the initial gate and any replay.
 * System back is blocked while shown — the flow is dismissible only via skip or
 * close (D5).
 */
@Composable
fun OnboardingScreen(
    steps: List<OnboardingStep>,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    val pagerState = rememberPagerState(pageCount = { steps.size })

    // Block system back so the flow is dismissible only via skip/close. Skipped
    // under @Preview/screenshot rendering, where no back dispatcher is provided.
    if (!LocalInspectionMode.current) {
        BackHandler(enabled = true) { /* consume — no-op */ }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(system.background),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
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
                    Text(text = "✕", style = PebblesTypography.headline, color = system.secondary)
                }
                TextButton(onClick = onFinish) {
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        style = PebblesTypography.callout,
                        color = system.secondary,
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
                                .background(if (index == pagerState.currentPage) accent.primary else system.muted),
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
