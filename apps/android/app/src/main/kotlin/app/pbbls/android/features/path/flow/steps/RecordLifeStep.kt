package app.pbbls.android.features.path.flow.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.data.LocalReferenceDataService
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.model.PebbleAnswers
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.ui.ReferenceStrings
import app.pbbls.android.core.ui.ReferenceType
import app.pbbls.android.core.ui.render.GlyphImage
import app.pbbls.android.core.ui.souls.SoulChooserSheet
import app.pbbls.android.core.ui.souls.UsualSoulsStrip

/** The list dissolves into the title and the bar over this height, and keeps that much padding so rest is unfaded. */
private val FadeHeight = 24.dp
private val TILE_MIN_HEIGHT = 48.dp
private val TILE_GLYPH = 28.dp
private const val ONE_COLUMN_FONT_SCALE = 1.5f
private const val DEFAULT_VIEW_BOX = "0 0 200 200"

/**
 * What part of life, and who was there (record-flow D10, Margin treatment): RECENT when there is any,
 * then every domain as a two-column list of glyph and name, and under it a pinned tinted bar: the last
 * tap's description once there is one, "Who was there?" with the four most-tagged people and More, then
 * "Make it a pebble". Mirrors iOS `CompleteLifeStep`.
 *
 * Stateless apart from the caption's last tap and the More sheet. It scrolls itself and draws its own
 * title, so it sits outside `RecordStepScaffold`: the bar's tint must reach the bottom edge of the screen,
 * which the scaffold's padding and the flow's bottom inset would both leave a strip under. The list
 * dissolves into the bar with an overlay gradient (never a `DstIn` mask: HWUI ignored one on a Pixel 8).
 *
 * Three domains at most: a fourth tap goes to [onToggleDomain] like any other and the model refuses it with a warning.
 */
@Composable
fun RecordLifeStep(
    answers: PebbleAnswers,
    domains: List<Domain>,
    recent: List<Domain>,
    souls: List<SoulWithGlyph>,
    isSoulsLoaded: Boolean,
    error: String?,
    isCompleting: Boolean,
    hapticsOn: Boolean,
    onToggleDomain: (String) -> Unit,
    onToggleSoul: (String) -> Unit,
    onSetSouls: (List<String>) -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    onAchievementCheck: () -> Unit = {},
    /** Which domain the caption starts on: nothing at rest; a preview or a test seeds it. */
    initialCaptionDomainId: String? = null,
) {
    val refs = LocalReferenceDataService.current
    var lastTappedId by rememberSaveable { mutableStateOf(initialCaptionDomainId) }
    var showsChooser by rememberSaveable { mutableStateOf(false) }

    // The cache refreshes only on soul mutations, so the counts would lag every pebble recorded since launch (#1370).
    LaunchedEffect(Unit) { refs.refreshSouls() }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.record_life_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = PebblesTheme.spacing.lg),
        )
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            DomainGrid(
                answers = answers,
                domains = domains,
                recent = recent,
                onTap = { id ->
                    lastTappedId = id
                    onToggleDomain(id)
                },
            )
            EdgeFade(top = true, modifier = Modifier.align(Alignment.TopCenter))
            EdgeFade(top = false, modifier = Modifier.align(Alignment.BottomCenter))
        }
        Bar(
            caption = lastTappedId?.let { id -> domains.firstOrNull { it.id == id } },
            strip = {
                UsualSoulsStrip(
                    souls = souls,
                    selection = answers.soulIds,
                    isLoaded = isSoulsLoaded,
                    onToggle = onToggleSoul,
                    onMore = { showsChooser = true },
                    hapticsOn = hapticsOn,
                )
            },
            error = error,
            canComplete = answers.isComplete,
            isCompleting = isCompleting,
            onComplete = onComplete,
        )
    }

    if (showsChooser) {
        SoulChooserSheet(
            title = stringResource(R.string.souls_who_was_there),
            currentSelection = answers.soulIds,
            allowsSkip = true,
            startsPartial = false,
            onDismiss = { showsChooser = false },
            onConfirm = {
                onSetSouls(it)
                showsChooser = false
            },
            hapticsOn = hapticsOn,
            onAchievementCheck = onAchievementCheck,
        )
    }
}

@Composable
private fun DomainGrid(
    answers: PebbleAnswers,
    domains: List<Domain>,
    recent: List<Domain>,
    onTap: (String) -> Unit,
) {
    // One column at large text: a half-width tile truncates every name.
    val columns = if (LocalDensity.current.fontScale >= ONE_COLUMN_FONT_SCALE) 1 else 2
    val space = PebblesTheme.spacing
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = space.lg, end = space.lg, top = FadeHeight, bottom = FadeHeight),
        horizontalArrangement = Arrangement.spacedBy(space.sm),
        verticalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        // Keys differ across the two sections: a domain may sit in RECENT and in ALL.
        if (recent.isNotEmpty()) {
            item(key = "label-recent", span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.record_life_recent)) }
            items(recent, key = { "recent-${it.id}" }) { DomainTile(it, it.id in answers.domainIds) { onTap(it.id) } }
        }
        item(key = "label-all", span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.record_life_all)) }
        items(domains, key = { "all-${it.id}" }) { DomainTile(it, it.id in answers.domainIds) { onTap(it.id) } }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DomainTile(
    domain: Domain,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val name = ReferenceStrings.referenceName(ReferenceType.DOMAIN, domain.slug, domain.name)
    val description = ReferenceStrings.domainLabel(domain.slug, domain.label)
    // A selected tile is a primaryContainer fill, so its content reads the paired role.
    val foreground = if (isSelected) colors.onPrimaryContainer else colors.onSurface
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(if (isSelected) colors.primaryContainer else colors.surfaceContainerLow)
                .semantics {
                    role = Role.Checkbox
                    toggleableState = ToggleableState(isSelected)
                }
                // The description is the action's label: TalkBack reads the name, the state, then what it is.
                .clickable(onClickLabel = description, onClick = onClick)
                .heightIn(min = TILE_MIN_HEIGHT)
                .padding(horizontal = PebblesTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(TILE_GLYPH)) {
            val strokes = domain.strokes
            // No default glyph: the slot stays empty rather than showing an invented mark.
            if (!strokes.isNullOrEmpty()) {
                GlyphImage(
                    strokes = strokes,
                    viewBox = domain.viewBox ?: DEFAULT_VIEW_BOX,
                    strokeColor = foreground,
                    modifier = Modifier.size(TILE_GLYPH),
                )
            }
        }
        Text(
            text = name,
            style = if (isSelected) MaterialTheme.typography.bodyLargeEmphasized else MaterialTheme.typography.bodyLarge,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The pinned bar. Its own tint and a hairline are what make the list's end read as the list's end rather
 * than a cut. The tint runs to the bottom edge; the content sits above the gesture bar, and a raised
 * keyboard replaces that bar instead of adding to it (as `SoulChooserTray`).
 */
@Composable
private fun Bar(
    caption: Domain?,
    strip: @Composable () -> Unit,
    error: String?,
    canComplete: Boolean,
    isCompleting: Boolean,
    onComplete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val space = PebblesTheme.spacing
    Column(modifier = Modifier.fillMaxWidth().background(colors.surfaceContainerLow)) {
        HorizontalDivider(color = colors.outlineVariant)
        Column(
            modifier =
                Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime))
                    .padding(horizontal = space.lg)
                    .padding(top = space.sm, bottom = space.sm),
            verticalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            // Absent until the first tap rather than a blank line holding its height. At large text it
            // would truncate to a stub and cost the list a line: the description stays on each tile's
            // semantics (its click label) and the bar stays under half the screen.
            if (caption != null && LocalDensity.current.fontScale < ONE_COLUMN_FONT_SCALE) {
                Text(
                    text = ReferenceStrings.domainLabel(caption.slug, caption.label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            strip()
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // No count: "(2)" read as two pebbles.
            PebblesPrimaryButton(
                text = stringResource(R.string.complete_cta),
                onClick = onComplete,
                enabled = canComplete && !isCompleting,
                isLoading = isCompleting,
            )
        }
    }
}

/** `surface` to `surface` at zero alpha: the same hue, so the edge never greys. */
@Composable
private fun EdgeFade(
    top: Boolean,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surface
    val clear = surface.copy(alpha = 0f)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(FadeHeight)
                .background(Brush.verticalGradient(if (top) listOf(surface, clear) else listOf(clear, surface))),
    )
}
