package app.pbbls.android.features.achievements.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineLayoutTokens
import app.pbbls.android.core.designsystem.rule
import app.pbbls.android.core.model.achievements.AchievementCard
import app.pbbls.android.core.model.achievements.AchievementYear
import app.pbbls.android.core.model.achievements.AchievementsPage
import app.pbbls.android.core.model.achievements.Completion
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.core.model.achievements.CountingSection
import app.pbbls.android.core.model.achievements.DomainsSection
import app.pbbls.android.core.model.achievements.EmotionsSection
import app.pbbls.android.core.model.achievements.ExplorerSection
import app.pbbls.android.features.achievements.AchievementLookups
import app.pbbls.android.features.achievements.AchievementSheet
import app.pbbls.android.features.achievements.copy.AchievementsCopy
import app.pbbls.android.features.achievements.copy.rememberAchievementsCopy
import app.pbbls.android.features.achievements.figures.CardLadder
import app.pbbls.android.features.achievements.figures.CardLadders
import app.pbbls.android.features.achievements.figures.ExplorerWheel
import app.pbbls.android.features.achievements.figures.Rosace
import app.pbbls.android.features.achievements.figures.ShardBand
import app.pbbls.android.features.achievements.figures.Skyline
import app.pbbls.android.features.achievements.figures.SkylineGeometry
import app.pbbls.android.features.achievements.figures.YearDisc
import app.pbbls.android.features.achievements.figures.domainBandItems
import app.pbbls.android.features.achievements.figures.skylineFill
import app.pbbls.android.features.achievements.sheets.ReadyMark

private val CARD_GUTTER = 14.dp
private val MARGIN = 34.dp
private val BODY_PAD = 14.dp
private val DOMAINS_BAR = 120.dp
private val STACK_LABEL = 104.dp
private val STACK_COUNT = 34.dp
private val MINI_SKYLINE = 103.dp
private val RULE_WIDTH = 1.dp
private val BAND_TOP = 14.dp

/**
 * The board's margin title: set on its side, reading bottom to top. Its intrinsics are the turned
 * text's, so the card can ask the margin how tall it must be.
 */
private object Upright : LayoutModifier {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val p = measurable.measure(Constraints())
        return layout(
            p.height,
            p.width,
        ) { p.placeWithLayer(x = (p.height - p.width) / 2, y = (p.width - p.height) / 2) { rotationZ = -90f } }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measurable.maxIntrinsicWidth(Constraints.Infinity)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measurable.maxIntrinsicWidth(Constraints.Infinity)

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int = measurable.minIntrinsicHeight(Constraints.Infinity)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int = measurable.maxIntrinsicHeight(Constraints.Infinity)
}

/** A completion's ready mark, as the band draws it: the label, what it reads aloud, its pull, whether it waits. */
private class Ready(
    val label: String,
    val description: String,
    val enabled: Boolean,
    val onPull: () -> Unit,
)

@Composable
private fun ReadyButton(
    ready: Ready,
    modifier: Modifier = Modifier,
    enabled: Boolean = ready.enabled,
) {
    ReadyMark(ready.label, ready.description, ready.onPull, modifier, enabled = enabled)
}

/**
 * The card's two columns: the body sets the height, and the margin (its rotated title over its
 * ladder) grows the card when it is the taller, as the Domains' long bar is. Only the margin is
 * asked for its intrinsic height: the figures are never measured that way.
 */
@Composable
private fun CardColumns(
    margin: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    val marginWidth = MARGIN + RULE_WIDTH
    val touchMin = SpineLayoutTokens.TOUCH_MIN
    Layout(contents = listOf(margin, body)) { (marginParts, bodyParts), constraints ->
        val side = marginWidth.roundToPx()
        val bodyWidth = (constraints.maxWidth - side).coerceAtLeast(0)
        val bodies = bodyParts.map { it.measure(Constraints(maxWidth = bodyWidth)) }
        val bodyHeight = bodies.maxOfOrNull { it.height } ?: 0
        val marginHeight = marginParts.maxOfOrNull { it.minIntrinsicHeight(side) } ?: 0
        val height = maxOf(bodyHeight, marginHeight, touchMin.roundToPx(), constraints.minHeight)
        val margins = marginParts.map { it.measure(Constraints.fixed(side, height)) }
        layout(constraints.maxWidth, height) {
            margins.forEach { it.place(0, 0) }
            bodies.forEach { it.place(side, 0) }
        }
    }
}

/**
 * The card shell (pick 6, iOS `AchievementCardShell`): the margin with the card's name and its
 * [ladder], the title band, the figure. One button saying [description]. [ready] sits over the
 * band's end as its own button, outside the card's node, so TalkBack reaches both; it takes the
 * hint's place.
 */
@Composable
private fun CardShell(
    marginTitle: String,
    title: String,
    hint: String?,
    description: String,
    onClick: () -> Unit,
    ladder: @Composable () -> Unit,
    ready: Ready?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    Box(Modifier.fillMaxWidth().padding(start = CARD_GUTTER, end = CARD_GUTTER, bottom = CARD_GUTTER)) {
        ElevatedCard(
            onClick = onClick,
            modifier =
                Modifier.fillMaxWidth().clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                    onClick {
                        onClick()
                        true
                    }
                },
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.elevatedCardColors(containerColor = colors.stock),
        ) {
            CardColumns(
                margin = {
                    Row {
                        Column(
                            modifier = Modifier.width(MARGIN).fillMaxHeight().padding(top = 16.dp, bottom = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(marginTitle, style = type.cardMargin, color = colors.ink2, maxLines = 1, modifier = Modifier.then(Upright))
                            Spacer(Modifier.weight(1f).heightIn(min = 14.dp))
                            ladder()
                        }
                        VerticalDivider(thickness = RULE_WIDTH, color = colors.cardRule)
                    }
                },
                body = {
                    Column(Modifier.fillMaxWidth().padding(start = BODY_PAD, end = BODY_PAD, bottom = 16.dp)) {
                        val band = Modifier.fillMaxWidth().rule(colors.cardRule, above = false).padding(top = BAND_TOP, bottom = 9.dp)
                        if (ready != null) {
                            // The mark keeps its place on the title's line: a stand-in, unseen, unheard and
                            // inert, under the real mark the card lays over it.
                            Row(band, verticalAlignment = Alignment.CenterVertically) {
                                Text(title, style = type.cardBand, color = colors.ink, modifier = Modifier.weight(1f).padding(end = 8.dp))
                                ReadyButton(ready, Modifier.alpha(0f).clearAndSetSemantics {}, enabled = false)
                            }
                        } else {
                            // The title and the hint share a line; when they cannot (large text), the hint
                            // goes under the title rather than either being cut (iOS `AchievementBand`).
                            FlowRow(
                                modifier = band,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                itemVerticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(title, style = type.cardBand, color = colors.ink, modifier = Modifier.padding(end = 8.dp))
                                if (hint != null) Text(hint, style = type.cardHint, color = colors.ink2)
                            }
                        }
                        content()
                    }
                },
            )
        }
        // Over its stand-in: the body's start past the margin, the band's top, the body's end.
        ready?.let { ReadyButton(it, Modifier.align(Alignment.TopEnd).padding(top = BAND_TOP, end = BODY_PAD)) }
    }
}

/** The ready mark a completion the server offers puts on its card; it waits while any pull runs. */
private fun readyFor(
    completion: Completion?,
    stack: CompletionStack,
    copy: AchievementsCopy,
    pulling: Boolean,
    onPull: (CompletionStack) -> Unit,
): Ready? =
    if (completion?.ready == true) {
        Ready(copy.string(R.string.achievements_ready), copy.readyDescription(stack), enabled = !pulling) { onPull(stack) }
    } else {
        null
    }

/** The header (S1, D2): since the account began, then the days kept, large. Hidden without a count. */
@Composable
private fun Header(
    page: AchievementsPage,
    copy: AchievementsCopy,
) {
    val days = page.daysKept ?: return
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 22.dp)
            .clearAndSetSemantics {
                contentDescription = copy.headerDescription(days, page.since)
                heading()
            },
    ) {
        page.since?.let { Text(copy.since(it), style = type.sinceLine, color = colors.ink2, modifier = Modifier.padding(bottom = 4.dp)) }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(copy.number(days), style = type.daysKept, color = colors.ink)
            Text(copy.daysUnit(days), style = type.daysUnit, color = colors.ink2, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

@Composable
private fun Centred(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun CardLine(text: String) {
    Text(
        text,
        style = PebblesTheme.spine.type.cardLine,
        color = PebblesTheme.spine.colors.ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    )
}

@Composable
private fun ExplorerCard(
    explorer: ExplorerSection,
    page: AchievementsPage,
    lookups: AchievementLookups,
    copy: AchievementsCopy,
    pulling: Boolean,
    onOpen: (AchievementSheet) -> Unit,
    onPull: (CompletionStack) -> Unit,
) {
    val title = copy.cardTitle(AchievementCard.EXPLORER)
    CardShell(
        marginTitle = title,
        title = title,
        hint = copy.explorerHint(),
        description = copy.explorerDescription(explorer, page.emotions, page.domains),
        onClick = { onOpen(AchievementSheet.Explorer) },
        ladder = { CardLadder(CardLadders.explorer(explorer, page.emotions, page.domains)) },
        ready = readyFor(explorer.completion, CompletionStack.SIX_FIRSTS, copy, pulling, onPull),
    ) {
        Centred { ExplorerWheel(explorer, page.emotions, page.domains, lookups, description = "") }
    }
}

@Composable
private fun EmotionsCard(
    emotions: EmotionsSection,
    lookups: AchievementLookups,
    copy: AchievementsCopy,
    pulling: Boolean,
    onOpen: (AchievementSheet) -> Unit,
    onPull: (CompletionStack) -> Unit,
) {
    val title = copy.cardTitle(AchievementCard.EMOTIONS)
    CardShell(
        marginTitle = title,
        title = title,
        hint = emotions.since?.let(copy::sinceHint),
        description = copy.emotionsDescription(emotions),
        onClick = { onOpen(AchievementSheet.Emotions) },
        ladder = { CardLadder(CardLadders.emotions(emotions)) },
        ready = readyFor(emotions.completion, CompletionStack.EMOTIONS, copy, pulling, onPull),
    ) {
        Centred { Rosace(emotions, lookups, description = "", centre = copy.number(emotions.count) to copy.rosaceOf(emotions.of)) }
    }
}

@Composable
private fun DomainsCard(
    domains: DomainsSection,
    copy: AchievementsCopy,
    pulling: Boolean,
    onOpen: (AchievementSheet) -> Unit,
    onPull: (CompletionStack) -> Unit,
) {
    val title = copy.cardTitle(AchievementCard.DOMAINS)
    CardShell(
        marginTitle = title,
        title = title,
        hint = copy.domainsHint(),
        description = copy.domainsDescription(domains),
        onClick = { onOpen(AchievementSheet.Domains) },
        ladder = { CardLadder(listOf(CardLadders.domains(domains)), long = DOMAINS_BAR) },
        ready = readyFor(domains.completion, CompletionStack.DOMAINS, copy, pulling, onPull),
    ) {
        ShardBand(domainBandItems(domains), description = "", modifier = Modifier.padding(top = 16.dp))
        copy.coverage(domains.coverage)?.let { CardLine(it) }
    }
}

/** A glyph stack's line (the board's `.srow`): its label, its small skyline, its count. */
@Composable
private fun StackLine(
    label: String,
    stack: CountingSection,
    copy: AchievementsCopy,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    // When the label cannot keep its column (large text), it takes its own line above the skyline
    // and the count rather than being cut (iOS `subRow`).
    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = SpineLayoutTokens.ROW_MIN)
                .rule(colors.rule, above = false)
                .padding(vertical = 4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = type.stackLabel, color = colors.ink2, modifier = Modifier.widthIn(min = STACK_LABEL))
        Row(Modifier.weight(1f).widthIn(min = MINI_SKYLINE + STACK_COUNT + 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                Skyline(stack.skylineFill(), description = "", viewport = SkylineGeometry.MINI, width = MINI_SKYLINE)
            }
            Text(
                copy.number(stack.count),
                style = type.stackCount,
                color = colors.ink,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = STACK_COUNT),
            )
        }
    }
}

@Composable
private fun PebblesCard(
    page: AchievementsPage,
    pebbles: CountingSection,
    copy: AchievementsCopy,
    onOpen: (AchievementSheet) -> Unit,
) {
    val title = copy.cardTitle(AchievementCard.PEBBLES)
    CardShell(
        marginTitle = title,
        title = title,
        hint = pebbles.since?.let(copy::sinceHint),
        description = copy.pebblesDescription(pebbles, page.glyphsCarved, page.glyphSales),
        onClick = { onOpen(AchievementSheet.Pebbles) },
        ladder = { CardLadder(CardLadders.counting(pebbles)) },
        ready = null,
    ) {
        Skyline(pebbles.skylineFill(), description = "", modifier = Modifier.padding(top = 22.dp))
        CardLine(copy.pebblesLine(pebbles))
        if (page.glyphsCarved != null || page.glyphSales != null) {
            Column(Modifier.padding(top = 14.dp).rule(PebblesTheme.spine.colors.rule, above = true)) {
                page.glyphsCarved?.let { StackLine(copy.string(R.string.achievements_section_glyphs_carved), it, copy) }
                page.glyphSales?.let { StackLine(copy.string(R.string.achievements_section_glyph_sales), it, copy) }
            }
        }
    }
}

@Composable
private fun YearCard(
    year: AchievementYear,
    lookups: AchievementLookups,
    copy: AchievementsCopy,
    onOpen: (AchievementSheet) -> Unit,
) {
    CardShell(
        marginTitle = copy.cardTitle(AchievementCard.YEAR),
        title = year.year.toString(),
        hint = copy.yearHint(year.current),
        description = copy.yearDescription(year),
        onClick = { onOpen(AchievementSheet.Year(year.year)) },
        ladder = { CardLadder(CardLadders.year(year)) },
        ready = null,
    ) {
        Centred { YearDisc(year, lookups, description = "") }
        year.nextStep?.let(copy::nextStep)?.let { CardLine(it) }
    }
}

/**
 * The achievements page (#1307, D1, iOS `AchievementsPageContent`): the header, then one card per
 * section the payload carries, in the board's order, then one card per year. A missing section
 * hides its card (spec §4); no legends, no "new since" lines: new items are marked on the figures,
 * and counted in each card's label. [onOpen] opens a card's sheet; [onPull] starts a ready set's
 * ceremony, and while [pulling] every ready mark waits.
 */
@Composable
fun AchievementsPageContent(
    page: AchievementsPage,
    lookups: AchievementLookups,
    onOpen: (AchievementSheet) -> Unit,
    onPull: (CompletionStack) -> Unit,
    modifier: Modifier = Modifier,
    pulling: Boolean = false,
) {
    val copy = rememberAchievementsCopy()
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header") { Header(page, copy) }
        page.explorer?.let { e -> item(key = "explorer") { ExplorerCard(e, page, lookups, copy, pulling, onOpen, onPull) } }
        page.emotions?.let { e -> item(key = "emotions") { EmotionsCard(e, lookups, copy, pulling, onOpen, onPull) } }
        page.domains?.let { d -> item(key = "domains") { DomainsCard(d, copy, pulling, onOpen, onPull) } }
        page.pebbles?.let { p -> item(key = "pebbles") { PebblesCard(page, p, copy, onOpen) } }
        items(page.years, key = { "year-${it.year}" }) { year -> YearCard(year, lookups, copy, onOpen) }
    }
}
