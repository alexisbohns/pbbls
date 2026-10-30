package app.pbbls.android.features.achievements.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineLayoutTokens
import app.pbbls.android.core.designsystem.rule
import app.pbbls.android.core.model.achievements.AchievementCard
import app.pbbls.android.core.model.achievements.CountingSection
import app.pbbls.android.features.achievements.copy.AchievementsCopy
import app.pbbls.android.features.achievements.copy.rememberAchievementsCopy
import app.pbbls.android.features.achievements.figures.Skyline
import app.pbbls.android.features.achievements.figures.SkylineStage
import app.pbbls.android.features.achievements.figures.skylineFill

private const val SMALL_STAGE = 0.8f
private val SKYLINE = 350.dp
private val THEN_EVERY_INSET = 44.dp

/** A threshold's stage: its place on the served ladder, the last stage for one past it. */
private fun CountingSection.stageOf(threshold: Int): Int = ladder.indexOf(threshold).takeIf { it >= 0 } ?: ladder.lastIndex

/**
 * One stack's milestones: the levels the server lists (solid, dated, opening their pebble), then
 * its next (filled by the server's progress, "n to go", never a date), then the ladder's steps that
 * are neither, then "then every N" when the ladder continues. [small] is the glyph stacks' grammar.
 */
@Composable
private fun Milestones(
    stack: CountingSection,
    copy: AchievementsCopy,
    small: Boolean,
    onOpenMoment: (String) -> Unit,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val numeral = if (small) type.milestoneSmall else type.milestone
    val scale = if (small) SMALL_STAGE else 1f
    val openPebble = copy.string(R.string.achievements_open_pebble)
    stack.levels.forEach { level ->
        val title = copy.number(level.threshold)
        val pebble = level.pebble
        val name = pebble?.let { copy.momentName(it.name) }
        val date = level.on?.let(copy::shortDate)
        val opens = pebble?.momentId
        RegisterRow(
            mark = { SkylineStage(stack.stageOf(level.threshold), fill = 1f, done = true, scale = scale) },
            title = title,
            subtitle = name,
            date = date,
            isNew = level.isNew,
            description = copy.row(title, name, date, isNew = level.isNew),
            onClick = opens?.let { id -> { onOpenMoment(id) } },
            clickLabel = opens?.let { openPebble },
            titleStyle = numeral,
        )
    }
    val next = stack.next
    if (next != null) {
        val title = copy.number(next.threshold)
        val left = copy.toGo(next.left)
        RegisterRow(
            mark = { SkylineStage(stack.stageOf(next.threshold), fill = next.progress, done = false, scale = scale) },
            title = title,
            subtitle = left,
            date = null,
            isNew = false,
            description = copy.row(title, left),
            onClick = null,
            clickLabel = null,
            titleStyle = numeral.copy(color = colors.ink2),
        )
    }
    val reached = stack.levels.map { it.threshold }.toSet()
    stack.ladder.forEachIndexed { i, step ->
        if (step in reached || step == next?.threshold) return@forEachIndexed
        val title = copy.number(step)
        RegisterRow(
            mark = { SkylineStage(i, fill = 0f, done = false, scale = scale) },
            title = title,
            subtitle = null,
            date = null,
            isNew = false,
            description = title,
            onClick = null,
            clickLabel = null,
            titleStyle = numeral.copy(color = colors.ink2),
        )
    }
    stack.thenEvery?.let { every ->
        Text(
            copy.thenEvery(every),
            style = type.still,
            color = colors.ink2,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = SpineLayoutTokens.ROW_MIN)
                    .rule(colors.rule, above = false)
                    .padding(start = THEN_EVERY_INSET, top = 12.dp),
        )
    }
}

/** The Pebbles sheet (the board's `sheetPebbles`): the skyline, the milestones, then glyphs and sales smaller. */
@Composable
fun PebblesSheet(
    pebbles: CountingSection,
    glyphsCarved: CountingSection?,
    glyphSales: CountingSection?,
    onOpenMoment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = rememberAchievementsCopy()
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val fill = pebbles.skylineFill()
    Column(modifier.fillMaxWidth()) {
        SheetBand(copy.cardTitle(AchievementCard.PEBBLES), hint = pebbles.since?.let(copy::sinceHint))
        Skyline(fill, copy.pebblesLine(pebbles), modifier = Modifier.padding(top = 24.dp), width = SKYLINE)
        Text(
            copy.pebblesKept(pebbles.count),
            style = type.lead,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
        SheetSection(copy.string(R.string.achievements_section_milestones), copy.ofWords(fill.full, fill.stages))
        Milestones(pebbles, copy, small = false, onOpenMoment = onOpenMoment)
        glyphsCarved?.let {
            SheetSection(copy.string(R.string.achievements_section_glyphs_carved), copy.number(it.count))
            Milestones(it, copy, small = true, onOpenMoment = onOpenMoment)
        }
        glyphSales?.let {
            SheetSection(copy.string(R.string.achievements_section_glyph_sales), copy.number(it.count))
            Milestones(it, copy, small = true, onOpenMoment = onOpenMoment)
            Text(
                copy.string(R.string.achievements_buyer_never_named),
                style = type.still,
                color = colors.ink2,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
