package app.pbbls.android.features.path.read

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.features.path.PebbleReadDateFormat
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Detail title block — ports iOS `PebbleReadTitle.swift`: a centered name in
 * `headlineSmall` (Ysabeau, 24 sp) over a localized date/time line in
 * `labelSmall`, sentence case (#853 dropped the iOS uppercase transform).
 *
 * [nameColor] / [dateColor] override the default chrome colors — the read page
 * tints them to the emotion palette (#605). Null keeps `onSurface` /
 * `onSurfaceVariant`.
 */
@Composable
fun PebbleReadTitle(
    name: String,
    happenedAt: OffsetDateTime,
    modifier: Modifier = Modifier,
    nameColor: Color? = null,
    dateColor: Color? = null,
) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            name,
            style = MaterialTheme.typography.headlineSmall,
            color = nameColor ?: colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            PebbleReadDateFormat.format(happenedAt, ZoneId.systemDefault(), locale),
            style = MaterialTheme.typography.labelSmall,
            color = dateColor ?: colors.onSurfaceVariant,
        )
    }
}
