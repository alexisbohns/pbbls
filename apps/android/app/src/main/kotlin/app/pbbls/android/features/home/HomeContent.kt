package app.pbbls.android.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.designsystem.PebblesScreen
import app.pbbls.android.features.home.components.HomeGreetingBlock
import java.time.LocalDateTime

/** The page's gutters: 16 dp to the sections, 4 more to the greeting's text (the mock's). */
private val GUTTER = 16.dp
private val TEXT_INSET = 4.dp
private val TOP = 22.dp
private val BOTTOM = 40.dp

/** Between two sections (the mock's 44 px gap). */
private val SECTION_GAP = 44.dp

/**
 * Home's page (#1383), stateless over [HomeUiState]: what the screenshot tests render. [now] pins the
 * greeting, so a preview passes a fixed time; [HomeScreen] passes the live minute. No top bar: the
 * greeting is the title, as on iOS. The column is the readable width ([PebblesScreen]).
 *
 * The date is formatted in the app's locale, read here in composition (D22), never `Locale.getDefault()`.
 */
@Composable
fun HomeContent(
    state: HomeUiState,
    now: LocalDateTime,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val greeting = remember(now, state.name, resources, locale) { HomeGreeting.lines(now, state.name, resources, locale) }
    PebblesScreen(modifier = modifier.testTag(JourneyTags.HOME)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = GUTTER, end = GUTTER, top = TOP, bottom = BOTTOM),
            verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
        ) {
            item(key = "greeting") {
                HomeGreetingBlock(lines = greeting, modifier = Modifier.padding(horizontal = TEXT_INSET))
            }
        }
    }
}
