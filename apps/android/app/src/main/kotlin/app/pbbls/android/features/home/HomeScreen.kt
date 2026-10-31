package app.pbbls.android.features.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.designsystem.PebblesScreen

/** The Home tab (#1383 D1): the first of five, where a signed-in launch lands. */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    PebblesScreen(modifier = modifier.testTag(JourneyTags.HOME)) {}
}
