package app.pbbls.android.features.achievements.sheets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.rule
import app.pbbls.android.core.model.achievements.AchievementCard
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.core.model.achievements.EmotionSeat
import app.pbbls.android.core.model.achievements.EmotionsSection
import app.pbbls.android.core.ui.ReferenceStrings
import app.pbbls.android.core.ui.ReferenceType
import app.pbbls.android.features.achievements.AchievementLookups
import app.pbbls.android.features.achievements.copy.rememberAchievementsCopy
import app.pbbls.android.features.achievements.figures.EmotionSealMark
import app.pbbls.android.features.achievements.figures.Rosace
import app.pbbls.android.features.achievements.figures.familyLabelColor
import app.pbbls.android.features.achievements.figures.sealValence

private val ROSACE = 330.dp

/** An emotion's name, or "An emotion" for one this device does not know yet. */
@Composable
private fun emotionName(
    seat: EmotionSeat,
    lookups: AchievementLookups,
    fallback: String,
): String =
    seat.emotionId?.let { lookups.emotion(it) }?.let { ReferenceStrings.referenceName(ReferenceType.EMOTION, it.slug, it.name) }
        ?: fallback

/**
 * The Emotions sheet (the board's `sheetEmotions`): the rosace large, then the server's families in
 * its order, each with its met emotions by the day they were met and then the rest, set quietly. A
 * family or an emotion this device does not know keeps its place under a fallback name.
 */
@Composable
fun EmotionsSheet(
    emotions: EmotionsSection,
    lookups: AchievementLookups,
    onOpenMoment: (String) -> Unit,
    onPull: (CompletionStack) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = rememberAchievementsCopy()
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val openPebble = copy.string(R.string.achievements_open_pebble)
    val unknownEmotion = copy.string(R.string.achievements_unknown_emotion)
    Column(modifier.fillMaxWidth()) {
        val ready: (@Composable () -> Unit)? =
            if (emotions.completion?.ready == true) {
                {
                    ReadyMark(
                        copy.string(R.string.achievements_ready),
                        copy.readyDescription(AchievementCard.EMOTIONS),
                        { onPull(CompletionStack.EMOTIONS) },
                    )
                }
            } else {
                null
            }
        SheetBand(title = copy.cardTitle(AchievementCard.EMOTIONS), hint = emotions.since?.let(copy::sinceHint), trailing = ready)
        Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
            Rosace(
                emotions,
                lookups,
                copy.emotionsDescription(emotions),
                size = ROSACE,
                centre =
                    copy.number(emotions.count) to copy.rosaceOf(emotions.of),
            )
        }
        emotions.families.forEach { group ->
            val family = group.family
            val palette = family?.let { lookups.family(it)?.palette }
            val name =
                family?.let { ReferenceStrings.referenceName(ReferenceType.EMOTION_CATEGORY, it, lookups.family(it)?.name ?: it) }
                    ?: copy.string(R.string.achievements_unknown_family)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 26.dp)
                        .rule(colors.cardRule, above = false)
                        .padding(bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    name,
                    style = type.cardBand,
                    color = if (family != null && palette != null) familyLabelColor(family, palette, colors.isNight) else colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    copy.countOf(group.met.size, group.met.size + group.notYet.size),
                    style = type.sectionHint,
                    color = colors.ink2,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            group.met.forEach { seat ->
                val title = emotionName(seat, lookups, unknownEmotion)
                val pebble = seat.pebble
                val pebbleName = pebble?.let { copy.momentName(it.name) }
                val date = seat.on?.let(copy::shortDate)
                val valence = seat.sealValence()
                val opens = pebble?.momentId
                RegisterRow(
                    mark = {
                        if (valence != null) EmotionSealMark(valence, seat.emotionId, lookups, width = 9f + valence.intensity * 2.4f)
                    },
                    title = title,
                    subtitle = pebbleName,
                    date = date,
                    isNew = seat.isNew,
                    description = copy.row(title, pebbleName, date, isNew = seat.isNew),
                    onClick = opens?.let { id -> { onOpenMoment(id) } },
                    clickLabel = opens?.let { openPebble },
                )
            }
            val notYet = group.notYet.map { emotionName(it, lookups, unknownEmotion) }
            if (notYet.isNotEmpty()) NotYetNames(notYet)
        }
    }
}
