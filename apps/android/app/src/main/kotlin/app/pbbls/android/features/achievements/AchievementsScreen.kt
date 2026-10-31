package app.pbbls.android.features.achievements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.common.ObserveUiEffects
import app.pbbls.android.core.data.LocalEmotionPaletteService
import app.pbbls.android.core.data.LocalReferenceDataService
import app.pbbls.android.core.designsystem.PebblesScreen
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.PebblesTopBar
import app.pbbls.android.core.model.EmotionFamilies
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.features.achievements.page.AchievementsPageContent
import app.pbbls.android.features.achievements.sheets.AchievementSheetHost
import app.pbbls.android.features.achievements.sheets.isShownBy
import kotlinx.coroutines.launch

/** The page's lookups from the app's reference data: two of the three permanent locals, read where they render. */
@Composable
internal fun rememberAchievementLookups(): AchievementLookups {
    val palettes = LocalEmotionPaletteService.current
    val references = LocalReferenceDataService.current
    val emotions = palettes.byEmotionId
    val families = remember(emotions) { EmotionFamilies.of(emotions.values).associateBy { it.slug } }
    val domains = remember(references.domains) { references.domains.associateBy { it.id } }
    return remember(families, emotions, domains) { AchievementLookups(families::get, emotions::get, domains::get) }
}

/**
 * You → Achievements (#1307, iOS `AchievementsView`): the days kept and five cards, served whole by
 * `get_achievements`, and their sheets. Opening it fires `check_achievements()` first (the
 * retroactive grant, which never celebrates). A ready mark pulls its ceremony, which plays the
 * existing unlock moment. It reads one [AchievementsUiState] and one sheet stack, and takes
 * navigation as lambdas.
 */
@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    onOpenMoment: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AchievementsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheets by viewModel.sheets.collectAsStateWithLifecycle()
    val pulling by viewModel.pulling.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    // The entry outlives a trip to a pebble's detail: the return reads the page again and unparks the sheets.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
    ObserveUiEffects(viewModel.effects) { effect ->
        when (effect) {
            is AchievementsEffect.OpenMoment -> onOpenMoment(effect.momentId)
            // A snackbar is UI, not a write: the composition's scope is the right one for it.
            is AchievementsEffect.Message -> scope.launch { snackbar.showSnackbar(resources.getString(effect.messageRes)) }
        }
    }

    AchievementsScreen(
        uiState = uiState,
        sheets = sheets.visible,
        lookups = rememberAchievementLookups(),
        onBack = onBack,
        onRetry = viewModel::retry,
        onOpenSheet = viewModel::openSheet,
        onCloseSheet = viewModel::closeSheet,
        onOpenMoment = viewModel::openMoment,
        onPull = viewModel::pullCeremony,
        pulling = pulling != null,
        snackbar = snackbar,
        modifier = modifier,
    )
}

/** Stateless host: the chrome, the state switch, and the sheets over the content. Previewable. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AchievementsScreen(
    uiState: AchievementsUiState,
    sheets: List<AchievementSheet>,
    lookups: AchievementLookups,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenSheet: (AchievementSheet) -> Unit,
    onCloseSheet: (AchievementSheet) -> Unit,
    onOpenMoment: (String) -> Unit,
    onPull: (CompletionStack) -> Unit,
    modifier: Modifier = Modifier,
    pulling: Boolean = false,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = MaterialTheme.colorScheme
    val sheetShown = uiState is AchievementsUiState.Content && sheets.any { it.isShownBy(uiState.page) }
    PebblesScreen(
        modifier = modifier,
        topBar = {
            PebblesTopBar(
                title = stringResource(R.string.achievements_title),
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
        // With a sheet open, the top sheet hosts the messages: its window covers this one.
        overlay = { if (!sheetShown) SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter)) },
    ) {
        // Exhaustive with no `else`: a new case must be rendered here or the build fails.
        when (uiState) {
            AchievementsUiState.Loading ->
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LoadingIndicator()
                }

            is AchievementsUiState.Error ->
                Column(
                    Modifier.fillMaxSize().padding(horizontal = PebblesTheme.spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.achievements_error_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(uiState.messageRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onRetry) {
                        Text(
                            stringResource(R.string.achievements_error_retry),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary,
                        )
                    }
                }

            is AchievementsUiState.Content -> {
                AchievementsPageContent(uiState.page, lookups, onOpen = onOpenSheet, onPull = onPull, pulling = pulling)
                AchievementSheetHost(
                    sheets = sheets,
                    page = uiState.page,
                    lookups = lookups,
                    onPush = onOpenSheet,
                    onDismiss = onCloseSheet,
                    onOpenMoment = onOpenMoment,
                    onPull = onPull,
                    pulling = pulling,
                    snackbar = snackbar,
                )
            }
        }
    }
}
