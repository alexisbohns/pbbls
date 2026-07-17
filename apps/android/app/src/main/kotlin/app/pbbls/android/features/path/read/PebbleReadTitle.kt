package app.pbbls.android.features.path.read

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pbbls.android.features.path.PebbleReadDateFormat
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Detail title block — ports iOS `PebbleReadTitle.swift`: centered Ysabeau
 * SemiBold 24 name over a localized date/time meta line. [buttonLabel] is the
 * Ysabeau token; only the 24sp size differs from its 17sp default. The `meta`
 * token uppercases the date at draw time (iOS `.textCase(.uppercase)`).
 *
 * [nameColor] / [dateColor] override the default chrome colors — the read page
 * tints them to the emotion palette (#605). Null keeps the system chrome.
 */
@Composable
fun PebbleReadTitle(
    name: String,
    happenedAt: OffsetDateTime,
    modifier: Modifier = Modifier,
    nameColor: Color? = null,
    dateColor: Color? = null,
) {
    val system = PebblesTheme.colors.system
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PebblesText(
            name,
            style = PebblesTypography.buttonLabel.copy(fontSize = 24.sp),
            color = nameColor ?: system.foreground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        PebblesText(
            PebbleReadDateFormat.format(happenedAt, ZoneId.systemDefault(), locale),
            style = PebblesTypography.meta,
            color = dateColor ?: system.secondary,
        )
    }
}
