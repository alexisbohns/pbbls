package app.pbbls.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.path.wrap.intro.IntroClock
import app.pbbls.android.features.path.wrap.intro.IntroSequence
import app.pbbls.android.features.path.wrap.intro.IntroStage
import app.pbbls.android.features.path.wrap.intro.LocalIntroStage
import app.pbbls.android.features.path.wrap.month.MonthCover
import app.pbbls.android.features.path.wrap.month.coverBeats
import com.android.tools.screenshot.PreviewTest

/** The month's intro, frozen mid-play (#1242; iOS `intro-garland-half`): the cover at 1150 ms, unscaled. */
@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 640)
@Composable
fun IntroGarlandHalf() {
    PebblesTheme {
        val wrap = SpineFixtureWeek.monthWrap
        val clock = IntroClock.frozen(wrap.month, IntroSequence.Cover, coverBeats(wrap, SpineFixtureWeek.lookups), elapsed = 1.15)
        Surface(color = PebblesTheme.spine.colors.ground) {
            Box(Modifier.width(390.dp)) {
                CompositionLocalProvider(LocalIntroStage provides IntroStage(clock, IntroSequence.Cover)) {
                    MonthCover(wrap, SpineFixtureWeek.lookups)
                }
            }
        }
    }
}
