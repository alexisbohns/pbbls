package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.navigation.PebblesKey
import app.pbbls.android.navigation.PebblesNavigationItems
import app.pbbls.android.navigation.pebblesNavigationSuiteColors
import com.android.tools.screenshot.PreviewTest

/**
 * The four-tab navigation (#852) in both of its forms (#855): the short bottom
 * bar on a phone and the collapsed wide rail from 600 dp. The type is passed
 * explicitly rather than read from the window, so each render pins the
 * component it is named for whatever width layoutlib reports.
 */
@Composable
private fun SuitePreview(type: NavigationSuiteType) {
    NavigationSuiteScaffold(
        navigationItems = {
            PebblesNavigationItems(
                current = PebblesKey.Path,
                navigationSuiteType = type,
                onSelect = {},
                onReselect = {},
            )
        },
        navigationSuiteType = type,
        navigationSuiteColors = pebblesNavigationSuiteColors(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Path", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 400)
@Preview(name = "fs2", showBackground = true, heightDp = 400, fontScale = 2f)
@Preview(name = "fr", showBackground = true, heightDp = 400, locale = "fr")
@Composable
fun NavigationBarLight() {
    PebblesTheme { SuitePreview(NavigationSuiteType.ShortNavigationBarCompact) }
}

@PreviewTest
@Preview(showBackground = true, heightDp = 400, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun NavigationBarDark() {
    PebblesTheme { SuitePreview(NavigationSuiteType.ShortNavigationBarCompact) }
}

@PreviewTest
@PreviewWideTall
@Preview(name = "fr", showBackground = true, widthDp = 840, heightDp = 720, locale = "fr")
@Composable
fun NavigationRailLight() {
    PebblesTheme { SuitePreview(NavigationSuiteType.WideNavigationRailCollapsed) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 840, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun NavigationRailDark() {
    PebblesTheme { SuitePreview(NavigationSuiteType.WideNavigationRailCollapsed) }
}
