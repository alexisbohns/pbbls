package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.data.DayView
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.path.wrap.month.MonthPage
import com.android.tools.screenshot.PreviewTest

/**
 * The month page (#1207, part 11) on the fixture's October: the garland cover, the weeks, the
 * milestones, the persons with their rings and the domains. Tall, to show every section.
 */
@Composable
private fun OctoberPage() {
    Surface(color = PebblesTheme.spine.colors.ground) {
        Box(Modifier.width(390.dp).height(3000.dp)) {
            MonthPage(
                wrap = SpineFixtureWeek.monthWrap,
                lookups = SpineFixtureWeek.lookups,
                dayView = DayView.CIRCLE,
                onDayView = {},
                onWeek = {},
                onOpen = {},
                focused = true,
                zone = SpineFixtureWeek.zone,
            )
        }
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 3000)
@PreviewFrench
@Composable
fun MonthPageLight() {
    PebblesTheme { OctoberPage() }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 3000, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun MonthPageDark() {
    PebblesTheme { OctoberPage() }
}
