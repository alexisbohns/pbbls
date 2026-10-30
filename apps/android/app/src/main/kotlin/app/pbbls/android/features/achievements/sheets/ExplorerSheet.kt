package app.pbbls.android.features.achievements.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.achievements.AchievementCard
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.core.model.achievements.DomainsSection
import app.pbbls.android.core.model.achievements.EmotionsSection
import app.pbbls.android.core.model.achievements.ExplorerSection
import app.pbbls.android.core.model.achievements.SubjectKind
import app.pbbls.android.features.achievements.AchievementLookups
import app.pbbls.android.features.achievements.AchievementSheet
import app.pbbls.android.features.achievements.copy.rememberAchievementsCopy
import app.pbbls.android.features.achievements.figures.DomainSealMark
import app.pbbls.android.features.achievements.figures.ExplorerWheel
import app.pbbls.android.features.achievements.figures.ExplorerWheelGeometry
import app.pbbls.android.features.achievements.figures.FirstFiveMark
import app.pbbls.android.features.achievements.figures.Rosace
import app.pbbls.android.features.achievements.figures.SixFirstMark
import app.pbbls.android.features.achievements.figures.TWENTY_FIVE_SEATS

private val WHEEL = 340.dp
private val MARK = 28.dp
private val CHEVRON = 20.dp

/** The board's 25-dot ring, the 25th row's mark. */
@Composable
private fun TwentyFiveMark() {
    val colors = PebblesTheme.spine.colors
    Canvas(Modifier.size(MARK).clearAndSetSemantics {}) {
        val unit = size.width / 28f
        drawCircle(colors.ink2, 11f * unit, center, style = Stroke(unit))
        for (i in 0 until TWENTY_FIVE_SEATS) {
            drawCircle(colors.ink, 1.1f * unit, center + ExplorerWheelGeometry.polar(11f, ExplorerWheelGeometry.twentyFiveAngle(i)) * unit)
        }
    }
}

@Composable
internal fun Chevron() {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        tint = PebblesTheme.spine.colors.ink2,
        modifier = Modifier.size(CHEVRON),
    )
}

/**
 * The Explorer sheet (the board's `sheetExplorer`): the wheel large, then one section per ring, each
 * row dated by its served `on`. The Domains and Emotions rows [onPush] their own sheet on top. A
 * row opens its moment only when the server sent one to open (a snapshot without a moment id,
 * or a first that made a collection, a soul or a glyph, is read but not opened).
 */
@Composable
fun ExplorerSheet(
    explorer: ExplorerSection,
    emotions: EmotionsSection?,
    domains: DomainsSection?,
    lookups: AchievementLookups,
    onPush: (AchievementSheet) -> Unit,
    onOpenMoment: (String) -> Unit,
    onPull: (CompletionStack) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = rememberAchievementsCopy()
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val openPebble = copy.string(R.string.achievements_open_pebble)
    Column(modifier.fillMaxWidth()) {
        val ready: (@Composable () -> Unit)? =
            if (explorer.completion?.ready == true) {
                {
                    ReadyMark(
                        copy.string(R.string.achievements_ready),
                        copy.readyDescription(AchievementCard.EXPLORER),
                        { onPull(CompletionStack.SIX_FIRSTS) },
                    )
                }
            } else {
                null
            }
        SheetBand(title = copy.cardTitle(AchievementCard.EXPLORER), hint = copy.explorerHint(), trailing = ready)
        Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
            ExplorerWheel(explorer, emotions, domains, lookups, copy.explorerDescription(explorer, emotions, domains), size = WHEEL)
        }

        SheetSection(
            copy.string(R.string.achievements_section_first_five),
            copy.ofWords(
                explorer.firstFive.count {
                    it.done
                },
                explorer.firstFive.size,
            ),
        )
        explorer.firstFive.forEach { item ->
            val title = copy.firstFiveTitle(item.step)
            val date = item.on?.let(copy::shortDate)
            val subject = item.subject
            val name = subject?.let { copy.momentName(it.name) }
            val opens = subject?.takeIf { it.kind == SubjectKind.MOMENT || it.kind == SubjectKind.PEBBLE }?.id
            RegisterRow(
                mark = { FirstFiveMark(item.step, item.done) },
                title = title,
                subtitle = name,
                date = date,
                isNew = item.isNew,
                description = copy.row(title, name, date, isNew = item.isNew),
                onClick = opens?.let { id -> { onOpenMoment(id) } },
                clickLabel = opens?.let { openPebble },
            )
        }

        SheetSection(
            copy.string(R.string.achievements_section_six_firsts),
            copy.ofWords(
                explorer.sixFirsts.count {
                    it.done
                },
                explorer.sixFirsts.size,
            ),
        )
        explorer.sixFirsts.forEach { first ->
            val title = copy.sixFirstName(first.kind)
            val pebble = first.pebble
            val name = pebble?.let { copy.momentName(it.name) }
            val date = first.on?.let(copy::shortDate)
            val opens = pebble?.momentId
            RegisterRow(
                mark = { SixFirstMark(first.kind, first.done) },
                title = title,
                subtitle = name,
                date = date,
                isNew = first.isNew,
                description = copy.row(title, name, date, isNew = first.isNew),
                onClick = opens?.let { id -> { onOpenMoment(id) } },
                clickLabel = opens?.let { openPebble },
            )
        }

        explorer.twentyFive?.let { twentyFive ->
            SheetSection(
                copy.string(R.string.achievements_section_twenty_five),
                if (twentyFive.done) {
                    copy.string(
                        R.string.achievements_twenty_five_closed,
                    )
                } else {
                    copy.countOf(twentyFive.count, TWENTY_FIVE_SEATS)
                },
            )
            val pebble = twentyFive.pebble
            if (!twentyFive.done) {
                Text(
                    copy.string(R.string.achievements_twenty_five_waiting),
                    style = type.still,
                    color = colors.ink2,
                    modifier = Modifier.padding(top = 10.dp),
                )
            } else {
                val title = copy.string(R.string.achievements_twenty_fifth)
                val name = pebble?.let { copy.momentName(it.name) }
                val date = twentyFive.on?.let(copy::shortDate)
                val opens = pebble?.momentId
                RegisterRow(
                    mark = { TwentyFiveMark() },
                    title = title,
                    subtitle = name,
                    date = date,
                    isNew = twentyFive.isNew,
                    description = copy.row(title, name, date, isNew = twentyFive.isNew),
                    onClick = opens?.let { id -> { onOpenMoment(id) } },
                    clickLabel = opens?.let { openPebble },
                )
            }
        }

        if (domains != null || emotions != null) SheetSection(copy.string(R.string.achievements_section_domains_emotions), hint = null)
        domains?.let { d ->
            val title = copy.cardTitle(AchievementCard.DOMAINS)
            val count = copy.countOf(d.count, d.of)
            RegisterRow(
                mark = { DomainSealMark() },
                title = title,
                subtitle = count,
                date = null,
                isNew = false,
                description = copy.row(title, count),
                onClick = { onPush(AchievementSheet.Domains) },
                clickLabel = copy.string(R.string.achievements_open_sheet),
                trailing = { Chevron() },
            )
        }
        emotions?.let { e ->
            val title = copy.cardTitle(AchievementCard.EMOTIONS)
            val count = copy.countOf(e.count, e.of)
            RegisterRow(
                mark = { Rosace(e, lookups, description = "", size = MARK, labels = false, ticks = false) },
                title = title,
                subtitle = count,
                date = null,
                isNew = false,
                description = copy.row(title, count),
                onClick = { onPush(AchievementSheet.Emotions) },
                clickLabel = copy.string(R.string.achievements_open_sheet),
                trailing = { Chevron() },
            )
        }
    }
}
