package app.pbbls.android.features.lab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pbbls.android.R
import app.pbbls.android.features.lab.components.LabMarkdownBody
import app.pbbls.android.features.lab.models.LabMarkdown
import app.pbbls.android.features.lab.models.Log
import app.pbbls.android.theme.PebblesScreen
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTypography
import coil3.compose.AsyncImage
import java.util.Locale

/**
 * Announcement detail — ports iOS `AnnouncementDetailView`: 200dp cover,
 * display title, subtitle summary, then the V1 markdown body (design D5). iOS
 * sets no toolbar title here (inline empty bar) — matched with an empty
 * [PebblesTopBar] title. Rendered as a content swap inside the Lab route
 * (design D9), so [onBack] just unwinds the swap.
 */
@Composable
fun AnnouncementDetailScreen(
    log: Log,
    coverUrl: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    PebblesScreen(
        modifier = modifier,
        topBar = {
            PebblesTopBar(
                title = "",
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.profile_back_a11y),
                            tint = system.secondary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        },
    ) {
        AnnouncementDetailContent(
            log = log,
            coverUrl = coverUrl,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Pure detail body — the screenshot-preview surface (design D10). */
@Composable
fun AnnouncementDetailContent(
    log: Log,
    coverUrl: String?,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val locale = Locale.getDefault()
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(PebblesTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.lg),
    ) {
        if (coverUrl != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
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
            style = PebblesTypography.title,
            color = system.foreground,
        )
        // iOS title3 (20pt) — no matching token, so the display face at 20sp.
        PebblesText(
            text = log.summary(locale),
            style = PebblesTypography.title.copy(fontSize = 20.sp),
            color = system.secondary,
        )
        val body = log.body(locale)
        if (!body.isNullOrEmpty()) {
            LabMarkdownBody(blocks = LabMarkdown.parse(body))
        }
    }
}
