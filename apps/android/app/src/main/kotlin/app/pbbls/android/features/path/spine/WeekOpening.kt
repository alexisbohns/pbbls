package app.pbbls.android.features.path.spine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.path.spine.SpineLayout.OPENING_SUMMARY_GAP
import app.pbbls.android.features.path.spine.SpineLayout.OPENING_TITLE_GAP
import app.pbbls.android.features.path.spine.SpineLayout.OPENING_TOP
import app.pbbls.android.features.path.spine.SpineLayout.VERSO_PAD_X
import app.pbbls.android.features.path.spine.logic.WeekTitle
import app.pbbls.android.features.path.spine.logic.WeekWords
import app.pbbls.android.features.path.spine.logic.WeekWordsBuilder

/**
 * How a week opens (#1122, spec §The opening; the web's `WeekOpening.tsx`): the kicker,
 * the title in words and a one-line summary. The title is the week's heading — there is
 * no other week header — and a live region, so paging to a week announces it.
 *
 * @param peopleCount the persons of the week, as `PersonsOfWeek` counts them.
 */
@Composable
fun WeekOpening(
    words: WeekWords,
    pebbleCount: Int,
    peopleCount: Int,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val locale = LocalConfiguration.current.locales[0]
    val resources = LocalResources.current

    val title =
        when (val t = words.title) {
            is WeekTitle.SameMonth -> stringResource(R.string.path_spine_title_same_month, t.from, t.to, t.month)
            is WeekTitle.CrossMonth -> stringResource(R.string.path_spine_title_cross_month, t.from, t.fromMonth, t.to, t.toMonth)
        }
    val (pebblesWords, peopleWords) = WeekWordsBuilder.summary(pebbleCount, peopleCount, locale)
    val summary =
        openingSummary(
            pebbleCount = pebbleCount,
            peopleCount = peopleCount,
            pebblesWords = pebblesWords,
            peopleWords = peopleWords,
            strings =
                OpeningStrings(
                    noPebbles = stringResource(R.string.path_spine_summary_no_pebbles),
                    pebbles = { n -> resources.getQuantityString(R.plurals.path_spine_summary_pebbles, n) },
                    people = { n -> resources.getQuantityString(R.plurals.path_spine_summary_people, n) },
                ),
            locale = locale,
        )

    Column(modifier.padding(horizontal = VERSO_PAD_X).padding(top = OPENING_TOP)) {
        Text(
            text = stringResource(R.string.path_spine_kicker, words.ordinal, words.year),
            style = type.kicker,
            color = colors.ink2,
        )
        Text(
            text = title,
            style = type.title.copy(lineBreak = LineBreak.Heading),
            color = colors.ink,
            modifier =
                Modifier
                    .padding(top = OPENING_TITLE_GAP)
                    .semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
        )
        Text(
            text = summary,
            style = type.summary,
            color = colors.ink2,
            modifier = Modifier.padding(top = OPENING_SUMMARY_GAP),
        )
    }
}
