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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesScreen
import app.pbbls.android.core.designsystem.PebblesText
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.PebblesTopBar
import app.pbbls.android.core.designsystem.PebblesTypography
import app.pbbls.android.core.model.LabMarkdown
import app.pbbls.android.core.model.Log
import app.pbbls.android.features.lab.components.LabMarkdownBody
import coil3.compose.AsyncImage
import java.util.Locale

/**
 * Announcement detail — ports iOS `AnnouncementDetailView`: 200dp cover,
 * display title, subtitle summary, then the V1 markdown body (design D5). iOS
 * sets no toolbar title here (inline empty bar) — matched with an empty
 * [PebblesTopBar] title. A real Nav3 entry now (#852): it loads its
 * own row by [logId], so [onBack] pops the entry rather than unwinding a swap.
 */
@Composable
fun AnnouncementDetailScreen(
    logId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnnouncementDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val system = PebblesTheme.colors.system

    LaunchedEffect(logId) { viewModel.start(logId) }

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
        // Exhaustive with no `else`: a new AnnouncementDetailUiState case must
        // be rendered.
        when (val state = uiState) {
            AnnouncementDetailUiState.Loading ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = PebblesTheme.colors.accent.primary)
                }

            is AnnouncementDetailUiState.Error ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PebblesText(
                        text = stringResource(state.messageRes),
                        style = PebblesTypography.body,
                        color = system.secondary,
                    )
                    TextButton(onClick = viewModel::retry) {
                        PebblesText(
                            text = stringResource(R.string.profile_retry),
                            style = PebblesTypography.buttonLabel,
                            color = PebblesTheme.colors.accent.primary,
                        )
                    }
                }

            is AnnouncementDetailUiState.Content ->
                AnnouncementDetailContent(
                    log = state.log,
                    coverUrl = state.coverUrl,
                    modifier = Modifier.fillMaxSize(),
                )
        }
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
