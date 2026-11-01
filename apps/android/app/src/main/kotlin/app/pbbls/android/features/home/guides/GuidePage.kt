package app.pbbls.android.features.home.guides

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.data.LocalEmotionPaletteService
import app.pbbls.android.core.designsystem.PebblesScreen
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.PebblesTopBar
import app.pbbls.android.core.model.EmotionFamilies
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.LabMarkdown
import app.pbbls.android.core.model.home.Guide
import app.pbbls.android.core.ui.MarkdownBody

/**
 * One About Pebbles reading (#1383 D13; iOS `GuidePage`), pushed from Home by its [slug]: reads the readings
 * ([GuideViewModel.start]) and draws [GuideContent]. The emotions' picture wears the families' palettes from the
 * session's palette cache.
 */
@Composable
fun GuideScreen(
    slug: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GuideViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(slug) { viewModel.start(slug) }
    val palettes = LocalEmotionPaletteService.current.byEmotionId
    val familyPalettes = remember(palettes) { EmotionFamilies.of(palettes.values).map { it.palette } }
    GuideContent(state = state, onBack = onBack, modifier = modifier, familyPalettes = familyPalettes)
}

/**
 * The guide page, stateless over [GuideUiState] (what the screenshot tests render, chrome included). Found: the
 * title in the large bar, then the summary in `lead`, the 120 dp picture when the reading has one, and the markdown
 * body. Loading: a spinner. Missing: "This reading isn't available" (iOS `home.about.missing`).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GuideContent(
    state: GuideUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    familyPalettes: List<EmotionPalette> = emptyList(),
) {
    val colors = MaterialTheme.colorScheme
    val back: @Composable () -> Unit = {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.profile_back_a11y),
            )
        }
    }
    when (state) {
        GuideUiState.Loading ->
            PebblesScreen(modifier = modifier, topBar = { PebblesTopBar(title = "", leading = { back() }) }) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { LoadingIndicator() }
            }

        GuideUiState.Missing ->
            PebblesScreen(modifier = modifier, topBar = { PebblesTopBar(title = "", leading = { back() }) }) {
                Box(Modifier.fillMaxSize().padding(PebblesTheme.spacing.xl), Alignment.Center) {
                    Text(
                        text = stringResource(R.string.home_about_missing),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

        is GuideUiState.Found -> {
            val locale = LocalConfiguration.current.locales[0]
            val words = remember(state.guide, locale) { state.guide.localized(locale) }
            // The Expressive large flexible bar, as the You page: the title sits large and folds into the bar.
            val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
            PebblesScreen(
                modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    LargeFlexibleTopAppBar(
                        title = { Text(text = words.title, modifier = Modifier.semantics { heading() }) },
                        navigationIcon = back,
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = colors.surface,
                                scrolledContainerColor = colors.surfaceContainer,
                            ),
                        scrollBehavior = scrollBehavior,
                    )
                },
            ) {
                GuideBody(state.guide, words.summary, words.body, familyPalettes)
            }
        }
    }
}

@Composable
private fun GuideBody(
    guide: Guide,
    summary: String,
    body: String,
    familyPalettes: List<EmotionPalette>,
) {
    val spacing = PebblesTheme.spacing
    val blocks = remember(body) { LabMarkdown.parse(body) }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = spacing.lg, end = spacing.lg, top = spacing.sm, bottom = spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(text = summary, style = PebblesTheme.page.lead, color = MaterialTheme.colorScheme.onSurfaceVariant)
        guide.illustration?.let { GuideIllustrationView(kind = it, familyPalettes = familyPalettes) }
        MarkdownBody(blocks = blocks)
    }
}
