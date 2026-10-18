package app.pbbls.android.features.path.spine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.features.path.spine.logic.EntryTypes
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val META_GAP = 8.dp
private val PEOPLE_GAP = 6.dp

/** The web's `max-w-[22ch]`: a `ch` is about half the em of a text face. */
private const val NAME_MAX_CH = 22f
private const val CH_PER_EM = 0.5f

/** An untitled entry reads "untitled" in a light italic, whatever its valence. */
private const val UNTITLED_WEIGHT = 340

/**
 * A pebble with no photo, set as type (rule set §No-photo entry): no box, the name
 * at the rank its intensity earns and the cut its valence earns, then the time and
 * the emotion, then who was there. No click of its own: the pebble wraps it.
 *
 * @param emotionName already localised by the caller; empty for a moment that is
 *   not a pebble yet, which shows the time alone.
 * @param emotionInk the pebble's spot ink, or `ink2` with no palette.
 */
@Composable
fun SpineEntry(
    moment: PathMoment,
    emotionName: String,
    emotionInk: Color,
    people: @Composable (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val locale = LocalConfiguration.current.locales[0]
    val valence = moment.valence
    val entry = remember(valence) { EntryTypes.entry(valence.intensity, valence.polarity) }
    val time =
        remember(moment.happenedAt, locale) {
            moment.happenedAt
                .atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
        }
    val name = moment.name
    val nameStyle =
        if (name != null) {
            type.entryName(entry.name.fontSizeSp, entry.name.fontWeight, entry.name.italic, entry.name.letterSpacingEm)
        } else {
            type.entryName(entry.name.fontSizeSp, UNTITLED_WEIGHT, italic = true, trackingEm = entry.name.letterSpacingEm)
        }
    val nameMaxWidth = with(LocalDensity.current) { (entry.name.fontSizeSp * CH_PER_EM * NAME_MAX_CH).sp.toDp() }

    Column(modifier) {
        Text(
            text = name ?: stringResource(R.string.path_spine_untitled),
            style = nameStyle.copy(lineBreak = LineBreak.Heading),
            color = if (name != null) colors.ink else colors.ink3,
            modifier = Modifier.widthIn(max = nameMaxWidth),
        )
        Row(
            modifier = Modifier.padding(top = META_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = time, style = type.entryTime, color = colors.ink)
            // A moment that is not a pebble yet has no emotion: the time alone (R4).
            if (moment.pebble != null && emotionName.isNotEmpty()) {
                Text(text = " · ", style = type.entryTime, color = colors.ink3)
                Text(text = emotionName, style = type.entryEmotion, color = emotionInk)
            }
        }
        if (people != null) {
            Column(Modifier.padding(top = PEOPLE_GAP)) { people() }
        }
    }
}
