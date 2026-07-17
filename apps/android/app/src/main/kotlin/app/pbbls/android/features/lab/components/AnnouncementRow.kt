package app.pbbls.android.features.lab.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.lab.models.Log
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import coil3.compose.AsyncImage
import java.util.Locale

/**
 * One announcement — ports iOS `AnnouncementRow`: optional 140dp cover
 * (cover-crop, 10dp corners, muted-30% placeholder behind the load), headline
 * title, 3-line summary, and a muted chevron standing in for iOS's automatic
 * nav-link disclosure. [coverUrl] comes from `LogsService.coverImageUrl`
 * (public `lab-assets` bucket — design D7); the cover is decorative
 * (`contentDescription = null`).
 */
@Composable
fun AnnouncementRow(
    log: Log,
    coverUrl: String?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val locale = Locale.getDefault()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onTap)
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (coverUrl != null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(system.muted.copy(alpha = 0.3f)),
                ) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            PebblesText(
                text = log.title(locale),
                style = PebblesTypography.headline,
                color = system.foreground,
            )
            PebblesText(
                text = log.summary(locale),
                style = PebblesTypography.subhead,
                color = system.secondary,
                maxLines = 3,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = system.muted,
            modifier = Modifier.size(16.dp),
        )
    }
}
