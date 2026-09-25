package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.ContrastLevel
import app.pbbls.android.core.designsystem.PebblesIcon
import app.pbbls.android.core.designsystem.PebblesIconToken
import app.pbbls.android.core.designsystem.PebblesListSection
import app.pbbls.android.core.designsystem.PebblesMaterialTypography
import app.pbbls.android.core.designsystem.PebblesShapes
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.PebblesTopBar
import app.pbbls.android.core.designsystem.PebblesTopBarTextButton
import app.pbbls.android.core.designsystem.pebblesColorScheme
import com.android.tools.screenshot.PreviewTest

/**
 * Design-system pass 2 previews (#565): the shared top bar (iOS-idiom defaults
 * and the shipped create-bar overrides), the bordered list-section chrome, the
 * profile-card chrome, and the milestone icon batch at every size token —
 * light and dark. These are the maintainer's visual review surface for the
 * idiom kit before Profile screens compose from it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChromeGallery() {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Column(
        modifier =
            Modifier
                .background(colors.surface)
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Defaults: labelSmall title in onSurfaceVariant, text buttons.
        PebblesTopBar(
            title = "Settings",
            leading = { PebblesTopBarTextButton(text = "Cancel", onClick = {}) },
            trailing = { PebblesTopBarTextButton(text = "Save", onClick = {}) },
        )
        // The create bar: accent actions and a busy spinner in the trailing slot.
        PebblesTopBar(
            title = "New pebble",
            leading = { PebblesTopBarTextButton(text = "Cancel", onClick = {}, color = colors.primary) },
            trailing = {
                CircularProgressIndicator(
                    color = colors.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            },
        )
        PebblesListSection(
            header = "Single",
            rows =
                listOf(
                    { Text("Single row", style = type.bodyLarge, color = colors.onSurface) },
                ),
        )
        PebblesListSection(
            header = "Multi-row",
            rows =
                listOf(
                    { Text("Top row", style = type.bodyLarge, color = colors.onSurface) },
                    { Text("Middle row", style = type.bodyLarge, color = colors.onSurface) },
                    { Text("Bottom row", style = type.bodyLarge, color = colors.onSurface) },
                ),
        )
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(PebblesTheme.spacing.lg)) {
                Text("Stats", style = type.titleSmall, color = colors.onSurfaceVariant)
                Text("Profile card chrome", style = type.bodyLarge, color = colors.onSurface)
            }
        }
        IconGallery()
    }
}

@Composable
private fun IconGallery() {
    val icons =
        listOf(
            R.drawable.ic_gear,
            R.drawable.ic_person,
            R.drawable.ic_person_pair,
            R.drawable.ic_sparkle,
            R.drawable.ic_calendar,
            R.drawable.ic_fossil_shell,
            R.drawable.ic_alternating_current,
            R.drawable.ic_chevron_right,
            R.drawable.ic_stack,
            R.drawable.ic_scribble,
            R.drawable.ic_plus,
        )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PebblesIconToken.entries.forEach { token ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                icons.forEach { resId ->
                    PebblesIcon(
                        painter = painterResource(resId),
                        token = token,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun DesignSystemChromeLight() {
    PebblesTheme { ChromeGallery() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun DesignSystemChromeDark() {
    PebblesTheme { ChromeGallery() }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HighContrast(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    // Swaps the scheme to the high-contrast variant. The outer PebblesTheme
    // stays for what M3 has no slot for (LocalSpacing, LocalHandTypography).
    PebblesTheme {
        MaterialExpressiveTheme(
            colorScheme = pebblesColorScheme(dark, ContrastLevel.HIGH),
            typography = PebblesMaterialTypography,
            shapes = PebblesShapes,
            content = content,
        )
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun ChromeGalleryHighContrastLight() {
    HighContrast(dark = false) { ChromeGallery() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun ChromeGalleryHighContrastDark() {
    HighContrast(dark = true) { ChromeGallery() }
}
