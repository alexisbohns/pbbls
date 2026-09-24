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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesScreen
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.PebblesTopBar
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
    val colors = MaterialTheme.colorScheme

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
                            tint = colors.onSurfaceVariant,
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
                    CircularProgressIndicator(color = colors.primary)
                }

            is AnnouncementDetailUiState.Error ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(state.messageRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                    )
                    TextButton(onClick = viewModel::retry) {
                        Text(
                            text = stringResource(R.string.profile_retry),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary,
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
    val colors = MaterialTheme.colorScheme
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
                        .clip(MaterialTheme.shapes.medium)
                        .background(colors.surfaceContainerHighest),
            ) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = log.title(locale),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onSurface,
        )
        // iOS title3 (20pt) — M3's titleLarge (22 sp, Ysabeau).
        Text(
            text = log.summary(locale),
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurfaceVariant,
        )
        val body = log.body(locale)
        if (!body.isNullOrEmpty()) {
            LabMarkdownBody(blocks = LabMarkdown.parse(body))
        }
    }
}
