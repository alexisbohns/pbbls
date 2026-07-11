package app.pbbls.android.features.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.pbbls.android.theme.PebblesTheme
import kotlinx.coroutines.delay

private const val AUTO_ADVANCE_MILLIS = 4_000L

/**
 * Three-slide welcome carousel — the `WelcomeCarousel` analog. A [HorizontalPager]
 * (swipeable) with custom pagination dots beneath, auto-advancing every 4s. The
 * auto-advance honors reduced motion ([reduceMotion]) by not running, mirroring
 * the iOS `!reduceMotion` guard.
 */
@Composable
fun WelcomeCarousel(
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val steps = WelcomeSteps.all
    val accent = PebblesTheme.colors.accent
    val system = PebblesTheme.colors.system
    val pagerState = rememberPagerState(pageCount = { steps.size })

    LaunchedEffect(pagerState, reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        while (true) {
            delay(AUTO_ADVANCE_MILLIS)
            val next = (pagerState.currentPage + 1) % steps.size
            pagerState.animateScrollToPage(next)
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(110.dp),
        ) { page ->
            WelcomeSlideView(step = steps[page])
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            steps.indices.forEach { index ->
                Box(
                    modifier =
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (index == pagerState.currentPage) accent.primary else system.muted),
                )
            }
        }
    }
}
