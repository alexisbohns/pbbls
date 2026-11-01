package app.pbbls.android.features.home.guides

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.ChipRow
import app.pbbls.android.core.designsystem.SectionHead
import app.pbbls.android.core.model.home.Guide
import app.pbbls.android.features.home.HomeSection

/** Under the head, to the first row (the mock's `.sh` margin in About). */
private val HEAD_GAP = 8.dp

/**
 * About Pebbles on Home (#1383 D13; iOS `GuidesList`): the head "About Pebbles", always, then one [ChipRow]
 * per published reading once the read is in, its chip icon from the guide's illustration ([chipIcon]), its
 * stone cut from `chip-<index>`. No box, no separators. [onOpen] gets the slug of the reading tapped.
 *
 * The words follow the app's locale, read here in composition, French falling back to English field by field.
 */
@Composable
internal fun GuidesSection(
    guides: HomeSection<List<Guide>>,
    textInset: Dp,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier) {
        SectionHead(
            title = stringResource(R.string.home_about_title),
            modifier = Modifier.padding(horizontal = textInset),
        )
        val shown = (guides as? HomeSection.Ready)?.value.orEmpty()
        if (shown.isNotEmpty()) Spacer(Modifier.height(HEAD_GAP))
        shown.forEachIndexed { index, guide ->
            val words = guide.localized(locale)
            ChipRow(
                icon = guide.illustration.chipIcon(),
                title = words.title,
                line = words.summary,
                seed = "chip-$index",
                onClick = { onOpen(guide.slug) },
                modifier = Modifier.padding(horizontal = textInset),
            )
        }
    }
}
